package io.tvc.vaccines
package regional

import regional.ByAge.{Over70s, Over80s}
import regional.Parser._

import cats.instances.either._
import cats.syntax.apply._
import cats.syntax.flatMap._
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ParserTest extends AnyWordSpec with Matchers {

  val wb: XSSFWorkbook =
    new XSSFWorkbook(getClass.getResourceAsStream("/4-February-2021.xlsx"))

  val region: Op[Region] =
    string.flatMapF(s => Region.forName(s).toRight(s"'$s' is not a region"))

  def over80s[A](data: Op[A]): Op[Over80s[A]] =
    (
      consumeL(data),
      consumeL(data),
    ).mapN(ByAge.Over80s.apply)

  def over70s[A](data: Op[A]): Op[Over70s[A]] =
    (
      consumeL(data),
      consumeL(data),
      consumeL(data),
      consumeL(data)
    ).mapN(ByAge.Over70s.apply)

  def byAge[A](parser: Op[A]): Op[ByAge[A]] =
    orElse(over70s(parser), over80s(parser))

  def verifyHeaders: Op[Unit] =
    sheet("Vaccinations by ICS STP & Age")
    jumpTo("Region of Residence") >> right >> right >>
    expect("ICS/STP of Residence") >> right >> down
    expect("Under 70") >> right
    expect("70-74") >> right
    expect("75-79") >> right
    expect("80+") >>
    jumpTo("Bedfordshire, Luton and Milton Keynes")

  def populations: Op[Map[Region, ByAge[Long]]] =
    sheet("Population estimates") >>
    jumpTo("ICS/STP of Residence") >>
    until(contains("Bedfordshire, Luton and Milton Keynes"))(downOrSkip(max = 2)) >>
    eachRow((consumeL(region), byAge(long)).tupled).map(_.toMap)

  def regionStatistics(populations: Map[Region, ByAge[Long]]): Op[(Region, RegionStatistics)] = {
    region.flatMap { name =>
      (
        consumeL(string),
        lift(populations.get(name).toRight(s"Cannot find population for $name")),
        consumeL(byAge(long)),
        byAge(long)
      ).mapN(RegionStatistics.apply).map(name -> _)
    }
  }


  "foo" should {

  "bar" in {

      println(
        create(wb).flatMap(
          populations.runA
        )
      )
    }
  }
}
