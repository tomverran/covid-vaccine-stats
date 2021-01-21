package io.tvc.vaccines

import cats.effect.{Async, ContextShift, IO, Resource, Sync}
import software.amazon.awssdk.services.eventbridge.EventBridgeAsyncClient
import software.amazon.awssdk.services.s3.S3AsyncClient

import java.util.concurrent.CompletableFuture
import scala.compat.java8.FutureConverters.toScala

object AWS {

  def s3Client[F[_]: Sync]: Resource[F, S3AsyncClient] =
    Resource.make(Sync[F].delay(S3AsyncClient.create))(c => Sync[F].delay(c.close()))

  def eventBridgeClient[F[_]: Sync]: Resource[F, EventBridgeAsyncClient] =
    Resource.make(Sync[F].delay(EventBridgeAsyncClient.create))(c => Sync[F].delay(c.close()))

  def capture[F[_]: Async, A](f: => CompletableFuture[A])(implicit c: ContextShift[IO]): F[A] =
    Async[F].liftIO(IO.fromFuture(IO(toScala(f))))
}
