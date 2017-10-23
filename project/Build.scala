/*
 * Copyright 2017 Iaroslav Zeigerman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import sbt._
import Keys._
import scoverage.ScoverageKeys._
import sbtassembly._
import AssemblyKeys._

object Resolvers {
  val hdpJetty = "HDP Jetty" at "http://repo.hortonworks.com/content/repositories/jetty-hadoop/"
  val hdpMain = "Hortonworks Releases" at "http://repo.hortonworks.com/content/repositories/releases/"
  val mvn = "maven1" at "https://repo1.maven.org/maven2/"
  val mvn2 = "maven2" at "http://central.maven.org/maven2/"
  val hdpResolver = Seq(mvn, mvn2, hdpJetty, hdpMain)
}


object AkkeeperBuild extends Build {
  import Resolvers._
  val AkkaVersion = "2.4.18"
  val AkkaHttpVersion = "10.0.7"
  val CuratorVersion = "2.4.0"
  val SprayJsonVersion = "1.3.3"
  val HadoopVersion = "2.7.3.2.6.0.3-8"
  val ScalaTestVersion = "2.2.6"
  val ScalamockVersion = "3.4.2"
  val Slf4jVersion = "1.7.19"
  val ScoptsVersion = "3.5.0"

  val HadoopDependencies = Seq(
    "org.apache.hadoop" % "hadoop-common" % HadoopVersion,
    "org.apache.hadoop" % "hadoop-hdfs" % HadoopVersion,
    "org.apache.hadoop" % "hadoop-yarn-common" % HadoopVersion,
    "org.apache.hadoop" % "hadoop-yarn-client" % HadoopVersion,
    "org.apache.hadoop" % "hadoop-mapreduce-client-core" % HadoopVersion,
    ("org.apache.curator" % "curator-framework" % CuratorVersion).exclude("org.jboss.netty", "netty"),
    "org.apache.curator" % "curator-test" % CuratorVersion % "test->*"
  ).map(_.exclude("log4j", "log4j"))

  val CommonSettings = Seq(
    organization := "com.github.akkeeper-project",
    scalaVersion := "2.11.11",
    version := "0.1",

    scalacOptions ++= Seq(
      "-unchecked",
      "-deprecation",
      "-feature",
      "-language:postfixOps",
      "-language:implicitConversions",
      "-language:higherKinds"),

    parallelExecution in Test := false,

    libraryDependencies ++= HadoopDependencies ++ Seq(
      "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
      "com.typesafe.akka" %% "akka-cluster" % AkkaVersion,
      "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
      "com.typesafe.akka" %% "akka-http-core" % AkkaHttpVersion,
      "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
      "io.spray" %% "spray-json" % SprayJsonVersion,
      "org.slf4j" % "slf4j-api" % Slf4jVersion,
      "org.slf4j" % "slf4j-log4j12" % Slf4jVersion,
      "com.github.scopt" %% "scopt" % ScoptsVersion,
      "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % "test->*",
      "org.scalatest" %% "scalatest" % ScalaTestVersion % "test->*",
      "org.scalamock" %% "scalamock-scalatest-support" % ScalamockVersion % "test->*"
    ),

    test in assembly := {}
  )

  val AkkeeperSettings = CommonSettings ++ Seq(
    mainClass in Compile := Some("akkeeper.launcher.LauncherMain"),
    resolvers := hdpResolver,
    assemblyMergeStrategy in assembly := {
      case PathList("org", "apache", xs @ _*) => MergeStrategy.first
      case "log4j.properties" => MergeStrategy.concat
      case "reference.conf" => ReferenceMergeStrategy
      case "application.conf" => MergeStrategy.concat
      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    }
  )

  val NoPublishSettings = CommonSettings ++ Seq(
    publishArtifact := false,
    publish := {},
    coverageEnabled := false
  )

  lazy val root = Project(id = "root", base = file("."))
    .settings(NoPublishSettings: _*)
    .aggregate(akkeeper, akkeeperExamples)
    .disablePlugins(sbtassembly.AssemblyPlugin)

  lazy val akkeeper = Project(id = "akkeeper", base = file("akkeeper"))
    .settings(AkkeeperSettings: _*)

  lazy val akkeeperExamples = Project(id = "akkeeper-examples", base = file("akkeeper-examples"))
    .settings(NoPublishSettings: _*)
    .dependsOn(akkeeper)
    .disablePlugins(sbtassembly.AssemblyPlugin)
}
