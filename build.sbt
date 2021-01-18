import sbtassembly.MergeStrategy

name := "vaccine-stats"

version := "0.1"

scalaVersion := "2.13.4"

idePackagePrefix := Some("io.tvc.vaccines")

assemblyJarName in assembly := "vaccine-stats.jar"

assemblyMergeStrategy in assembly := {
  case PathList(p @ _*) if p.exists(_.contains("codegen-resources")) => MergeStrategy.discard
  case PathList(p @ _*) if p.last.endsWith("io.netty.versions.properties") => MergeStrategy.discard
  case PathList(p @ _*) if p.last.endsWith("module-info.class") => MergeStrategy.discard
  case PathList(p @ _*) if p.last.endsWith("mime.types") => MergeStrategy.discard
  case x => (assemblyMergeStrategy in assembly).value(x)
}

val awsSdkVersion = "2.15.66"
val http4sVersion = "0.21.15"
val catsEffectVersion = "2.3.1"
val catsVersion = "2.3.1"
val circeVersion = "0.13.0"

libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "org.http4s" %% "http4s-circe" % http4sVersion,
  "org.http4s" %% "http4s-blaze-server" % http4sVersion,
  "org.http4s" %% "http4s-blaze-client" % http4sVersion,

  "org.typelevel" %% "cats-core" % catsVersion,
  "org.typelevel" %% "cats-effect" % catsVersion,

  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,

  "com.amazonaws" % "aws-lambda-java-core" % "1.2.1",
  "org.scala-lang.modules" %% "scala-java8-compat" % "0.9.1",
  "software.amazon.awssdk" % "s3" % awsSdkVersion,
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "is.cir" %% "ciris" % "1.2.1"
)
