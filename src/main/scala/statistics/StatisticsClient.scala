package io.tvc.vaccines
package statistics

import cats.effect.{Async, Blocker, ContextShift, IO}
import cats.syntax.flatMap._
import cats.syntax.functor._
import io.circe.parser.decode
import io.circe.syntax._
import io.tvc.vaccines.AWS.capture
import io.tvc.vaccines.vaccines.DailyTotals
import software.amazon.awssdk.core.async.AsyncResponseTransformer
import software.amazon.awssdk.core.internal.async.ByteArrayAsyncRequestBody
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.{GetObjectRequest, GetObjectResponse, PutObjectRequest}

trait StatisticsClient[F[_]] {
  def fetch: F[List[DailyTotals]]
  def put(list: List[DailyTotals]): F[Unit]
}

/**
 * The code in here isn't very good
 * the AWS SDK v2 seems weirdly painful for S3
 */
object StatisticsClient {

  case class Config(bucketName: String)

  def apply[F[_]: ContextShift](
    blocker: Blocker,
    s3: S3AsyncClient,
    config: Config
  )(implicit c: ContextShift[IO], F: Async[F]): StatisticsClient[F] =
    new StatisticsClient[F] {

      val getObj: GetObjectRequest =
        GetObjectRequest.builder.bucket(config.bucketName).key("statistics.json").build

      val putObj: PutObjectRequest =
        PutObjectRequest
          .builder
          .bucket(config.bucketName)
          .key("statistics.json")
          .acl("public-read")
          .build

      def fetch: F[List[DailyTotals]] =
        capture(s3.getObject(getObj, AsyncResponseTransformer.toBytes[GetObjectResponse]))
          .flatMap(r => F.fromEither(decode[List[DailyTotals]](new String(r.asByteArray()))))

      def put(list: List[DailyTotals]): F[Unit] =
        capture(s3.putObject(putObj, new ByteArrayAsyncRequestBody(list.asJson.spaces2.getBytes))).void
    }
}
