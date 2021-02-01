package io.tvc.vaccines
package regional

import cats.effect.{ConcurrentEffect, Sync}
import fs2.io.toInputStream
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.http4s.Uri.unsafeFromString
import org.http4s.client.Client
import org.http4s.headers.{AgentProduct, `User-Agent`}
import org.http4s.{Headers, Request, Uri}

import java.io.InputStream
import java.time.LocalDate
import cats.syntax.functor._

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
    ofPattern("yyyy/MM/'COVID-19-weekly-announced-vaccinations-'dd-MMMM-yyyy'.xlsx'").format(date)

  private def fileUrl(date: LocalDate): Uri =
    unsafeFromString(s"$host/statistics/wp-content/uploads/sites/2/${fileName(date)}")

  /**
   * Extremely safe and resilient code to pull totals out of the NHS doc
   * no doubt the format will never ever change and this will always just be fine
   */
  private def parseWorkbook[F[_]: Sync](date: LocalDate)(is: InputStream): F[RegionalTotals] =
    Sync[F].delay {

      val workbook = new XSSFWorkbook(is)
      val sheet = workbook.getSheet("Vaccinations by ICS STP & Age")

      RegionalTotals(
        date = date,
        statistics = (15 to 56).toList.map(sheet.getRow).flatMap { row =>
          (
            for {
              regionName <- Option(row.getCell(3))
              svgRegion <- Region.forName(regionName.getStringCellValue)
              firstDoseUnder80 <- Option(row.getCell(4))
              firstDoseOver80 <- Option(row.getCell(5))
              firstDosePercentOfOver80 <- Option(row.getCell(6))
              secondDoseUnder80 <- Option(row.getCell(8))
              secondDoseOver80 <- Option(row.getCell(9))
              secondDosePercentOfOver80 <- Option(row.getCell(10))
            } yield svgRegion -> RegionStatistics(
              firstDose = DosesByAge(
                percentOver80 = firstDosePercentOfOver80.getNumericCellValue,
                under80 = firstDoseUnder80.getNumericCellValue.toLong,
                over80 = firstDoseOver80.getNumericCellValue.toLong,
              ),
              secondDose = DosesByAge(
                percentOver80 = secondDosePercentOfOver80.getNumericCellValue,
                under80 = secondDoseUnder80.getNumericCellValue.toLong,
                over80 = secondDoseOver80.getNumericCellValue.toLong
              )
            )
            ).toList
        }.toMap
      )
    }

  def apply[F[_]](http: Client[F])(implicit F: ConcurrentEffect[F]): NHSClient[F] =
    date =>
      http.run(Request(uri = fileUrl(date), headers = headers)).use {
        case r if r.status.isSuccess =>
          r.body.through(toInputStream).evalMap(parseWorkbook[F](date)).compile.last
        case _ =>
          F.delay(println(s"Got 404 for ${fileUrl(date)}")).as(None)
      }
}