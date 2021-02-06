package io.tvc.vaccines
package regional

import regional.RegionParser.regionStatistics
import regional.XSLXParser.create

import cats.effect.{ConcurrentEffect, Sync}
import cats.syntax.functor._
import fs2.io.toInputStream
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.http4s.Uri.unsafeFromString
import org.http4s.client.Client
import org.http4s.headers.{AgentProduct, `User-Agent`}
import org.http4s.{Headers, Request, Uri}

import java.time.LocalDate
import java.time.format.DateTimeFormatter.ofPattern

trait NHSClient[F[_]] {
  def regionalData(publishedOn: LocalDate): F[Option[RegionalTotals]]
}

object NHSClient {

  private val host: String =
    "https://www.england.nhs.uk"

  private val headers: Headers =
    Headers.of(`User-Agent`(AgentProduct("https://covid-vaccine-stats.uk/")))

  private def fileName(date: LocalDate): String =
    ofPattern("yyyy/MM/'COVID-19-weekly-announced-vaccinations-'d-MMMM-yyyy'.xlsx'").format(date)

  private def fileUrl(date: LocalDate): Uri =
    unsafeFromString(s"$host/statistics/wp-content/uploads/sites/2/${fileName(date)}")

  /**
    * Extremely safe and resilient code to pull totals out of the NHS doc
    * no doubt the format will never ever change and this will always just be fine
   */
  private def parseWorkbook[F[_]: Sync](date: LocalDate)(wb: XSSFWorkbook): F[RegionalTotals] =
    Sync[F].fromEither(
      create(wb)
        .flatMap(regionStatistics.runA)
        .map(stats => RegionalTotals(stats, date))
        .left.map(error => new Exception(s"Failed to parse xlsx: $error"))
    )

  def apply[F[_]](http: Client[F])(implicit F: ConcurrentEffect[F]): NHSClient[F] =
    date =>
      http.run(Request(uri = fileUrl(date), headers = headers)).use {
        case r if r.status.isSuccess =>
          r.body
            .through(toInputStream)
            .map(new XSSFWorkbook(_))
            .evalMap(parseWorkbook[F](date))
            .compile
            .last
        case _ =>
          F.delay(println(s"Got 404 for ${fileUrl(date)}")).as(None)
    }
}
