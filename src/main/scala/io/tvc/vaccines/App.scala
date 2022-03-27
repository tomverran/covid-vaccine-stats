package io.tvc.vaccines

import cats.Monad
import cats.data.{Kleisli, NonEmptyList, OptionT, ReaderT}
import cats.effect.kernel.Async
import cats.effect.{Clock, Resource, Sync}
import cats.syntax.applicativeError._
import cats.syntax.foldable._
import cats.syntax.functor._
import cats.syntax.traverse._
import fs2.io.stdin
import io.tvc.vaccines.regional.{NHSClient, RegionalTotals}
import io.tvc.vaccines.scheduler.Scheduler
import io.tvc.vaccines.statistics.StatisticsClient
import io.tvc.vaccines.twitter.{Tweet, TwitterClient}
import io.tvc.vaccines.vaccines.{DailyTotals, VaccineClient}
import org.http4s.Uri
import org.http4s.blaze.client.BlazeClientBuilder

import java.time.LocalDate
import java.time.temporal.ChronoUnit.DAYS
import scala.concurrent.ExecutionContext.global
import cats.syntax.flatMap._

object App {

  case class Dependencies[F[_]](
    twitterClient: TwitterClient[F],
    vaccineClient: VaccineClient[F],
    dailyStatsClient: StatisticsClient[F, DailyTotals],
    regionalStatsClient: StatisticsClient[F, RegionalTotals],
    nhsClient: NHSClient[F],
    scheduler: Scheduler[F],
    today: LocalDate
  ) {
    def yesterday: LocalDate =
      today.minusDays(1)
  }

  def load[F[_]: Async: Clock]: Resource[F, Dependencies[F]] =
    for {
      s3 <- AWS.s3Client
      eventBridge <- AWS.eventBridgeClient
      config <- Resource.eval(Config.load[F])
      http <- BlazeClientBuilder[F](global).withDefaultSslContext.resource
      today <- Resource.eval(Sync[F].delay(LocalDate.now)) // bit OTT maybe
    } yield Dependencies[F](
      nhsClient = NHSClient(http),
      vaccineClient = VaccineClient(http),
      twitterClient = TwitterClient(config.twitter, http),
      dailyStatsClient = StatisticsClient(s3, config.dailyStatistics),
      regionalStatsClient = StatisticsClient(s3, config.regionalStatistics),
      scheduler = Scheduler(eventBridge, config.scheduler),
      today = today
    )

  type Operation[F[_], A] = Kleisli[OptionT[F, *], Dependencies[F], A]

  def fetchPastDailyStats[F[_]: Monad]: Operation[F, List[DailyTotals]] =
    Kleisli(d => OptionT.liftF(d.dailyStatsClient.fetch).filter(_.forall(_.date != d.yesterday)))

  def fetchPastRegionalStats[F[_]: Monad]: Operation[F, List[RegionalTotals]] =
    Kleisli(d => OptionT.liftF(d.regionalStatsClient.fetch))

  def findDaysToFetch[F[_]: Monad](past: List[DailyTotals]): Operation[F, NonEmptyList[LocalDate]] =
    Kleisli { d =>
      val start = past.headOption.fold(LocalDate.of(2021, 1, 12))(_.date.plusDays(1))
      val dates = (0L to DAYS.between(start, d.today).abs).map(start.plusDays).toList.reverse
      OptionT.fromOption(NonEmptyList.fromList(dates))
    }

  def fetchDailyStats[F[_]: Monad](day: LocalDate): Operation[F, List[DailyTotals]] =
    Kleisli(d => OptionT.liftF(d.vaccineClient.vaccineTotals(day).map(_.toList)))

  def combineStats[F[_]: Monad](
    fetched: List[DailyTotals],
    existing: List[DailyTotals]
  ): Operation[F, NonEmptyList[DailyTotals]] =
    Kleisli.liftF(OptionT.fromOption(NonEmptyList.fromList(fetched ++ existing)))

  def putDailyStats[F[_]: Monad](stats: NonEmptyList[DailyTotals]): Operation[F, Unit] =
    Kleisli(d => OptionT.liftF(d.dailyStatsClient.put(stats.toList)))

  def putRegionalStats[F[_]: Monad](stats: NonEmptyList[RegionalTotals]): Operation[F, Unit] =
    Kleisli(d => OptionT.liftF(d.regionalStatsClient.put(stats.toList)))

  def postTweet[F[_]: Monad](stats: NonEmptyList[DailyTotals]): Operation[F, Unit] =
    Kleisli(d => OptionT.liftF(d.twitterClient.tweet(Tweet.forStatistics(stats))))

  def waitUntilTomorrow[F[_]: Monad]: Operation[F, Unit] =
    Kleisli(d => OptionT.liftF(d.scheduler.stopUntilTomorrow))

  def putLine[F[_]: Sync](what: String): Operation[F, Unit] =
    Kleisli.liftF(OptionT.liftF(Sync[F].delay(println(what))))

  def readLine[F[_]: Sync]: Operation[F, String] =
    Kleisli.liftF(
      OptionT.liftF(
        stdin[F](bufSize = 1)
          .through(fs2.text.utf8Decode[F])
          .through(fs2.text.lines[F])
          .take(1)
          .compile
          .lastOrError
      )
    )

  def fetchRegionalTotals[F[_]: Monad](uri: Uri): Operation[F, RegionalTotals] =
    Kleisli(d => OptionT(d.nhsClient.regionalData(uri)))

  def addToList(now: RegionalTotals, old: List[RegionalTotals]): NonEmptyList[RegionalTotals] =
    NonEmptyList(now, old.filter(_.date != now.date))

  def regional[F[_]: Async]: Operation[F, Unit] =
    for {
      old     <- fetchPastRegionalStats
      _       <- putLine(s"Latest stats: ${old.headOption.fold("None")(_.date.toString)}")
      _       <- putLine(s"Please enter the URL to fetch stats from.")
      file    <- readLine
      _       <- putLine("Trying to fetch stats...")
      today   <- fetchRegionalTotals(Uri.unsafeFromString(file))
      updated = addToList(today, old)
      _       <- putRegionalStats(updated)
      _       <- putLine(s"Updated stats for ${today.date}")
    } yield ()

  def daily[F[_]: Monad]: Operation[F, Unit] =
    Kleisli { data =>
      OptionT(
        (
          for {
            past    <- fetchPastDailyStats
            days    <- findDaysToFetch(past)
            fetched <- days.toList.flatTraverse(fetchDailyStats[F])
            updated <- combineStats(fetched, past)
            _       <- putDailyStats(updated)
            _       <- postTweet(updated)
          } yield ()
        ).run(data).value.flatMap { _ =>
          waitUntilTomorrow.run(data).value
        }
      )
    }
}
