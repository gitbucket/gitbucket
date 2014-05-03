import sbt._
import Keys._
import org.scalatra.sbt._
import twirl.sbt.TwirlPlugin._
import com.typesafe.sbteclipse.plugin.EclipsePlugin.EclipseKeys

object MyBuild extends Build {
  val Organization = "jp.sf.amateras"
  val Name = "gitbucket"
  val Version = "0.0.1"
  val ScalaVersion = "2.10.3"
  val ScalatraVersion = "2.2.1"

  lazy val project = Project (
    "gitbucket",
    file("."),
    settings = Defaults.defaultSettings ++ ScalatraPlugin.scalatraWithJRebel ++ Seq(
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
        "org.eclipse.jgit" % "org.eclipse.jgit.http.server" % "3.0.0.201306101825-r",
        "org.scalatra" %% "scalatra" % ScalatraVersion,
        "org.scalatra" %% "scalatra-specs2" % ScalatraVersion % "test",
        "org.scalatra" %% "scalatra-json" % ScalatraVersion,
        "org.json4s" %% "json4s-jackson" % "3.2.5",
        "jp.sf.amateras" %% "scalatra-forms" % "0.0.14",
        "commons-io" % "commons-io" % "2.4",
        "org.pegdown" % "pegdown" % "1.4.1",
        "org.apache.commons" % "commons-compress" % "1.5",
        "org.apache.commons" % "commons-email" % "1.3.1",
        "org.apache.httpcomponents" % "httpclient" % "4.3",
        "org.apache.sshd" % "apache-sshd" % "0.11.0",
        "com.typesafe.slick" %% "slick" % "1.0.1",
        "com.novell.ldap" % "jldap" % "2009-10-07",
        "com.h2database" % "h2" % "1.3.173",
        "ch.qos.logback" % "logback-classic" % "1.0.13" % "runtime",
        "org.eclipse.jetty" % "jetty-webapp" % "8.1.8.v20121106" % "container;provided",
        "org.eclipse.jetty.orbit" % "javax.servlet" % "3.0.0.v201112011016" % "container;provided;test" artifacts Artifact("javax.servlet", "jar", "jar"),
        "junit" % "junit" % "4.11" % "test"
      ),
      EclipseKeys.withSource := true,
      javacOptions in compile ++= Seq("-target", "6", "-source", "6"),
      testOptions in Test += Tests.Argument(TestFrameworks.Specs2, "junitxml", "console"),
      packageOptions += Package.MainClass("JettyLauncher")
    ) ++ seq(Twirl.settings: _*)
  )
}
