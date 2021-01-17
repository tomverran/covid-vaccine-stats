package io.tvc.vaccines

import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}

import java.io.{InputStream, OutputStream}

class Handler extends RequestStreamHandler {
  def handleRequest(input: InputStream, output: OutputStream, context: Context): Unit =
    Main.run(List.empty).unsafeRunSync
}
