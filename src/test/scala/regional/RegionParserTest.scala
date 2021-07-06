package io.tvc.vaccines
package regional

import regional.RegionParser._
import regional.XSLXParser._

import cats.instances.either._
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.scalatest.{Inspectors, ParallelTestExecution}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class RegionParserTest extends AnyWordSpec with Matchers with ParallelTestExecution {

  "RegionParser" should {

    "parse January format docs" in {
      val book = new XSSFWorkbook(getClass.getResourceAsStream("/28-January-2021.xlsx"))
      create(book).flatMap(regionalTotals.runA).map(_.statistics.keySet.size) shouldBe Right(42)
    }

    "parse 4th February format docs" in {
      val book = new XSSFWorkbook(getClass.getResourceAsStream("/4-February-2021.xlsx"))
      create(book).flatMap(regionalTotals.runA).map(_.statistics.keySet.size) shouldBe Right(42)
    }

    "parse 11th February format docs" in {
      val book = new XSSFWorkbook(getClass.getResourceAsStream("/11-February-2021.xlsx"))
      create(book).flatMap(regionalTotals.runA).map(_.statistics.keySet.size) shouldBe Right(42)
    }

    "parse 18th February format docs" in {
      val book = new XSSFWorkbook(getClass.getResourceAsStream("/18-February-2021.xlsx"))
      create(book).flatMap(regionalTotals.runA).map(_.statistics.keySet.size) shouldBe Right(42)
    }

    "parse 25th February format docs" in {
      val book = new XSSFWorkbook(getClass.getResourceAsStream("/25-February-2021.xlsx"))
      create(book).flatMap(regionalTotals.runA).map(_.statistics.keySet.size) shouldBe Right(42)
    }

    "parse 4th March format docs" in {
      val book = new XSSFWorkbook(getClass.getResourceAsStream("/4-March-2021.xlsx"))
      create(book).flatMap(regionalTotals.runA).map(_.statistics.keySet.size) shouldBe Right(42)
    }

    "parse 11th March format docs" in {
      val book = new XSSFWorkbook(getClass.getResourceAsStream("/11-March-2021.xlsx"))
      create(book).flatMap(regionalTotals.runA).map(_.statistics.keySet.size) shouldBe Right(42)
    }

    "parse 18th March format docs" in {
      val book = new XSSFWorkbook(getClass.getResourceAsStream("/18-March-2021.xlsx"))
      create(book).flatMap(regionalTotals.runA).map(_.statistics.keySet.size) shouldBe Right(42)
    }

    "parse 25th March format docs" in {
      val book = new XSSFWorkbook(getClass.getResourceAsStream("/25-March-2021.xlsx"))
      create(book).flatMap(regionalTotals.runA).map(_.statistics.keySet.size) shouldBe Right(42)
    }

    "parse 6th May format docs" in {
      val book = new XSSFWorkbook(getClass.getResourceAsStream("/06-May-2021.xlsx"))
      create(book).flatMap(regionalTotals.runA).map(_.statistics.keySet.size) shouldBe Right(42)
    }

    "parse 13th May format docs" in {
      val book = new XSSFWorkbook(getClass.getResourceAsStream("/13-May-2021.xlsx"))
      val stats = create(book).flatMap(regionalTotals.runA).map(_.statistics)
      stats.map(_.keySet.size) shouldBe Right(42)

      val ranges = stats.toSeq.flatMap(_.values.flatMap(_.firstDose.keys))
      val secondDoses = stats.toSeq.flatMap(_.values.map(_.secondDose))
      val firstDoses = stats.toSeq.flatMap(_.values.map(_.firstDose))

      Inspectors.forAll(ranges) { range =>
        Inspectors.forAll(firstDoses.zip(secondDoses)) { case (first, second) =>
          first.get(range) shouldNot be(second.get(range))
        }
      }
    }

    "parse 1st July format docs" in {
      val book = new XSSFWorkbook(getClass.getResourceAsStream("/1-July-2021.xlsx"))
      create(book).flatMap(regionalTotals.runA).map(_.statistics.keySet.size) shouldBe Right(42)
    }
  }
}
