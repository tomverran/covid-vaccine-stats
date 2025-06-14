package io.tvc.vaccines.statistics

import cats.effect.Async
import cats.syntax.flatMap._
import cats.syntax.functor._
import io.circe.Codec
import io.circe.parser.decode
import io.circe.syntax._
import software.amazon.awssdk.core.async.AsyncResponseTransformer
import software.amazon.awssdk.core.internal.async.ByteArrayAsyncRequestBody
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model._

trait StatisticsClient[F[_], A] {
  def put(list: List[A]): F[Unit]
  def fetch: F[List[A]]
}

/**
 * The code in here isn't very good
 * the AWS SDK v2 seems weirdly painful for S3
 */
object StatisticsClient {

  case class Config(
    bucketName: String,
    objectName: String
  )

  def apply[F[_], A: Codec](s3: S3AsyncClient, config: Config)(implicit F: Async[F]): StatisticsClient[F, A] =
    new StatisticsClient[F, A] {

      val getObj: GetObjectRequest =
        GetObjectRequest.builder.bucket(config.bucketName).key(config.objectName).build

      val putObj: PutObjectRequest =
        PutObjectRequest
          .builder
          .bucket(config.bucketName)
          .key(config.objectName)
          .cacheControl("no-store")
          .acl("public-read")
          .build

      def fetch: F[List[A]] =
        F.recoverWith(
          F.fromCompletableFuture(
            F.delay(s3.getObject(getObj, AsyncResponseTransformer.toBytes[GetObjectResponse]))
          ).flatMap(r => F.fromEither(decode[List[A]](new String(r.asByteArray()))))
        ) {
          case e if Option(e.getCause).exists(_.isInstanceOf[NoSuchKeyException]) =>
            F.pure(List.empty)
          case o =>
            F.raiseError(o)
        }

      def put(list: List[A]): F[Unit] =
        F.fromCompletableFuture {
          val body = new ByteArrayAsyncRequestBody(list.asJson.spaces2.getBytes, "application/json")
          F.delay(s3.putObject(putObj, body))
        }.void
    }
}
