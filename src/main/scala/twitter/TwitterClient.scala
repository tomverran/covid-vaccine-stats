package io.tvc.vaccines
package twitter

import cats.effect.Sync
import cats.syntax.flatMap._
import cats.syntax.functor._
import io.circe.Json
import org.http4s.Method.POST
import org.http4s.client.Client
import org.http4s.client.oauth1.signRequest
import org.http4s.syntax.literals._
import org.http4s.{Request, Uri, UrlForm}
import org.http4s.circe.CirceEntityCodec._

trait TwitterClient[F[_]] {
  def tweet(what: Tweet): F[Unit]
}

object TwitterClient {

  private val updateStatus: Uri =
    uri"https://api.twitter.com/1.1/statuses/update.json"

  def apply[F[_]: Sync](config: Config, http: Client[F]): TwitterClient[F] =
    tweet =>
      signRequest(
        Request[F](
          method = POST,
          uri = updateStatus
        ).withEntity(
          UrlForm("status" -> tweet.content)
        ),
        consumer = config.consumer,
        token = Some(config.token),
        callback = None,
        verifier = None
      ).flatMap { req =>
        http.run(req).use { resp =>
          resp.as[Json].flatTap(j => Sync[F].delay(println(j.spaces2))).void
        }
      }
}