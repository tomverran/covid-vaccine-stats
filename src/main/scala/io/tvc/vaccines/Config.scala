package io.tvc.vaccines

import cats.effect.Async
import cats.syntax.apply._
import ciris.aws.ssm.{Param, params}
import ciris.{ConfigValue, env}
import io.tvc.vaccines.scheduler.Scheduler
import io.tvc.vaccines.statistics.StatisticsClient
import io.tvc.vaccines.twitter.{Config => TwitterConfig}
import org.http4s.client.oauth1.{Consumer, Token}
import software.amazon.awssdk.regions.Region.EU_WEST_1

case class Config(
  dailyStatistics: StatisticsClient.Config,
  regionalStatistics: StatisticsClient.Config,
  scheduler: Scheduler.Config,
  twitter: TwitterConfig
)

object Config {

  def consumer[F[_]](param: Param[F]): ConfigValue[F, Consumer] =
    (
      param("/vaccines/TWITTER_CONSUMER_KEY").redacted,
      param("/vaccines/TWITTER_CONSUMER_SECRET").redacted,
    ).mapN(Consumer)

  def token[F[_]](param: Param[F]): ConfigValue[F, Token] =
    (
      param("/vaccines/TWITTER_ACCESS_TOKEN").redacted,
      param("/vaccines/TWITTER_TOKEN_SECRET").redacted
    ).mapN(Token)

  def load[F[_]: Async]: F[Config] =
    params(EU_WEST_1).flatMap { param =>
      (
        env("STATISTICS_BUCKET_NAME").map(StatisticsClient.Config(_, "statistics_v2.json")),
        env("STATISTICS_BUCKET_NAME").map(StatisticsClient.Config(_, "regional_v4.json")),
        env("SCHEDULER_RULE_NAME").map(Scheduler.Config),
        (consumer(param), token(param)).mapN(twitter.Config)
      ).mapN(Config.apply)
    }.load[F]
}
