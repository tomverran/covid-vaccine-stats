package io.tvc.vaccines

import AWS.{eventBridgeClient, s3Client}
import scheduler.Scheduler
import statistics.StatisticsClient
import vaccines.VaccineClient

import cats.data.OptionT
import cats.effect.ExitCode.Success
import cats.effect.{Blocker, ExitCode, IO, IOApp, Resource}
import cats.syntax.apply._
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder

import java.time.LocalDate
import scala.concurrent.ExecutionContext.global

object Main extends IOApp {

  def log(what: String): IO[Unit] =
    IO.delay(println(what))

  val http: Resource[IO, Client[IO]] =
    BlazeClientBuilder[IO](global).withDefaultSslContext.resource

  val vaccines: Resource[IO, VaccineClient[IO]] =
    http.map(VaccineClient[IO])

  def statistics(config: StatisticsClient.Config): Resource[IO, StatisticsClient[IO]] =
    (Blocker[IO], s3Client).mapN(StatisticsClient[IO](_, _, config))

  def scheduler(config: Scheduler.Config): Resource[IO, Scheduler[IO]] =
    eventBridgeClient.map(Scheduler(_, config))

  /**
   * Fetch one day of vaccination data from the Vaccine Client and store it in S3.
   * If we already have the day in S3 then we skip calling the API.
   */
  def runForDay(
    vaccines: VaccineClient[IO],
    statistics: StatisticsClient[IO],
    scheduler: Scheduler[IO]
  )(date: LocalDate): IO[Unit] =
    (
      for {
        history <- OptionT.liftF(statistics.fetch).filterNot(_.exists(_.date == date))
        totals  <- OptionT(vaccines.vaccineTotals(date))
        _       <- OptionT.liftF(statistics.put(totals :: history))
        _       <- OptionT.liftF(scheduler.stopUntilTomorrow)
      } yield ()
    )
    .flatTapNone(log("No statistics saved"))
    .semiflatTap(_ => log("Saved statistics"))
    .getOrElse(())

  /**
   * Entrypoint into the app,
   * tries to pull one day of stats for yesterday
   */
  def run(args: List[String]): IO[ExitCode] =
      Config.load[IO].flatMap { config =>
        (
          vaccines,
          scheduler(config.scheduler),
          statistics(config.statistics),
          Resource.liftF(IO(LocalDate.now))
        ).tupled.use { case (nhs, scheduler, stats, today) =>
          runForDay(nhs, stats, scheduler)(today.minusDays(1)).as(Success)
        }
      }
}
