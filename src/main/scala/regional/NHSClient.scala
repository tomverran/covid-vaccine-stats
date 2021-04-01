package io.tvc.vaccines
package regional

import regional.RegionParser.regionalTotals
import regional.XSLXParser.create

import cats.effect.{ConcurrentEffect, Sync}
import cats.syntax.functor._
import fs2.io.toInputStream
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.http4s.client.Client
import org.http4s.headers.{AgentProduct, `User-Agent`}
import org.http4s.{Headers, Request, Uri}

trait NHSClient[F[_]] {
  def regionalData(uri: Uri): F[Option[RegionalTotals]]
}

object NHSClient {

  private val headers: Headers =
    Headers.of(`User-Agent`(AgentProduct("https://covid-vaccine-stats.uk/")))

  /**
    * Extremely safe and resilient code to pull totals out of the NHS doc
    * no doubt the format will never ever change and this will always just be fine
   */
  private def parseWorkbook[F[_]: Sync](wb: XSSFWorkbook): F[RegionalTotals] =
    Sync[F].fromEither(
      create(wb)
        .flatMap(regionalTotals.runA)
        .left.map(error => new Exception(s"Failed to parse xlsx: $error"))
    )

  def apply[F[_]](http: Client[F])(implicit F: ConcurrentEffect[F]): NHSClient[F] =
    uri =>
      http.run(Request(uri = uri, headers = headers)).use {
        case r if r.status.isSuccess =>
          r.body
            .through(toInputStream)
            .map(new XSSFWorkbook(_))
            .evalMap(parseWorkbook[F])
            .compile
            .last
        case _ =>
          F.delay(println(s"Got 404 for $uri")).as(None)
    }
}
