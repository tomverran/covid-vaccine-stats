package io.tvc.vaccines

import cats.effect.{Async, ContextShift}
import cats.syntax.apply._
import ciris.env
import io.tvc.vaccines.scheduler.Scheduler
import io.tvc.vaccines.statistics.StatisticsClient

case class Config(
  statistics: StatisticsClient.Config,
  scheduler: Scheduler.Config
)

object Config {

  def load[F[_]: ContextShift: Async]: F[Config] =
    (
      env("STATISTICS_BUCKET_NAME").map(StatisticsClient.Config),
      env("SCHEDULER_RULE_NAME").map(Scheduler.Config)
    ).mapN(Config.apply).load[F]
}
