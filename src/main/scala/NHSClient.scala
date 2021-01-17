package io.tvc.vaccines

import cats.effect.{ConcurrentEffect, Sync}
import fs2.io.toInputStream
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.http4s.Uri.unsafeFromString
import org.http4s.client.Client
import org.http4s.headers.{AgentProduct, `User-Agent`}
import org.http4s.{Headers, Request, Uri}

import java.io.InputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

trait NHSClient[F[_]] {
  def vaccineTotals(date: LocalDate): F[Option[DoseTotals]]
}

object NHSClient {

  private val host: String =
    "https://www.england.nhs.uk"

  private val headers: Headers =
    Headers.of(`User-Agent`(AgentProduct("vaccine-stats-app")))

  private val fileName: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy/MM/'COVID-19-daily-announced-vaccinations-'dd-MMMM-yyyy'.xlsx'")

  private def fileUrl(date: LocalDate): Uri =
    unsafeFromString(s"$host/statistics/wp-content/uploads/sites/2/${fileName.format(date)}")

  /**
   * Extremely safe and resilient code to pull totals out of the NHS doc
   * no doubt the format will never ever change and this will always just be fine
   */
  private def parseWorkbook[F[_]: Sync](is: InputStream): F[DoseTotals] =
    Sync[F].delay {
      val workbook = new XSSFWorkbook(is)
      val sheet = workbook.getSheet("Total Vaccinations")
      DoseTotals(
        sheet.getRow(14).getCell(3).getNumericCellValue.toLong,
        sheet.getRow(15).getCell(3).getNumericCellValue.toLong
      )
    }

  def apply[F[_]](http: Client[F])(implicit F: ConcurrentEffect[F]): NHSClient[F] =
    date =>
      http.run(Request(uri = fileUrl(date), headers = headers)).use {
        case r if r.status.isSuccess =>
          r.body.through(toInputStream).evalMap(parseWorkbook[F]).compile.last
        case _ =>
          F.pure(None)
      }
}
