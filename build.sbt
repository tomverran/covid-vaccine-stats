import sbtassembly.MergeStrategy

name := "vaccine-stats"

version := "0.1"

scalaVersion := "2.13.4"

assembly / assemblyJarName := "vaccine-stats.jar"
resolvers += "Kaluza artifactory" at "https://kaluza.jfrog.io/artifactory/maven"

assembly / assemblyMergeStrategy := {
  case PathList(p @ _*) if p.exists(_.contains("codegen-resources")) => MergeStrategy.discard
  case PathList(p @ _*) if p.last.endsWith("io.netty.versions.properties") => MergeStrategy.discard
  case PathList(p @ _*) if p.last.endsWith("module-info.class") => MergeStrategy.discard
  case PathList(p @ _*) if p.last.endsWith("mime.types") => MergeStrategy.discard
  case x => (assembly / assemblyMergeStrategy).value(x)
}

val awsSdkVersion = "2.15.66"
val http4sVersion = "1.0.0-M23"
val catsEffectVersion = "3.2.1"
val catsVersion = "2.6.1"
val circeVersion = "0.14.0"

libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "org.http4s" %% "http4s-circe" % http4sVersion,
  "org.http4s" %% "http4s-blaze-server" % http4sVersion,
  "org.http4s" %% "http4s-blaze-client" % http4sVersion,

  "org.typelevel" %% "cats-core" % catsVersion,
  "org.typelevel" %% "cats-effect" % catsEffectVersion,

  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-generic-extras" % circeVersion,

  "org.apache.poi" % "poi-ooxml" % "4.1.2",
  "ch.qos.logback" % "logback-classic" % "1.2.3",

  "org.scalatest" %% "scalatest" % "3.2.2" % Test,

  "com.amazonaws" % "aws-lambda-java-core" % "1.2.1",
  "software.amazon.awssdk" % "eventbridge" % awsSdkVersion,
  "software.amazon.awssdk" % "s3" % awsSdkVersion,

  "is.cir" %% "ciris" % "2.0.1",
  "com.ovoenergy" %% "ciris-aws-ssm" % "3.0.1"
)

addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.11.3" cross CrossVersion.full)