package io.tvc.vaccines.regional

import cats.effect.Sync
import cats.effect.kernel.Async
import cats.syntax.functor._
import fs2.io.toInputStream
import io.tvc.vaccines.regional.RegionParser.regionalTotals
import io.tvc.vaccines.regional.XSLXParser.create
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.http4s.client.Client
import org.http4s.headers.`User-Agent`
import org.http4s.{Headers, ProductId, Request, Uri}

trait NHSClient[F[_]] {
  def regionalData(uri: Uri): F[Option[RegionalTotals]]
}

object NHSClient {

  private val headers: Headers =
    Headers(`User-Agent`(ProductId("https://covid-vaccine-stats.uk/")))

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

  def apply[F[_]](http: Client[F])(implicit F: Async[F]): NHSClient[F] =
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
