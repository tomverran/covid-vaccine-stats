package io.tvc.vaccines

import scheduler.Scheduler
import statistics.StatisticsClient
import twitter.{Config => TwitterConfig}

import cats.effect.{Async, ContextShift}
import cats.syntax.apply._
import ciris.{ConfigValue, env}
import org.http4s.client.oauth1.{Consumer, Token}

case class Config(
  statistics: StatisticsClient.Config,
  scheduler: Scheduler.Config,
  twitter: TwitterConfig
)

object Config {

  def consumer: ConfigValue[Consumer] =
    (
      env("TWITTER_CONSUMER_KEY").redacted,
      env("TWITTER_CONSUMER_SECRET").redacted,
    ).mapN(Consumer)

  def token: ConfigValue[Token] =
    (
      env("TWITTER_ACCESS_TOKEN").redacted,
      env("TWITTER_TOKEN_SECRET").redacted
    ).mapN(Token)

  def load[F[_]: ContextShift: Async]: F[Config] =
    (
      env("STATISTICS_BUCKET_NAME").map(StatisticsClient.Config),
      env("SCHEDULER_RULE_NAME").map(Scheduler.Config),
      (consumer, token).mapN(twitter.Config)
    ).mapN(Config.apply).load[F]
}
