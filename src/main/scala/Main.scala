package io.tvc.vaccines

import cats.data.OptionT
import cats.effect.ExitCode.Success
import cats.effect.{Blocker, ExitCode, IO, IOApp, Resource}
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.traverse._
import ciris.env
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import software.amazon.awssdk.services.s3.S3AsyncClient

import java.time.LocalDate
import scala.concurrent.ExecutionContext.global

object Main extends IOApp {

  def log(what: String): IO[Unit] =
    IO.delay(println(what))

  val bucketName: IO[String] =
    env("STATISTICS_BUCKET_NAME").load[IO]

  val http: Resource[IO, Client[IO]] =
    BlazeClientBuilder[IO](global).withDefaultSslContext.resource

  val s3Client: Resource[IO, S3AsyncClient] =
    Resource.make(IO.delay(S3AsyncClient.create))(c => IO.delay(c.close()))

  val nhs: Resource[IO, VaccineClient[IO]] =
    http.map(VaccineClient[IO])

  val statistics: Resource[IO, StatisticsClient[IO]] =
    (
      Blocker[IO],
      s3Client,
      Resource.liftF(bucketName)
    ).mapN(StatisticsClient[IO])

  /**
   * Fetch one day of vaccination data from the Vaccine Client and store it in S3.
   * If we already have the day in S3 then we skip calling the API.
   */
  def runForDay(nhs: VaccineClient[IO], stats: StatisticsClient[IO])(date: LocalDate): IO[Unit] =
    (
      for {
        history <- OptionT.liftF(stats.fetchStatistics).filterNot(_.exists(_.date == date))
        totals <- OptionT(nhs.vaccineTotals(date))
        _ <- OptionT.liftF(stats.putStatistics(totals :: history))
      } yield ()
    )
    .flatTapNone(log("No statistics saved"))
    .semiflatTap(_ => log("Saved statistics"))
    .getOrElse(())

  /**
   * This can be run if the data needs to be entirely reingested
   * but it is comically inefficient since it does it day-by-day
   */
  def backfill(nhs: VaccineClient[IO], stats: StatisticsClient[IO]): IO[Unit] =
    stats.putStatistics(List.empty) >>
      (11 to 16).toList
        .map(LocalDate.of(2021, 1, _))
        .traverse(runForDay(nhs, stats))
        .void

  /**
   * Entrypoint into the app,
   * tries to pull one day of stats for yesterday
   */
  def run(args: List[String]): IO[ExitCode] =
      (
        nhs,
        statistics,
        Resource.liftF(IO(LocalDate.now))
      ).tupled.use { case (nhs, stats, today) =>
        runForDay(nhs, stats)(today.minusDays(1)).as(Success)
      }
}
