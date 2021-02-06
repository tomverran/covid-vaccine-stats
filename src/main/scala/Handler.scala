package io.tvc.vaccines

import cats.effect.{ExitCode, IO, IOApp}
import cats.syntax.parallel._
import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}

import java.io.{InputStream, OutputStream}

class Handler extends RequestStreamHandler {
  def handleRequest(input: InputStream,
                    output: OutputStream,
                    context: Context): Unit =
    Handler.run(List.empty).unsafeRunSync()
}

object Handler extends IOApp {
  def run(args: List[String]): IO[ExitCode] =
    App.load[IO].use { deps =>
      App.regional[IO].run(deps).value.as(ExitCode.Success)
    }
}
