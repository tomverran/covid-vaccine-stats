package io.tvc.vaccines
package regional

import regional.RegionParser._
import regional.XSLXParser._

import cats.instances.either._
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class RegionParserTest extends AnyWordSpec with Matchers {

  "RegionParser" should {

    "parse January format docs" in {
      val book = new XSSFWorkbook(getClass.getResourceAsStream("/28-January-2021.xlsx"))
      create(book).flatMap(regionStatistics.runA).map(_.keySet.size) shouldBe Right(42)
    }

    "parse 4th February format docs" in {
      val book = new XSSFWorkbook(getClass.getResourceAsStream("/4-February-2021.xlsx"))
      create(book).flatMap(regionStatistics.runA).map(_.keySet.size) shouldBe Right(42)
    }

    "parse 11th February format docs" in {
      val book = new XSSFWorkbook(getClass.getResourceAsStream("/11-February-2021.xlsx"))
      create(book).flatMap(regionStatistics.runA).map(_.keySet.size) shouldBe Right(42)
    }

    "parse 18th February format docs" in {
      val book = new XSSFWorkbook(getClass.getResourceAsStream("/18-February-2021.xlsx"))
      create(book).flatMap(regionStatistics.runA).map(_.keySet.size) shouldBe Right(42)
    }

    "parse 25th February format docs" in {
      val book = new XSSFWorkbook(getClass.getResourceAsStream("/25-February-2021.xlsx"))
      create(book).flatMap(regionStatistics.runA).map(_.keySet.size) shouldBe Right(42)
    }
  }
}
