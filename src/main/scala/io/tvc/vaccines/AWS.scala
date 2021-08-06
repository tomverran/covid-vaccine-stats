package io.tvc.vaccines

import cats.effect.{Resource, Sync}
import software.amazon.awssdk.services.eventbridge.EventBridgeAsyncClient
import software.amazon.awssdk.services.s3.S3AsyncClient

object AWS {

  def s3Client[F[_]: Sync]: Resource[F, S3AsyncClient] =
    Resource.make(Sync[F].delay(S3AsyncClient.create))(c => Sync[F].delay(c.close()))

  def eventBridgeClient[F[_]: Sync]: Resource[F, EventBridgeAsyncClient] =
    Resource.make(Sync[F].delay(EventBridgeAsyncClient.create))(c => Sync[F].delay(c.close()))
}
