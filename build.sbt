ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.1.2"

lazy val root = (project in file("."))
  .settings(
    name := "scala-concurrency",
    libraryDependencies ++= Seq(
      "org.scala-lang.modules" %% "scala-parallel-collections" % "1.0.4",
      "io.monix" %% "monix" % "3.4.0"
    )
  )