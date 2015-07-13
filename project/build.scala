import sbt._
import Keys._
import org.scalatra.sbt._
import com.typesafe.sbteclipse.plugin.EclipsePlugin.EclipseKeys
import play.twirl.sbt.SbtTwirl
import play.twirl.sbt.Import.TwirlKeys._
import sbtassembly._
import sbtassembly.AssemblyKeys._

object MyBuild extends Build {
  val Organization = "gitbucket"
  val Name = "gitbucket"
  val Version = "3.5.0-SNAPSHOT"
  val ScalaVersion = "2.11.6"
  val ScalatraVersion = "2.3.1"

  lazy val project = Project (
    "gitbucket",
    file(".")
  )
  .settings(ScalatraPlugin.scalatraWithJRebel: _*)
  .settings(
    test in assembly := {},
    assemblyMergeStrategy in assembly := {
      case PathList("META-INF", xs @ _*) =>
        (xs map {_.toLowerCase}) match {
          case ("manifest.mf" :: Nil) => MergeStrategy.discard
          case _ => MergeStrategy.discard
      }
      case x => MergeStrategy.first
    }
  )
  .settings(
    sourcesInBase := false,
    organization := Organization,
    name := Name,
    version := Version,
    scalaVersion := ScalaVersion,
    resolvers ++= Seq(
      Classpaths.typesafeReleases,
      "amateras-repo" at "http://amateras.sourceforge.jp/mvn/"
    ),
    scalacOptions := Seq("-deprecation", "-language:postfixOps"),
    libraryDependencies ++= Seq(
      "org.eclipse.jgit" % "org.eclipse.jgit.http.server" % "3.4.2.201412180340-r",
      "org.eclipse.jgit" % "org.eclipse.jgit.archive" % "3.4.2.201412180340-r",
      "org.scalatra" %% "scalatra" % ScalatraVersion,
      "org.scalatra" %% "scalatra-specs2" % ScalatraVersion % "test",
      "org.scalatra" %% "scalatra-json" % ScalatraVersion,
      "org.json4s" %% "json4s-jackson" % "3.2.11",
      "jp.sf.amateras" %% "scalatra-forms" % "0.1.0",
      "commons-io" % "commons-io" % "2.4",
      "org.pegdown" % "pegdown" % "1.5.0",
      "org.apache.commons" % "commons-compress" % "1.9",
      "org.apache.commons" % "commons-email" % "1.3.3",
      "org.apache.httpcomponents" % "httpclient" % "4.3.6",
      "org.apache.sshd" % "apache-sshd" % "0.11.0",
      "com.typesafe.slick" %% "slick" % "2.1.0",
      "com.novell.ldap" % "jldap" % "2009-10-07",
      "com.h2database" % "h2" % "1.4.180",
//      "ch.qos.logback" % "logback-classic" % "1.0.13" % "runtime",
      "org.eclipse.jetty" % "jetty-webapp" % "8.1.16.v20140903" % "container;provided",
      "org.eclipse.jetty.orbit" % "javax.servlet" % "3.0.0.v201112011016" % "container;provided;test" artifacts Artifact("javax.servlet", "jar", "jar"),
      "junit" % "junit" % "4.12" % "test",
      "com.mchange" % "c3p0" % "0.9.5",
      "com.typesafe" % "config" % "1.2.1",
      "com.typesafe.akka" %% "akka-actor" % "2.3.10",
      "com.enragedginger" %% "akka-quartz-scheduler" % "1.3.0-akka-2.3.x"  exclude("c3p0","c3p0")
    ),
    play.twirl.sbt.Import.TwirlKeys.templateImports += "gitbucket.core._",
    EclipseKeys.withSource := true,
    javacOptions in compile ++= Seq("-target", "7", "-source", "7"),
    testOptions in Test += Tests.Argument(TestFrameworks.Specs2, "junitxml", "console"),
    javaOptions in Test += "-Dgitbucket.home=target/gitbucket_home_for_test",
    testOptions in Test += Tests.Setup( () => new java.io.File("target/gitbucket_home_for_test").mkdir() ),
    fork in Test := true,
    packageOptions += Package.MainClass("JettyLauncher")
  ).enablePlugins(SbtTwirl)
}
