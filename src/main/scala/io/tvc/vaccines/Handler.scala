package io.tvc.vaccines

import cats.effect.unsafe.IORuntime
import cats.effect.{ExitCode, IO, IOApp}
import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}

import java.io.{InputStream, OutputStream}

class Handler extends RequestStreamHandler {
  def handleRequest(input: InputStream, output: OutputStream, context: Context): Unit =
    Handler.runDaily.unsafeRunSync()(IORuntime.global)
}

object Handler extends IOApp {

  def runDaily: IO[Unit] =
    App.load[IO].use(App.daily[IO].run(_).value).void

  def run(args: List[String]): IO[ExitCode] =
    App.load[IO].use(App.regional[IO].run(_).value).as(ExitCode.Success)
}
