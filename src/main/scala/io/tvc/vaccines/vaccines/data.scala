package io.tvc.vaccines.vaccines

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

import java.time.LocalDate

case class DoseTotals(
  firstDose: Long,
  secondDose: Long,
  thirdDose: Long
) {
  def totalDoses: Long =
    firstDose + secondDose + thirdDose
}

object DoseTotals {
  implicit val codec: Codec[DoseTotals] = deriveCodec
}

case class DailyTotals(
  date: LocalDate,
  today: DoseTotals,
  total: DoseTotals
)

object DailyTotals {

  implicit val codec: Codec[DailyTotals] = deriveCodec

  /**
   * Given a list of DailyTotals add the new day information to it,
   * calculating the daily total of vaccinations from the previous days total
   */
  def addDay(date: LocalDate, today: DoseTotals)(history: List[DailyTotals]): List[DailyTotals] =
    history.headOption.fold(DailyTotals(date, today, today)) { prev =>
      DailyTotals(
        date = date,
        today = DoseTotals(
          firstDose = today.firstDose - prev.total.firstDose,
          secondDose = today.secondDose - prev.total.secondDose,
          thirdDose = today.thirdDose - prev.total.thirdDose
        ),
        total = today
      )
    } :: history
}
