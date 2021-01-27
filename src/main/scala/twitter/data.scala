package io.tvc.vaccines
package twitter

import vaccines.DailyTotals

import cats.data.NonEmptyList
import cats.instances.long._
import cats.instances.option._
import cats.syntax.foldable._
import org.http4s.client.oauth1.{Consumer, Token}

import java.time.LocalDate
import java.time.format.DateTimeFormatter.ofPattern
import scala.math.BigDecimal.RoundingMode.HALF_UP

case class Config(
  consumer: Consumer,
  token: Token
)

case class Tweet(content: String)

object Tweet {

  /**
   * I stole this from
   * https://stackoverflow.com/questions/4011075/how-do-you-format-the-day-of-the-month-to-say-11th-21st-or-23rd-ordinal/4011116
   * I cannot believe there's no super obvious library to do this
   */
  private def suffix(number: Int): String =
    number % 10 match {
      case 1 => "st"
      case 2 => "nd"
      case 3 => "rd"
      case _ => "th"
    }

  private def formatDate(date: LocalDate): String =
    date.format(ofPattern("EEEE dd'_' MMMM YYYY")).replace("_", suffix(date.getDayOfMonth))

  /**
   * figure out what the percent change of first doses is for the latest day vs the day before
   */
  def percentChange(dailyTotals: NonEmptyList[DailyTotals]): BigDecimal = {
    val yesterday = dailyTotals.tail.headOption.foldMap(_.today.totalDoses)
    (dailyTotals.head.today.totalDoses - yesterday) / BigDecimal(yesterday) * 100
  }

  /**
   * Render a tweet fragment displaying the percent change calculated above
   */
  def changeText(dailyTotals: NonEmptyList[DailyTotals]): String = {
    val change = percentChange(dailyTotals)
    val formatted = change.setScale(1, HALF_UP).abs
    if (change > 0) {
      s"Up $formatted% 📈 on the day before."
    } else {
      s"Down $formatted% 📉 on the day before."
    }
  }

  def recordText(stats: NonEmptyList[DailyTotals]): String = {
    val totalToday = stats.head.today.totalDoses
    val record: Long = stats.tail.dropRight(1).maxByOption(_.today.totalDoses).foldMap(_.today.totalDoses)
    if (totalToday > record) "\n|🔹 A new record! ✨ Well done #nhs!" else ""
  }

  /**
   * Format a nice emoji laden tweet conveying the latest vaccine stats.
   */
  def forStatistics(dailyTotals: NonEmptyList[DailyTotals]): Tweet =
    Tweet(
      f"""
      |UK #covid19 #vaccine statistics for ${formatDate(dailyTotals.head.date)} 💉
      |
      |🔹 ${dailyTotals.head.today.firstDose}%,d first doses given.
      |🔹 ${dailyTotals.head.today.secondDose}%,d second doses given.${recordText(dailyTotals)}
      |🔹 ${changeText(dailyTotals)}
      |🔹 ${dailyTotals.head.total.firstDose}%,d total first doses given.
      |
      |See more: https://covid-vaccine-stats.uk
      |""".stripMargin
    )
}