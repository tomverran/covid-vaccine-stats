package io.tvc.vaccines
package regional

import regional.ByAge.{Over70s, Over80s}
import regional.XSLXParser._

import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.functor._

object RegionParser {

  private val region: Op[Region] =
    string.flatMapF(s => Region.forName(s).toRight(s"'$s' is not a region"))

  /**
   * Parse a series of columns containing data broken down
   * as either being for ages 16-19 or 80+. This is for docs prior to February
   */
  private def over80s[A](data: Op[A]): Op[Over80s[A]] =
    (
      consumeL(data),
      consumeL(data),
    ).mapN(ByAge.Over80s.apply)

  /**
   * Parse a series of columns broken down into 16-69, 70-75, 75-79 and 80+
   * this is for docs produced from February onwards
   */
  private def over70s[A](data: Op[A]): Op[Over70s[A]] =
    (
      consumeL(data),
      consumeL(data),
      consumeL(data),
      consumeL(data)
    ).mapN(ByAge.Over70s.apply)

  /**
   * Parse a row into data broken down into either of the available
   * granularities of age information. We ensure none of the data is blank
   * as that usually indicates the doc format isn't what we expect
   */
  private def byAge[A](parser: Op[A]): Op[ByAge[A]] =
    orElse(over70s(nonEmpty(parser)).widen, over80s(nonEmpty(parser)).widen)

  /**
   * Verify that we have all the headers we expect for data broken down
   * by one of the two age groups we currently anticipate seeing
   */
  private val verifyDoseHeaders: Op[Unit] =
    sheet("Vaccinations by ICS STP & Age") >>
      jumpTo("ICS/STP of Residence") >> right >> down >>
      orElse(
        a = consumeL(expect("Under 70")) >>
          consumeL(expect("70-74")) >>
          consumeL(expect("75-79")) >>
          consumeL(expect("80+")),
        b = consumeL(expect("Under 80")) >>
          consumeL(expect("80+"))
      )

  /**
   * Same as above but for population headers
   * which have slightly different names of course
   */
  private val verifyPopulationHeaders: Op[Unit] =
    sheet("Population estimates") >>
      jumpTo("ICS/STP of Residence") >> right >> down >>
      orElse(
        a = consumeL(expect("16-69 estimated population")) >>
          consumeL(expect("70-74 estimated population")) >>
          consumeL(expect("75-79 estimated population")) >>
          consumeL(expect("80+ estimated population")),
        b = consumeL(expect("16-79 estimated population")) >>
          consumeL(expect("80+ estimated population"))
      )

  /**
   * Go to the ICS/STP of Residence column then scan down
   * until we get to the first region directly below the heading
   */
  private val jumpToFirstRegion: Op[Unit] =
    jumpTo("ICS/STP of Residence") >>
    until(string.map(s => s.headOption.exists(_.isLetter) && !s.startsWith("ICS")))(downOrSkip(max = 2))

  /**
   * Parse populations from the XLSX document
   * This is then joined onto region information below
   */
  private val populations: Op[Map[Region, ByAge[Long]]] =
    verifyPopulationHeaders >>
    jumpToFirstRegion >>
    eachRow((consumeL(region), byAge(long)).tupled).map(_.toMap)

  /**
   * Put everything together to parse region statistics from the given XLSX document
   * We don't bother with percentage data as it can be calculated ad-hoc in the frontend
   */
  val regionStatistics: Op[Map[Region, RegionStatistics]] =
    populations.flatMap { pops =>
      verifyDoseHeaders >>
      jumpToFirstRegion >>
      eachRow(
        region.flatMap { name =>
          (
            consumeL(string),
            lift(pops.get(name).toRight(s"Cannot find population for $name")),
            byAge(long).flatTap(_ => consumeL(until(isEmpty)(right))),
            byAge(long)
          ).mapN(RegionStatistics.apply).map(name -> _)
        }
      ).map(_.toMap)
    }
}
