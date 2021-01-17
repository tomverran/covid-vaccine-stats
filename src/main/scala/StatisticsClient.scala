package io.tvc.vaccines

import cats.effect.{Async, Blocker, ContextShift, IO}
import cats.syntax.flatMap._
import cats.syntax.functor._
import io.circe.parser.decode
import io.circe.syntax._
import software.amazon.awssdk.core.async.AsyncResponseTransformer
import software.amazon.awssdk.core.internal.async.ByteArrayAsyncRequestBody
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.{GetObjectRequest, GetObjectResponse, PutObjectRequest}

import java.util.concurrent.CompletableFuture
import scala.compat.java8.FutureConverters._

trait StatisticsClient[F[_]] {
  def fetchStatistics: F[List[DailyTotals]]
  def putStatistics(list: List[DailyTotals]): F[Unit]
}

/**
 * The code in here isn't very good
 * the AWS SDK v2 seems weirdly painful for S3
 */
object StatisticsClient {

  def capture[F[_]: Async, A](f: => CompletableFuture[A])(implicit c: ContextShift[IO]): F[A] =
    Async[F].liftIO(IO.fromFuture(IO(toScala(f))))

  def apply[F[_]: ContextShift](
    blocker: Blocker,
    s3: S3AsyncClient,
    bucket: String
  )(implicit c: ContextShift[IO], F: Async[F]): StatisticsClient[F] =
    new StatisticsClient[F] {

      val getObj: GetObjectRequest =
        GetObjectRequest.builder.bucket(bucket).key("statistics.json").build

      val putObj: PutObjectRequest =
        PutObjectRequest
          .builder
          .bucket(bucket)
          .key("statistics.json")
          .acl("public-read")
          .build

      def fetchStatistics: F[List[DailyTotals]] =
        capture(s3.getObject(getObj, AsyncResponseTransformer.toBytes[GetObjectResponse]))
          .flatMap(r => F.fromEither(decode[List[DailyTotals]](new String(r.asByteArray()))))

      def putStatistics(list: List[DailyTotals]): F[Unit] =
        capture(s3.putObject(putObj, new ByteArrayAsyncRequestBody(list.asJson.spaces2.getBytes))).void
    }
}
