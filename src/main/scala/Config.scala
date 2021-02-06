package io.tvc.vaccines

import scheduler.Scheduler
import statistics.StatisticsClient
import twitter.{Config => TwitterConfig}

import cats.effect.{Async, Blocker, ContextShift}
import cats.syntax.apply._
import ciris.{ConfigValue, env}
import ciris.aws.ssm.{Param, params}
import org.http4s.client.oauth1.{Consumer, Token}
import software.amazon.awssdk.regions.Region.EU_WEST_1

case class Config(
  dailyStatistics: StatisticsClient.Config,
  regionalStatistics: StatisticsClient.Config,
  scheduler: Scheduler.Config,
  twitter: TwitterConfig
)

object Config {

  def consumer(param: Param): ConfigValue[Consumer] =
    (
      param("/vaccines/TWITTER_CONSUMER_KEY").redacted,
      param("/vaccines/TWITTER_CONSUMER_SECRET").redacted,
    ).mapN(Consumer)

  def token(param: Param): ConfigValue[Token] =
    (
      param("/vaccines/TWITTER_ACCESS_TOKEN").redacted,
      param("/vaccines/TWITTER_TOKEN_SECRET").redacted
    ).mapN(Token)

  def load[F[_]: ContextShift: Async](blocker: Blocker): F[Config] =
    params(blocker, EU_WEST_1).flatMap { param =>
      (
        env("STATISTICS_BUCKET_NAME").map(StatisticsClient.Config(_, "statistics.json")),
        env("STATISTICS_BUCKET_NAME").map(StatisticsClient.Config(_, "regional_v2.json")),
        env("SCHEDULER_RULE_NAME").map(Scheduler.Config),
        (consumer(param), token(param)).mapN(twitter.Config)
      ).mapN(Config.apply)
    }.load[F]
}
