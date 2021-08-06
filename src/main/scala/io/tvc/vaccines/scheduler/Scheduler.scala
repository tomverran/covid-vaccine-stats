package io.tvc.vaccines.scheduler

import cats.effect.{Async, Clock}
import cats.syntax.flatMap._
import cats.syntax.functor._
import software.amazon.awssdk.services.eventbridge.EventBridgeAsyncClient
import software.amazon.awssdk.services.eventbridge.model.PutRuleRequest

import java.time.{Instant, LocalDateTime, LocalTime, ZoneId}


trait Scheduler[F[_]] {
  def stopUntilTomorrow: F[Unit]
}

object Scheduler {

  case class Config(ruleName: String)
  private val utc: ZoneId = ZoneId.of("UTC")
  private val london: ZoneId = ZoneId.of("Europe/London")
  private val fourPm: LocalTime = LocalTime.of(16, 0)

  def apply[F[_]: Async: Clock](
    client: EventBridgeAsyncClient,
    config: Config,
  ): Scheduler[F] =
    new Scheduler[F] {

      val nextStartTime: F[LocalDateTime] =
        Clock[F]
          .realTime
          .map { time =>
            Instant
              .ofEpochMilli(time.toMillis)
              .atZone(london)
              .toLocalDate
              .plusDays(1)
              .atTime(fourPm)
              .atZone(utc)
              .toLocalDateTime
          }

      def putRuleRequest: F[PutRuleRequest] =
        nextStartTime.map { t =>
          PutRuleRequest
            .builder
            .name(config.ruleName)
            .scheduleExpression(s"cron(0/5 ${t.getHour}-23 ${t.getDayOfMonth} * ? *)")
            .build
        }

      def stopUntilTomorrow: F[Unit] =
        putRuleRequest.flatMap { req =>
          Async[F].fromCompletableFuture(Async[F].delay(client.putRule(req))).void
        }
    }
}