package io.tvc.vaccines

import scheduler.Scheduler
import statistics.StatisticsClient
import twitter.{Tweet, TwitterClient}
import vaccines.{DailyTotals, VaccineClient}

import cats.Monad
import cats.data.{Kleisli, NonEmptyList, OptionT}
import cats.effect.{Blocker, Clock, ConcurrentEffect, ContextShift, IO, Resource, Sync}
import org.http4s.client.blaze.BlazeClientBuilder

import java.time.LocalDate
import scala.concurrent.ExecutionContext.global

object App {

  case class Dependencies[F[_]](
    twitterClient: TwitterClient[F],
    vaccineClient: VaccineClient[F],
    statisticsClient: StatisticsClient[F],
    scheduler: Scheduler[F],
    date: LocalDate
  )

  def load[F[_]: ConcurrentEffect: ContextShift: Clock](
    implicit io: ContextShift[IO]
  ): Resource[F, Dependencies[F]] =
    for {
      block       <- Blocker[F]
      s3          <- AWS.s3Client
      eventBridge <- AWS.eventBridgeClient
      config      <- Resource.liftF(Config.load[F](block))
      http        <- BlazeClientBuilder[F](global).withDefaultSslContext.resource
      today       <- Resource.liftF(Sync[F].delay(LocalDate.now)) // bit OTT maybe
    } yield Dependencies[F](
      twitterClient = TwitterClient(config.twitter, http),
      vaccineClient = VaccineClient(http),
      statisticsClient = StatisticsClient(block, s3, config.statistics),
      scheduler = Scheduler(eventBridge, config.scheduler),
      date = today.minusDays(1)
    )

  type Operation[F[_], A] = Kleisli[OptionT[F, *], Dependencies[F], A]

  def fetchPastStats[F[_]: Monad]: Operation[F, List[DailyTotals]] =
    Kleisli(d => OptionT.liftF(d.statisticsClient.fetch).filter(_.forall(_.date != d.date)))

  def fetchDailyStats[F[_]]: Operation[F, DailyTotals] =
    Kleisli(d => OptionT(d.vaccineClient.vaccineTotals(d.date)))

  def putNewStats[F[_]: Monad](stats: NonEmptyList[DailyTotals]): Operation[F, Unit] =
    Kleisli(d => OptionT.liftF(d.statisticsClient.put(stats.toList)))

  def postTweet[F[_]: Monad](stats: NonEmptyList[DailyTotals]): Operation[F, Unit] =
    Kleisli(d => OptionT.liftF(d.twitterClient.tweet(Tweet.forStatistics(stats))))

  def waitUntilTomorrow[F[_]: Monad]: Operation[F, Unit] =
    Kleisli(d => OptionT.liftF(d.scheduler.stopUntilTomorrow))

  def application[F[_]: Monad]: Operation[F, Unit] =
    for {
      stats   <- fetchPastStats
      daily   <- fetchDailyStats
      updated =  NonEmptyList(daily, stats)
      _       <- putNewStats(updated)
      _       <- postTweet(updated)
      _       <- waitUntilTomorrow
    } yield ()
}
