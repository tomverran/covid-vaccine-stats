package io.tvc.vaccines
package regional

import regional.XSLXParser._

import cats.data.{Nested, NonEmptyList, ZipList}
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.traverse._
import cats.instances.list._

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import scala.util.Try

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
   * Find the date that these stats are for,
   * we take the end of the period mentioned in the sheet
   */
  private val statsDate: Op[LocalDate] = {
    val fmt = DateTimeFormatter.ofPattern("d MMMM uuuu")
    val dateCell = sheet("Contents") >> jumpToFirst("Period:") >> right >> string
    val dateStr = dateCell.map(_.split("to ").lastOption.map(_.replaceAll("^([0-9]{1,2})\\w{2} ", "$1 ")))
    dateStr.flatMapF(_.flatMap(d => Try(LocalDate.parse(d, fmt)).toOption).toRight("Failed to parse date"))
  }

  /**
   * Run the given operation against each row in a column where the first few rows
   * are things we don't care about, i.e. blank cells / a "total" row
   */
  private def column[A](op: Op[A]): Op[Vector[A]] =
    times(3)(downOrSkip(max = 1)) >>
      until(succeeds(op))(downOrSkip(max = 1)) >>
      eachRow(op)

  /**
   * A couple of variations are used for the region column name,
   * this operation will only succeed if we find an expected one
   */
  private val regionColumnHeading: Op[Unit] =
    orElse(
      expect("ICS/STP of Residence"),
      expect("ICS/STP of Residence Name")
    )

  /**
   * Do something with each row in the column of ICS/STP regions
   * We validate the region data is correct before running the op
   */
  private def regionColumn[A](op: Op[A]): Op[ZipList[A]] =
    peek(jumpUntil(regionColumnHeading, "STP column") >>
    column(region >> op).map(l => ZipList(l.toList)))

  /**
   * Find consecutive columns headed by age ranges
   * and produce a list where each item is a row containing a map of the column data
   */
  private val ageColumns: Op[ZipList[Map[AgeRange, Long]]] =
    jumpUntil(ageRange, name = "Age range") >>
      until(fails(ageRange)) {
        for {
          header <- ageRange
          values <- peek(column(long))
          _ <- orElse(right, firstCell)
        } yield values.map(header -> _)
      }.map(items => ZipList(items.transpose.map(_.toMap).toList))

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
  private def enforceCommonRanges(rs: RegionStatistics): RegionStatistics = {
    val commonKeys = rs.firstDose.keySet ++ rs.secondDose.keySet ++ rs.population.keySet
    rs.copy(
      firstDose = rs.firstDose.view.filterKeys(commonKeys.contains).toMap,
      secondDose = rs.secondDose.view.filterKeys(commonKeys.contains).toMap,
      population = rs.population.view.filterKeys(commonKeys.contains).toMap
    )
  }

  /**
   * Given some RegionStatistics ensure all the age group ranges are contiguous,
   * i.e. there are no gaps in the age ranges
   */
  private def validateRangesContiguous(rs: RegionStatistics): Either[String, RegionStatistics] = {
    val sortedKeys = rs.firstDose.keys.toList.sortBy(_.min)
    Either.cond(
      sortedKeys.zip(sortedKeys.drop(1).map(Some(_)) :+ None).forall { case (key, next) =>
        next.forall(n => key.max.contains(n.min - 1)) ||
          (next.isEmpty && key.max.isEmpty)
      },
      right = rs,
      left = s"Ranges not contiguous. Keys: ${sortedKeys.map(_.rendered).mkString(", ")}"
    )
  }

  private def validateRegionCount(rs: Map[Region, RegionStatistics]): Either[String, Unit] =
    Either.cond(rs.keySet.size == 42, (), "A number other than 42 regions found")

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
   * we have to use startsWith instead of a plain match
   * as the data has begun to have superscript suffixes
   */
  private def findDose(num: String): Op[Unit] =
    jumpUntil(failIfFalse(string.map(_.startsWith(s"$num dose")), s"$num dose"), s"$num dose")

  /**
   * Obtain regional statistics from the main table of stats
   * we then re-scan the region column below to make a map
   */
  private val regionStats: Nested[Op, ZipList, RegionStatistics] =
    (
      Nested(regionColumn(string)),
      Nested(populations),
      Nested(findDose("1st") >> peek(ageColumns.map(_.map(twiddleDoseAges)))),
      Nested(findDose("2nd") >> peek(ageColumns.map(_.map(twiddleDoseAges))))
    ).mapN(RegionStatistics.apply)

  /**
   * Put everything together to parse region statistics from the given XLSX document
   * We don't bother with percentage data as it can be calculated ad-hoc in the frontend
   */
  private val regionStatistics: Op[Map[Region, RegionStatistics]] =
    trySheets(NonEmptyList.of("Vaccinations by ICS STP & Age", "ICS STP")) >>
      (
        Nested(regionColumn(region)),
        regionStats
        ).tupled.value.flatMapF { stats =>
          stats
            .value
            .traverse { case (k, v) => validateRangesContiguous(enforceCommonRanges(v)).map(k -> _) }
            .map(_.toMap).flatTap(validateRegionCount)
      }

  /**
   * Join the date and the regional statistics
   * into some totals to be published onto the site
   */
  val regionalTotals: Op[RegionalTotals] =
    (regionStatistics, statsDate).mapN(RegionalTotals.apply)
}
