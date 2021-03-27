package io.tvc.vaccines
package regional

import regional.XSLXParser._

import cats.data.{Nested, NonEmptyList, ZipList}
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.functor._

object RegionParser {

  /**
   * Remove the "estimated population" suffix from a cell name
   * this appears in old versions of the population tables
   */
  private val removePopulationSuffix: Op[String] =
    string.map(_.replace("estimated population", "").trim)

  /**
   * Parse an e.g. 16-55 age range
   */
  private val hyphenated: Op[AgeRange] =
    removePopulationSuffix.flatMapF(AgeRange.parseClosed)

  /**
   * Parse an open ended age range,
   * either in the format 80+ or "over 80"
   */
  private val over: Op[AgeRange] =
    removePopulationSuffix.map(_.toLowerCase.replace("over ", "").replace("+", "").trim).flatMapF {
      case s if s.forall(_.isDigit) && s.nonEmpty => Right(AgeRange(s.toInt, None))
      case os => Left(s"Unable to parse $os as AgeRange")
    }

  /**
   * Parse an age range with no explicit lower bound
   * e.g. "Under 70" - We assume the lower bound is 0 but this is changed later for doses
   */
  private val under: Op[AgeRange] =
    removePopulationSuffix.map(_.toLowerCase.replace("under ", "")).flatMapF {
      case s if s.forall(_.isDigit) && s.nonEmpty => Right(AgeRange(0, Some(s.toInt - 1)))
      case os => Left(s"Unable to parse $os as AgeRange")
    }

  private val ageRange: Op[AgeRange] =
    orElse(hyphenated, orElse(over, under))

  private val region: Op[Region] =
    string.flatMapF(s => Region.forName(s).toRight(s"'$s' is not a region"))

  /**
   * Run the given operation against each row in a column where the first few rows
   * are things we don't care about, i.e. blank cells / a "total" row
   */
  private def column[A](op: Op[A]): Op[Vector[A]] =
    times(3)(downOrSkip(max = 1)) >>
    until(succeeds(op))(downOrSkip(max = 1)) >>
    eachRow(op)

  /**
   * Do something with each row in the column of ICS/STP regions
   * We validate the region data is correct before running the op
   */
  private def regionColumn[A](op: Op[A]): Op[ZipList[A]] =
    peek(jumpToNext("ICS/STP of Residence") >> column(region >> op).map(l => ZipList(l.toList)))

  /**
   * Find consecutive columns headed by age ranges
   * and produce a list where each item is a row containing a map of the column data
   */
  private val ageColumns: Op[ZipList[Map[AgeRange, Long]]] =
    jumpUntil(ageRange) >>
    until(fails(ageRange)) {
      for {
        header <- ageRange
        values <- peek(column(long))
        _      <- orElse(right, firstCell)
      } yield values.map(header -> _)
    }.map(items =>ZipList(items.transpose.map(_.toMap).toList))

  /**
   * Parse populations from the XLSX document
   * This is then joined onto region information below
   */
  private val populations: Op[ZipList[Map[AgeRange, Long]]] =
    peek(
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
        ) >> ageColumns
    )

  /**
   * Ensure that we only include age ranges present in all the per-age data
   * This makes me a bit sad but the ranges change all the time hence this representation
   */
  private def validateRanges(rs: RegionStatistics): RegionStatistics = {
    val commonKeys = rs.firstDose.keySet ++ rs.secondDose.keySet ++ rs.population.keySet
    rs.copy(
      firstDose = rs.firstDose.view.filterKeys(commonKeys.contains).toMap,
      secondDose = rs.secondDose.view.filterKeys(commonKeys.contains).toMap,
      population = rs.population.view.filterKeys(commonKeys.contains).toMap
    )
  }

  /**
   * Dose age ranges just use "under 50" but what they mean is 16-50
   * since the number of under 16s getting doses is minimal
   */
  private def twiddleDoseAges(map: Map[AgeRange, Long]): Map[AgeRange, Long] =
    map.map {
      case (AgeRange(0, max), v) => AgeRange(16, max) -> v
      case (range, v) => range -> v
    }

  /**
   * Obtain regional statistics from the main table of stats
   * we then re-scan the region column below to make a map
   */
  private val regionStats: Nested[Op, ZipList, RegionStatistics] =
    (
      Nested(regionColumn(string)),
      Nested(populations),
      Nested(jumpToNext("1st dose") >> peek(ageColumns.map(_.map(twiddleDoseAges)))),
      Nested(jumpToNext("2nd dose") >> peek(ageColumns.map(_.map(twiddleDoseAges))))
    ).mapN(RegionStatistics.apply).map(validateRanges)

  /**
   * Put everything together to parse region statistics from the given XLSX document
   * We don't bother with percentage data as it can be calculated ad-hoc in the frontend
   */
  val regionStatistics: Op[Map[Region, RegionStatistics]] =
    trySheets(NonEmptyList.of("Vaccinations by ICS STP & Age", "ICS STP")) >>
    (
      Nested(regionColumn(region)),
      regionStats
    ).tupled.value.map(_.value.toMap.view.mapValues(validateRanges).toMap)
}
