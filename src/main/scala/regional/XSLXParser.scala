package io.tvc.vaccines
package regional

import cats.data.StateT
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.{Monad, MonadError}
import org.apache.poi.ss.usermodel.{Cell, CellType}
import org.apache.poi.xssf.usermodel.{XSSFSheet, XSSFWorkbook}

import scala.jdk.CollectionConverters._
import scala.util.Try

/**
 * A StateT based parser for XLSX documents with Apache POI
 * provides basic utilities to move around & extract values
 */
object XSLXParser {

  case class Context(
    workbook: XSSFWorkbook,
    sheet: XSSFSheet,
    cell: Cell
  )

  type OrError[A] = Either[String, A]
  type Op[A]= StateT[OrError, Context, A]

  /**
   * Create the state for the parser to work with -
   * we try to find a sheet them move to the first cell
   */
  def create(wb: XSSFWorkbook): OrError[Context] =
    for {
      sheet <- Option(wb.getSheetAt(wb.getActiveSheetIndex)).toRight("Can't find any sheets")
      row   <- Option(sheet.getRow(sheet.getFirstRowNum)).toRight("Cannot move to first row")
      col   <- Option(row.getCell(row.getFirstCellNum)).toRight("Cannot move to first cell")
    } yield Context(wb, sheet, col)


  def sheet(name: String): Op[Unit] =
    StateT.modifyF { context =>
      Option(context.workbook.getSheet(name))
        .toRight(s"Cannot find sheet named '$name'")
        .map(s => context.copy(sheet = s))
    }

  /**
   * Jump to the first cell containing a particular string value
   * useful for moving to table headers
   */
  def jumpTo(name: String): Op[Unit] =
    StateT.modifyF { context =>
      (
        for {
          row <- context.sheet.rowIterator.asScala
          cell <- row.cellIterator.asScala
          txt <- Try(cell.getStringCellValue).toOption.iterator if txt == name
        } yield context.copy(cell = cell)
      ).nextOption.toRight(s"Cannot jump to cell containing '$name'")
    }

  /**
   * Move by the specify X & Y amounts across the sheet
   * will fail if we can't find the cell to move to
   */
  def move(name: String, x: Int, y: Int): Op[Unit] =
    StateT.modifyF { context =>
      (
        for {
          row <- Option(context.sheet.getRow(context.cell.getRowIndex + y))
          col <- Option(row.getCell(context.cell.getColumnIndex + x))
        } yield context.copy(cell = col)
      ).toRight(s"Could not move $name from ${context.cell.getAddress}")
    }

  /**
   * Move one unit along Y
   */
  def down: Op[Unit] =
    move("down", x = 0, y = 1)

  /**
   * Move one unit along Y,
   * if the cell doesn't exist then try the row below that one
   * until we either find a cell or reach `max`
   */
  def downOrSkip(max: Int): Op[Unit] =
    Monad[Op].tailRecM[Int, OrError[Unit]](1) { num =>
      MonadError[Op, String].attempt(move("down", x = 0, y = num)).map {
        case Left(_) if num < max => Left(num + 1)
        case other => Right(other)
      }
    }.flatMapF(identity)

  /**
   * Move one unit along X
   */
  def right: Op[Unit] =
    move("right", x = 1, y = 0)

  /**
   * Does the cell contain a particular string value?
   * Will fail if the value in the cell isn't a string at all
   */
  def contains(expected: String): Op[Boolean] =
    string.map(_ == expected)

  /**
   * Verify that a cell contains an expected value
   * useful to ensure the structure of the sheet hasn't changed
   */
  def expect(expected: String): Op[Unit] =
    contains(expected).flatMapF { result =>
      Either.cond(result, (), s"expectation of '$expected' failed")
    }

  /**
   * Extract a string value from the cell
   * will fail if the cell contains any other type of data
   */
  def string: Op[String] =
    StateT.inspectF { c =>
      Option(c.cell.getStringCellValue)
        .toRight(s"${c.cell.getAddress} does not contain a string")
    }

  /**
   * Extract a long value from the cell
   * will fail if the cell contains any other type of data
   */
  def long: Op[Long] =
    StateT.inspectF { c =>
      Option(c.cell.getNumericCellValue)
        .flatMap(d => Try(d.toLong).toOption)
        .toRight(s"${c.cell.getAddress} does not contain a long")
    }

  /**
   * Find out if the cell is blank,
   * useful for detecting the ends of datasets
   */
  def isEmpty: Op[Boolean] =
    StateT.inspect(ctx => ctx.cell.getCellType == CellType.BLANK)

  /**
   * Fail the given operation if the cell is empty
   * this guards against accidentally parsing blank strings etc
   */
  def nonEmpty[A](op: Op[A]): Op[A] =
    Monad[Op].ifM(isEmpty)(
      ifTrue = StateT.inspectF(d => Left(s"${d.cell.getAddress} is empty")),
      ifFalse = op
    )

  /**
   * Run the parsing operation for each row (including the one we're on)
   * until we find a row that begins with a blank cell or has no cell. After parsing a row
   * the position of the parser automatically returns to the first cell in the row
   */
  def eachRow[A](parser: StateT[OrError, Context, A]): StateT[OrError, Context, Vector[A]] = {
    Monad[Op].tailRecM[Vector[A], Vector[A]](Vector.empty) { values =>
      Monad[Op].ifM(isEmpty)(
        ifTrue = Monad[Op].pure(Right(values)),
        ifFalse = StateT
          .inspectF(parser.map[Either[Vector[A], Vector[A]]](r => Left(values :+ r)).runA)
          .flatMap(res => MonadError[Op, String].recover(down.as(res)) { case _ => res.swap })
      )
    }
  }

  /**
   * Run the given operation until the condition in `op` returns true
   * useful for moving along cells until we find a particular value
   */
  def until(op: Op[Boolean])(run: Op[Unit]): Op[Unit] =
    Monad[Op].tailRecM(())(_ =>
      Monad[Op].ifM(op)(
        ifTrue = Monad[Op].pure(Right(())),
        ifFalse = run.map(Left(_))
      )
    )

  /**
   * Turn a pre-baked either into a parser op
   * returning it as a constant result - this is for type inference
   */
  def lift[A](a: Either[String, A]): Op[A] =
    StateT.liftF(a)

  /**
   * Run the parsing operation the move right,
   * as if we're consuming values from left to right
   */
  def consumeL[A](p: Op[A]): Op[A] =
    p.flatTap(_ => right)

  def orElse[A](a: Op[A], b: Op[A]): Op[A] =
    MonadError[Op, String].recoverWith(a) { case _ => b }
}
