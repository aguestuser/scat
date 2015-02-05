name := "scat"

version := "1.0"

scalaVersion := "2.11.5"

// specs2 dependencies:

libraryDependencies ++= Seq(
  "org.specs2" %% "specs2-core" % "2.4.15" % "test"
)

libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic" % "1.1.2"
)

scalacOptions in Test ++= Seq("-Yrangepos")