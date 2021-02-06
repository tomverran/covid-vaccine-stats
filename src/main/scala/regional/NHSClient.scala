package io.tvc.vaccines
package regional

import cats.effect.{ConcurrentEffect, Sync}
import cats.syntax.flatMap._
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
    ofPattern(
      "yyyy/MM/'COVID-19-weekly-announced-vaccinations-'d-MMMM-yyyy'.xlsx'"
    ).format(date)

  private def fileUrl(date: LocalDate): Uri =
    unsafeFromString(
      s"$host/statistics/wp-content/uploads/sites/2/${fileName(date)}"
    )

  /**
    * Parse populations from the relevant sheet
    * we include populations + names in every day's data just in case they're changed
    */
  private def parsePopulations[F[_]: Sync](
    workbook: XSSFWorkbook
  ): F[Map[Region, Long]] =
    Sync[F].catchNonFatal {
      (15 to 56).toList
        .map(workbook.getSheet("Population estimates").getRow)
        .flatMap { row =>
          (
            for {
              regionName <- Option(row.getCell(9))
              name <- Region.forName(regionName.getStringCellValue)
              population <- Option(row.getCell(12))
            } yield name -> population.getNumericCellValue.toLong
          ).toList
        }
        .toMap
    }

  /**
    * Extremely safe and resilient code to pull totals out of the NHS doc
    * no doubt the format will never ever change and this will always just be fine
    */
  private def parseWorkbook[F[_]: Sync](
    date: LocalDate
  )(wb: XSSFWorkbook): F[RegionalTotals] =
    ???

  def apply[F[_]](
    http: Client[F]
  )(implicit F: ConcurrentEffect[F]): NHSClient[F] =
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
