package io.tvc.vaccines
package regional

import cats.data.{NonEmptyList, StateT}
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.{Monad, MonadError}
import org.apache.poi.ss.usermodel.{Cell, CellType, Sheet}
import org.apache.poi.xssf.usermodel.{XSSFSheet, XSSFWorkbook}

import scala.jdk.CollectionConverters._
import scala.util.Try

/**
 * A StateT based parser for XLSX documents with Apache POI
 * provides basic utilities to move around & extract values
 */
object XSLXParser {

  type OrError[A] = Either[String, A]
  type Op[A]= StateT[OrError, Cell, A]

  /**
   * Given a sheet return the top left cell
   */
  private def findFirstCell(sheet: Sheet): OrError[Cell] =
    for {
      row   <- Option(sheet.getRow(sheet.getFirstRowNum)).toRight("Cannot move to first row")
      col   <- Option(row.getCell(row.getFirstCellNum)).toRight("Cannot move to first cell")
    } yield col

  /**
   * Create the state for the parser to work with -
   * we try to find a sheet them move to the first cell
   */
  def create(wb: XSSFWorkbook): OrError[Cell] =
    for {
      sheet <- Option(wb.getSheetAt(wb.getActiveSheetIndex)).toRight("Can't find any sheets")
      first <- findFirstCell(sheet)
    } yield first

  /**
   * Run the given operation
   * without moving the current context
   */
  def peek[A](op: Op[A]): Op[A] =
    StateT.inspectF(op.runA)

  /**
   * Fail the parsing operation with a given reason
   */
  def fail[A](why: String): Op[A] =
    StateT.liftF(Left(why))

  val firstCell: Op[Unit] =
    StateT.modifyF(c => findFirstCell(c.getSheet))

  /**
   * Switch to a different sheet in the workbook
   * and move to the top left cell within the sheet
   */
  def sheet(name: String): Op[Unit] =
    StateT.modifyF { context =>
      Option(context.getSheet.getWorkbook.getSheet(name))
        .toRight(s"Cannot find sheet named '$name'")
        .flatMap(findFirstCell)
    }

  /**
   * Jump to the first cell containing a particular string value -
   * we scan from the top left of the sheet moving right and down
   */
  def jumpToFirst(name: String): Op[Unit] =
    StateT.modifyF { context =>
      (
        for {
          row <- context.getSheet.rowIterator.asScala
          cell <- row.cellIterator.asScala
          txt <- Try(cell.getStringCellValue).toOption.iterator if txt == name
        } yield cell
      ).nextOption().toRight(s"Cannot jump to cell containing '$name'")
    }

  /**
   * Starting at the current cell, find the next cell the op succeeds on
   * scanning to the right and then down
   */
  def jumpUntil[A](op: Op[A], name: String): Op[A] =
    StateT { ctx =>
      (
        for {
          row <- ctx.getSheet.rowIterator.asScala
          col <- row.cellIterator.asScala
          res <- op.run(col).toOption.toList
          if col.getColumnIndex >= ctx.getColumnIndex
          if row.getRowNum >= ctx.getRow.getRowNum
        } yield res
      ).nextOption().toRight(s"Cannot jump from ${ctx.getSheet.getSheetName}:${ctx.getAddress} to $name")
    }

  /**
   * Starting at the current cell, find the next cell containing a string value
   * scanning to the right and then down
   */
  def jumpToNext(name: String): Op[Unit] =
    jumpUntil(expect(name), s"cell named $name").void

  /**
   * Move by the specify X & Y amounts across the sheet
   * will fail if we can't find the cell to move to
   */
  def move(name: String, x: Int, y: Int): Op[Unit] =
    StateT.modifyF { context =>
      (
        for {
          row <- Option(context.getSheet.getRow(context.getRowIndex + y))
          col <- Option(row.getCell(context.getColumnIndex + x))
        } yield col
      ).toRight(s"Could not move $name from ${context.getAddress}")
    }

  /**
   * Move one unit along Y
   */
  def down: Op[Unit] =
    move("down", x = 0, y = 1)

  /**
   * Repeat an operation a certain number of times
   * and accumulate the results
   */
  def times[A](count: Int)(op: Op[A]): Op[Vector[A]] =
    Monad[Op].tailRecM(0 -> Vector.empty[A]) {
      case (n, l) if n < count => op.map(r => Left(n + 1 -> (l :+ r)))
      case (n, l) => StateT.pure(Right(l))
    }

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
   * Fail if the given op that returns a boolean fails or
   * if it succeeds and the value it has extracted is false
   */
  def failIfFalse(op: Op[Boolean], name: String): Op[Unit] =
    op.flatMapF(r => Either.cond(r, (), s"op named '$name' returned false"))

  /**
   * Extract a string value from the cell
   * will fail if the cell contains any other type of data
   */
  def string: Op[String] =
    StateT.inspectF { c =>
      Try(Option(c.getStringCellValue)).toOption.flatten
        .toRight(s"${c.getAddress} does not contain a string")
    }

  /**
   * Extract a long value from the cell
   * will fail if the cell contains any other type of data
   */
  def long: Op[Long] =
    StateT.inspectF { c =>
      Try(Option(c.getNumericCellValue)).toOption
        .flatten.flatMap(d => Try(d.toLong).toOption)
        .toRight(s"${c.getAddress} does not contain a long")
    }

  /**
   * Return whether the given op succeeds
   * useful for turning a string/long op into a predicate
   */
  def succeeds[A](op: Op[A]): Op[Boolean] =
    MonadError[Op, String].recover(op.as(true)) { case _ => false }

  def fails[A](op: Op[A]): Op[Boolean] =
    succeeds(op).map(a => !a)

  /**
   * Find out if the cell is blank,
   * useful for detecting the ends of datasets
   */
  def isEmpty: Op[Boolean] =
    StateT.inspect(ctx => ctx.getCellType == CellType.BLANK)

  /**
   * Fail the given operation if the cell is empty
   * this guards against accidentally parsing blank strings etc
   */
  def nonEmpty[A](op: Op[A]): Op[A] =
    Monad[Op].ifM(isEmpty)(
      ifTrue = StateT.inspectF(d => Left(s"${d.getAddress} is empty")),
      ifFalse = op
    )

  /**
   * Print the cell address for debugging
   */
  val whereAmI: Op[Unit] =
    StateT.inspect(s => println(s.getAddress))

  /**
   * Run the parsing operation for each row (including the one we're on)
   * until we find a row that begins with a blank cell or has no cell.
   */
  def eachRow[A](parser: StateT[OrError, Cell, A]): StateT[OrError, Cell, Vector[A]] = {
    Monad[Op].tailRecM[Vector[A], Vector[A]](Vector.empty) { values =>
      Monad[Op].ifM(isEmpty)(
        ifTrue = Monad[Op].pure(Right(values)),
        ifFalse = parser.map[Either[Vector[A], Vector[A]]](r => Left(values :+ r))
          .flatMap(res => MonadError[Op, String].recover(down.as(res)) { case _ => res.swap })
      )
    }
  }

  /**
   * Run the given operation, accumulating results until the condition in `op` returns true
   * useful for moving along cells until we find a particular value
   */
  def until[A](op: Op[Boolean])(run: Op[A]): Op[Vector[A]] =
    Monad[Op].tailRecM(0 -> Vector.empty[A]) { case (count, acc) =>
      Monad[Op].ifM(op)(
        ifTrue = Monad[Op].pure(Right(acc)),
        ifFalse = if (count < 100) {
          run.map(r => Left(count + 1 -> (acc :+ r)))
        } else {
          fail("Until tried >100 times")
        }
      )
    }

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

  /**
   * Try to find a sheet named any of the given names
   */
  def trySheets(names: NonEmptyList[String]): Op[Unit] =
    names.tail.foldLeft(sheet(names.head)) { case (acc, name) => orElse(acc, sheet(name)) }
}
