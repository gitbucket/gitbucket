val Organization = "gitbucket"
val Name = "gitbucket"
val GitBucketVersion = "3.14.0-SNAPSHOT"
val ScalatraVersion = "2.4.0"
val JettyVersion = "9.3.6.v20151106"

lazy val root = (project in file(".")).enablePlugins(SbtTwirl, JettyPlugin)

sourcesInBase := false
organization := Organization
name := Name
version := GitBucketVersion
scalaVersion := "2.11.8"

// dependency settings
resolvers ++= Seq(
  Classpaths.typesafeReleases,
  "sonatype-snapshot" at "https://oss.sonatype.org/content/repositories/snapshots/",
  "amateras-snapshot" at "http://amateras.sourceforge.jp/mvn-snapshot/"
)
libraryDependencies ++= Seq(
  "org.scala-lang.modules"   %% "scala-java8-compat"           % "0.7.0",
  "org.eclipse.jgit"          % "org.eclipse.jgit.http.server" % "4.1.1.201511131810-r",
  "org.eclipse.jgit"          % "org.eclipse.jgit.archive"     % "4.1.1.201511131810-r",
  "org.scalatra"             %% "scalatra"                     % ScalatraVersion,
  "org.scalatra"             %% "scalatra-json"                % ScalatraVersion,
  "org.json4s"               %% "json4s-jackson"               % "3.3.0",
  "io.github.gitbucket"      %% "scalatra-forms"               % "1.0.0",
  "commons-io"                % "commons-io"                   % "2.4",
  "io.github.gitbucket"       % "solidbase"                    % "1.0.0-SNAPSHOT",
  "io.github.gitbucket"       % "markedj"                      % "1.0.7",
  "org.apache.commons"        % "commons-compress"             % "1.10",
  "org.apache.commons"        % "commons-email"                % "1.4",
  "org.apache.httpcomponents" % "httpclient"                   % "4.5.1",
  "org.apache.sshd"           % "apache-sshd"                  % "1.0.0",
  "org.apache.tika"           % "tika-core"                    % "1.11",
  "com.typesafe.slick"       %% "slick"                        % "2.1.0",
  "com.novell.ldap"           % "jldap"                        % "2009-10-07",
  "com.h2database"            % "h2"                           % "1.4.190",
  "ch.qos.logback"            % "logback-classic"              % "1.1.1",
  "com.mchange"               % "c3p0"                         % "0.9.5.2",
  "com.typesafe"              % "config"                       % "1.3.0",
  "com.typesafe.akka"        %% "akka-actor"                   % "2.3.14",
  "fr.brouillard.oss.security.xhub" % "xhub4j-core"            % "1.0.0",
  "com.enragedginger"        %% "akka-quartz-scheduler"        % "1.4.0-akka-2.3.x" exclude("c3p0","c3p0"),
  "org.eclipse.jetty"         % "jetty-webapp"                 % JettyVersion     % "provided",
  "javax.servlet"             % "javax.servlet-api"            % "3.1.0"          % "provided",
  "junit"                     % "junit"                        % "4.12"           % "test",
  "org.scalatra"             %% "scalatra-scalatest"           % ScalatraVersion  % "test",
  "org.scalaz"               %% "scalaz-core"                  % "7.2.0"          % "test"
)

// Twirl settings
play.twirl.sbt.Import.TwirlKeys.templateImports += "gitbucket.core._"

// Compiler settings
scalacOptions := Seq("-deprecation", "-language:postfixOps", "-Ybackend:GenBCode", "-Ydelambdafy:method", "-target:jvm-1.8")
javacOptions in compile ++= Seq("-target", "8", "-source", "8")
javaOptions in Jetty += "-Dlogback.configurationFile=/logback-dev.xml"
testOptions in Test += Tests.Argument(TestFrameworks.Specs2, "junitxml", "console")
javaOptions in Test += "-Dgitbucket.home=target/gitbucket_home_for_test"
testOptions in Test += Tests.Setup( () => new java.io.File("target/gitbucket_home_for_test").mkdir() )
fork in Test := true
packageOptions += Package.MainClass("JettyLauncher")

// Assembly settings
test in assembly := {}
assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) =>
    (xs map {_.toLowerCase}) match {
      case ("manifest.mf" :: Nil) => MergeStrategy.discard
      case _ => MergeStrategy.discard
    }
  case x => MergeStrategy.first
}

// JRebel
jrebel.webLinks += (target in webappPrepare).value
jrebel.enabled := System.getenv().get("JREBEL") != null
javaOptions in Jetty ++= Option(System.getenv().get("JREBEL")).toSeq.flatMap { path =>
  Seq("-noverify", "-XX:+UseConcMarkSweepGC", "-XX:+CMSClassUnloadingEnabled", s"-javaagent:${path}")
}
jrebelSettings

// Create executable war file
val executableConfig = config("executable").hide
Keys.ivyConfigurations += executableConfig
libraryDependencies ++= Seq(
  "org.eclipse.jetty" % "jetty-security"     % JettyVersion % "executable",
  "org.eclipse.jetty" % "jetty-webapp"       % JettyVersion % "executable",
  "org.eclipse.jetty" % "jetty-continuation" % JettyVersion % "executable",
  "org.eclipse.jetty" % "jetty-server"       % JettyVersion % "executable",
  "org.eclipse.jetty" % "jetty-xml"          % JettyVersion % "executable",
  "org.eclipse.jetty" % "jetty-http"         % JettyVersion % "executable",
  "org.eclipse.jetty" % "jetty-servlet"      % JettyVersion % "executable",
  "org.eclipse.jetty" % "jetty-io"           % JettyVersion % "executable",
  "org.eclipse.jetty" % "jetty-util"         % JettyVersion % "executable"
)

val executableKey = TaskKey[File]("executable")
executableKey := {
  import org.apache.ivy.util.ChecksumHelper
  import java.util.jar.{ Manifest => JarManifest }
  import java.util.jar.Attributes.{ Name => AttrName }

  val workDir   = Keys.target.value / "executable"
  val warName   = Keys.name.value + ".war"

  val log       = streams.value.log
  log info s"building executable webapp in ${workDir}"

  // initialize temp directory
  val temp  = workDir / "webapp"
  IO delete temp

  // include jetty classes
  val jettyJars = Keys.update.value select configurationFilter(name = executableConfig.name)
  jettyJars foreach { jar =>
    IO unzip (jar, temp, (name:String) =>
      (name startsWith "javax/") ||
      (name startsWith "org/")
    )
  }

  // include original war file
  val warFile   = (Keys.`package`).value
  IO unzip (warFile, temp)

  // include launcher classes
  val classDir      = (Keys.classDirectory in Compile).value
  val launchClasses = Seq("JettyLauncher.class" /*, "HttpsSupportConnector.class" */)
  launchClasses foreach { name =>
    IO copyFile (classDir / name, temp / name)
  }

  // zip it up
  IO delete (temp / "META-INF" / "MANIFEST.MF")
  val contentMappings   = (temp.*** --- PathFinder(temp)).get pair relativeTo(temp)
  val manifest          = new JarManifest
  manifest.getMainAttributes put (AttrName.MANIFEST_VERSION,    "1.0")
  manifest.getMainAttributes put (AttrName.MAIN_CLASS,          "JettyLauncher")
  val outputFile    = workDir / warName
  IO jar (contentMappings, outputFile, manifest)

  // generate checksums
  Seq(
    "md5"    -> "MD5",
    "sha1"   -> "SHA-1",
    "sha256" -> "SHA-256"
  )
  .foreach { case (extension, algorithm) =>
    val checksumFile = workDir / (warName + "." + extension)
    Checksums generate (outputFile, checksumFile, algorithm)
  }

  // done
  log info s"built executable webapp ${outputFile}"
  outputFile
}
/*
Keys.artifact in (Compile, executableKey) ~= {
    _ copy (`type` = "war", extension = "war"))
}
addArtifact(Keys.artifact in (Compile, executableKey), executableKey)
*/
