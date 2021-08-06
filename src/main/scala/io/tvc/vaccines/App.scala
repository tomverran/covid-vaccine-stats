package io.tvc.vaccines

import cats.Monad
import cats.data.{Kleisli, NonEmptyList, OptionT}
import cats.effect.kernel.Async
import cats.effect.{Clock, Resource, Sync}
import fs2.io.stdin
import io.tvc.vaccines.regional.{NHSClient, RegionalTotals}
import io.tvc.vaccines.scheduler.Scheduler
import io.tvc.vaccines.statistics.StatisticsClient
import io.tvc.vaccines.twitter.{Tweet, TwitterClient}
import io.tvc.vaccines.vaccines.{DailyTotals, VaccineClient}
import org.http4s.Uri
import org.http4s.blaze.client.BlazeClientBuilder

import java.time.LocalDate
import scala.concurrent.ExecutionContext.global

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

  def fetchDailyStats[F[_]]: Operation[F, DailyTotals] =
    Kleisli(d => OptionT(d.vaccineClient.vaccineTotals(d.yesterday)))

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
    for {
      stats   <- fetchPastDailyStats
      daily   <- fetchDailyStats
      updated = NonEmptyList(daily, stats)
      _       <- putDailyStats(updated)
      _       <- postTweet(updated)
      _       <- waitUntilTomorrow
    } yield ()
}
