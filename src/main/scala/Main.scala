package io.tvc.vaccines

import DailyTotals.addDay

import cats.data.OptionT
import cats.effect.{Blocker, ExitCode, IO, IOApp, Resource}
import cats.syntax.apply._
import ciris.env
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import software.amazon.awssdk.services.s3.S3AsyncClient

import java.time.LocalDate
import scala.concurrent.ExecutionContext.global

object Main extends IOApp {

  /**
   * Extremely advanced pure functional logging library
   */
  def log(what: String): IO[Unit] =
    IO.delay(println(what))

  val bucketName: IO[String] =
    env("STATISTICS_BUCKET_NAME").load[IO]

  val http: Resource[IO, Client[IO]] =
    BlazeClientBuilder[IO](global).withDefaultSslContext.resource

  val s3Client: Resource[IO, S3AsyncClient] =
    Resource.make(IO.delay(S3AsyncClient.create))(c => IO.delay(c.close()))

  val nhs: Resource[IO, NHSClient[IO]] =
    http.map(NHSClient[IO])

  val statistics: Resource[IO, StatisticsClient[IO]] =
    (
      Blocker[IO],
      s3Client,
      Resource.liftF(bucketName)
    ).mapN(StatisticsClient[IO])

  def runForDay(nhs: NHSClient[IO], stats: StatisticsClient[IO])(date: LocalDate): IO[Unit] =
    (
      for {
        today <- OptionT(nhs.vaccineTotals(date))
        history <- OptionT.liftF(stats.fetchStatistics).filterNot(_.exists(_.date == date))
        _ <- OptionT.liftF(stats.putStatistics(addDay(date, today)(history)))
      } yield ()
    )
    .flatTapNone(log("No statistics saved"))
    .semiflatTap(_ => log("Saved statistics"))
    .getOrElse(())

    def run(args: List[String]): IO[ExitCode] =
      (
        nhs,
        statistics,
        Resource.liftF(IO(LocalDate.now))
      ).tupled.use { case (nhs, stats, today) =>
        runForDay(nhs, stats)(today).as(ExitCode.Success)
      }
}
