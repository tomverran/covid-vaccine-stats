package io.tvc.vaccines

import cats.effect.{Async, ContextShift, IO, Resource}
import software.amazon.awssdk.services.eventbridge.EventBridgeAsyncClient
import software.amazon.awssdk.services.s3.S3AsyncClient

import java.util.concurrent.CompletableFuture
import scala.compat.java8.FutureConverters.toScala

object AWS {

  val s3Client: Resource[IO, S3AsyncClient] =
    Resource.make(IO.delay(S3AsyncClient.create))(c => IO.delay(c.close()))

  val eventBridgeClient: Resource[IO, EventBridgeAsyncClient] =
    Resource.make(IO.delay(EventBridgeAsyncClient.create))(c => IO.delay(c.close()))

  def capture[F[_]: Async, A](f: => CompletableFuture[A])(implicit c: ContextShift[IO]): F[A] =
    Async[F].liftIO(IO.fromFuture(IO(toScala(f))))
}
