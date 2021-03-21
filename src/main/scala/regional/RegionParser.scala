package io.tvc.vaccines
package regional

import regional.ByAge._
import regional.XSLXParser._

import cats.data.{Nested, NonEmptyList, ZipList}
import cats.instances.int._
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.foldable._
import cats.syntax.functor._

object RegionParser {

  private case class AgeRange(min: Int, max: Option[Int]) {
    def rendered: String = max.fold(s"$min+")(m => s"$min-$m")
  }

  private object AgeRange {
    def apply(min: Int, max: Int): AgeRange = AgeRange(min, Some(max))
    def apply(min: Int): AgeRange = AgeRange(min, None)
  }

  val region: Op[Region] =
    string.flatMapF(s => Region.forName(s).toRight(s"'$s' is not a region"))

  /**
   * Run the given operation against each row in a column where the first few rows
   * are things we don't care about, i.e. blank cells / a "total" row
   */
  def columnWithTotal[A](name: String)(op: Op[A]): Nested[Op, ZipList, A] =
    Nested(
      jumpToNext(name) >>
      peek(
        times(3)(downOrSkip(max = 1)) >>
        until(succeeds(op))(downOrSkip(max = 1)) >>
        eachRow(op).map(b => ZipList(b.toList)))
    )

  /**
   * Do something with each row in the column of ICS/STP regions
   * We validate the region data is correct before running the op
   */
  def regionColumn[A](op: Op[A]): Op[ZipList[A]] =
    columnWithTotal("ICS/STP of Residence")(region >> op).value

  /**
   * Extract data broken down into under 80s and over 80s
   * using the given function to figure out the column names
   */
  def over80s(name: AgeRange => String): Op[ZipList[Over80s]] =
    (
      columnWithTotal(name(AgeRange(16, 79)))(long),
      columnWithTotal(name(AgeRange(80)))(long)
    ).mapN(Over80s.apply).value

  /**
   * Extract data broken down into under 70s and then buckets of 5 years
   * using the given function to figure out the column names
   */
  def over70s(name: AgeRange => String): Op[ZipList[Over70s]] =
    (
      columnWithTotal(name(AgeRange(16, 69)))(long),
      columnWithTotal(name(AgeRange(70, 74)))(long),
      columnWithTotal(name(AgeRange(75, 79)))(long),
      columnWithTotal(name(AgeRange(80)))(long)
    ).mapN(Over70s.apply).value

  /**
   * Extract data broken down into under 65s and then buckets of 5 years
   * using the given function to figure out the column names
   */
  def over65s(name: AgeRange => String): Op[ZipList[Over65s]] =
    (
      columnWithTotal(name(AgeRange(16, 64)))(long),
      columnWithTotal(name(AgeRange(65, 69)))(long),
      columnWithTotal(name(AgeRange(70, 74)))(long),
      columnWithTotal(name(AgeRange(75, 79)))(long),
      columnWithTotal(name(AgeRange(80)))(long)
    ).mapN(Over65s.apply).value

  def over60s(name: AgeRange => String): Op[ZipList[Over60s]] =
    (
      columnWithTotal(name(AgeRange(16, 59)))(long),
      columnWithTotal(name(AgeRange(60, 64)))(long),
      columnWithTotal(name(AgeRange(65, 69)))(long),
      columnWithTotal(name(AgeRange(70, 74)))(long),
      columnWithTotal(name(AgeRange(75, 79)))(long),
      columnWithTotal(name(AgeRange(80)))(long)
    ).mapN(Over60s.apply).value

  def over55s(name: AgeRange => String): Op[ZipList[Over55s]] =
    (
      columnWithTotal(name(AgeRange(16, 54)))(long),
      columnWithTotal(name(AgeRange(55, 59)))(long),
      columnWithTotal(name(AgeRange(60, 64)))(long),
      columnWithTotal(name(AgeRange(65, 69)))(long),
      columnWithTotal(name(AgeRange(70, 74)))(long),
      columnWithTotal(name(AgeRange(75, 79)))(long),
      columnWithTotal(name(AgeRange(80)))(long)
    ).mapN(Over55s.apply).value

  /**
   * Put all the above functions together to extract
   * a table of data bucketed by age
   */
  def byAge(name: AgeRange => String): Op[ZipList[ByAge]] = {
    orElse(
      over55s(name).map(_.widen),
      orElse(
        over60s(name).map(_.widen),
        orElse(
          over65s(name).map(_.widen),
          orElse(
            over70s(name).map(_.widen),
            over80s(name).map(_.widen)
          )
        )
      )
    )
  }

  /**
   * Turn the age range into the right column title format
   * used for the population estimates tables
   */
  def populationColumn(ageRange: AgeRange): String =
    s"${ageRange.rendered} estimated population"

  /**
   * Turn the age range into the right column title format
   * used for the dose totals tables
   */
  def doseColumn(ageRange: AgeRange): String =
    if (ageRange.min == 16) s"Under ${ageRange.max.foldMap(_ + 1)}" else ageRange.rendered

  /**
   * Parse populations from the XLSX document
   * This is then joined onto region information below
   */
  val populations: Op[ZipList[ByAge]] =
    trySheets(
      NonEmptyList.of(
        "Population estimates",
        "Population estimates (ONS)",
        "Vaccinations by ICS STP & Age"
      )
    ) >>
    orElse(
      jumpToFirst("ONS population mapped to ICS / STP"),
      jumpToFirst("2019 ONS Population Estimates")
    ) >>
    orElse(
      byAge(populationColumn),
      byAge(_.rendered)
    )

  /**
   * Obtain regional statistics from the main table of stats
   * we then re-scan the region column below to make a map
   */
  val regionStats: Nested[Op, ZipList, RegionStatistics] =
    (
      Nested(regionColumn(string)),
      Nested(peek(populations)),
      Nested(jumpToNext("1st dose") >> peek(byAge(doseColumn))),
      Nested(jumpToNext("2nd dose") >> peek(byAge(doseColumn)))
    ).mapN(RegionStatistics.apply)

  /**
   * Put everything together to parse region statistics from the given XLSX document
   * We don't bother with percentage data as it can be calculated ad-hoc in the frontend
   */
  val regionStatistics: Op[Map[Region, RegionStatistics]] =
    trySheets(NonEmptyList.of("Vaccinations by ICS STP & Age", "ICS STP")) >>
    (Nested(regionColumn(region)), regionStats).tupled.value.map(_.value.toMap)
}
