package io.tvc.vaccines

import cats.effect.Sync
import cats.syntax.apply._
import cats.syntax.functor._
import io.circe.Decoder
import io.circe.syntax._
import org.http4s.Method.GET
import org.http4s.Status.NoContent
import org.http4s.circe.CirceEntityCodec._
import org.http4s.client.middleware.GZip
import org.http4s.client.{Client, UnexpectedStatus}
import org.http4s.syntax.literals._
import org.http4s.{Request, Uri}

import java.time.LocalDate

trait VaccineClient[F[_]] {
  def vaccineTotals(publishedOn: LocalDate): F[Option[DailyTotals]]
}

object VaccineClient {

  private def doseTotals(time: String): Decoder[DoseTotals] =
    Decoder { c =>
      (
        c.get[Long](s"${time}PeopleVaccinatedFirstDoseByPublishDate"),
        c.get[Long](s"${time}PeopleVaccinatedSecondDoseByPublishDate")
      ).mapN(DoseTotals.apply)
    }

  private val dailyTotals: Decoder[DailyTotals] =
    Decoder { c =>
      (
        c.get[LocalDate]("date"),
        c.as(doseTotals(time = "new")),
        c.as(doseTotals(time = "cum"))
      ).mapN(DailyTotals.apply)
    }

  private implicit val decoder: Decoder[Option[DailyTotals]] =
    Decoder.decodeList(dailyTotals).prepare(_.downField("data")).map(_.headOption)

  private val structure: Map[String, String] =
    List(
      "date",
      "newPeopleVaccinatedFirstDoseByPublishDate",
      "cumPeopleVaccinatedFirstDoseByPublishDate",
      "newPeopleVaccinatedSecondDoseByPublishDate",
      "cumPeopleVaccinatedSecondDoseByPublishDate",
    ).map(item => item -> item).toMap

  private val uri: Uri =
    uri"https://api.coronavirus.data.gov.uk/v1/data"

  private def request[F[_]](date: LocalDate): Request[F] =
    Request[F](
      method = GET,
      uri = uri
        .withQueryParam("structure", structure.asJson.noSpaces)
        .withQueryParam("filters", s"areaType=overview;date=$date")
        .withQueryParam("format", "json")
        .withQueryParam("page", 1)
    )

  def apply[F[_]](http: Client[F])(implicit F: Sync[F]): VaccineClient[F] =
    date => GZip[F]()(http).run(request(date)).use {
      case resp if resp.status == NoContent => F.pure(None)
      case resp if resp.status.isSuccess => resp.as[Option[DailyTotals]]
      case resp => F.raiseError(UnexpectedStatus(resp.status))
    }
}
