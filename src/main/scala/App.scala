package io.tvc.vaccines

import regional.{NHSClient, RegionalTotals}
import scheduler.Scheduler
import statistics.StatisticsClient
import twitter.{Tweet, TwitterClient}
import vaccines.{DailyTotals, VaccineClient}

import cats.data.{Kleisli, NonEmptyList, OptionT}
import cats.effect.{Blocker, Clock, ConcurrentEffect, ContextShift, IO, Resource, Sync}
import cats.{Applicative, Monad}
import org.http4s.client.blaze.BlazeClientBuilder

import java.time.DayOfWeek.THURSDAY
import java.time.LocalDate
import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration.DAYS

object App {

  case class Dependencies[F[_]](
    twitterClient: TwitterClient[F],
    vaccineClient: VaccineClient[F],
    dailyStatsClient: StatisticsClient[F, DailyTotals],
    regionalStatsClient: StatisticsClient[F, RegionalTotals],
    nhsClient: NHSClient[F],
    scheduler: Scheduler[F],
    date: LocalDate
  )

  def load[F[_]: ConcurrentEffect: ContextShift: Clock](
    implicit io: ContextShift[IO]
  ): Resource[F, Dependencies[F]] =
    for {
      block <- Blocker[F]
      s3 <- AWS.s3Client
      eventBridge <- AWS.eventBridgeClient
      config <- Resource.liftF(Config.load[F](block))
      http <- BlazeClientBuilder[F](global).withDefaultSslContext.resource
      today <- Resource.liftF(Sync[F].delay(LocalDate.now)) // bit OTT maybe
    } yield Dependencies[F](
      nhsClient = NHSClient(http),
      vaccineClient = VaccineClient(http),
      twitterClient = TwitterClient(config.twitter, http),
      dailyStatsClient = StatisticsClient(block, s3, config.dailyStatistics),
      regionalStatsClient = StatisticsClient(block, s3, config.regionalStatistics),
      scheduler = Scheduler(eventBridge, config.scheduler),
      date = today.minusDays(1)
    )

  type Operation[F[_], A] = Kleisli[OptionT[F, *], Dependencies[F], A]

  def fetchPastDailyStats[F[_]: Monad]: Operation[F, List[DailyTotals]] =
    Kleisli(d => OptionT.liftF(d.dailyStatsClient.fetch).filter(_.forall(_.date != d.date)))

  def fetchPastRegionalStats[F[_]: Monad]: Operation[F, List[RegionalTotals]] =
    Kleisli(d => OptionT.liftF(d.regionalStatsClient.fetch))

  def fetchDailyStats[F[_]]: Operation[F, DailyTotals] =
    Kleisli(d => OptionT(d.vaccineClient.vaccineTotals(d.date)))

  def putDailyStats[F[_]: Monad](stats: NonEmptyList[DailyTotals]): Operation[F, Unit] =
    Kleisli(d => OptionT.liftF(d.dailyStatsClient.put(stats.toList)))

  def putRegionalStats[F[_]: Monad](stats: NonEmptyList[RegionalTotals]): Operation[F, Unit] =
    Kleisli(d => OptionT.liftF(d.regionalStatsClient.put(stats.toList)))

  def postTweet[F[_]: Monad](stats: NonEmptyList[DailyTotals]): Operation[F, Unit] =
    Kleisli(d => OptionT.liftF(d.twitterClient.tweet(Tweet.forStatistics(stats))))

  def waitUntilTomorrow[F[_]: Monad]: Operation[F, Unit] =
    Kleisli(d => OptionT.liftF(d.scheduler.stopUntilTomorrow))

  def fetchLatestRegionalStats[F[_]: Monad]: Operation[F, RegionalTotals] =
    Kleisli { d =>
      val thursdayValue = THURSDAY.getValue
      val daysSinceThurs = d.date.getDayOfWeek.getValue - thursdayValue
      val toSubtract = if (daysSinceThurs < 0) 7 - daysSinceThurs else daysSinceThurs
      OptionT(d.nhsClient.regionalData(d.date.minusDays(toSubtract)))
    }

  def addToList(now: RegionalTotals, old: List[RegionalTotals]): NonEmptyList[RegionalTotals] =
    NonEmptyList(now, old.filter(_.date != now.date))

  def regional[F[_]: Monad]: Operation[F, Unit] =
    for {
      old     <- fetchPastRegionalStats
      today   <- fetchLatestRegionalStats
      updated = addToList(today, old)
      _       <- putRegionalStats(updated)
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
