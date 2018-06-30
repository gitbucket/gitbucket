import com.typesafe.sbt.license.{DepModuleInfo, LicenseInfo}
import com.typesafe.sbt.pgp.PgpKeys._

val Organization = "io.github.gitbucket"
val Name = "gitbucket"
val GitBucketVersion = "4.26.0"
val ScalatraVersion = "2.6.1"
val JettyVersion = "9.4.7.v20170914"

lazy val root = (project in file("."))
  .enablePlugins(SbtTwirl, ScalatraPlugin, JRebelPlugin)
  .settings(
    )

sourcesInBase := false
organization := Organization
name := Name
version := GitBucketVersion
scalaVersion := "2.12.6"

scalafmtOnCompile := true

// dependency settings
resolvers ++= Seq(
  Classpaths.typesafeReleases,
  Resolver.jcenterRepo,
  "amateras" at "http://amateras.sourceforge.jp/mvn/",
  "sonatype-snapshot" at "https://oss.sonatype.org/content/repositories/snapshots/",
  "amateras-snapshot" at "http://amateras.sourceforge.jp/mvn-snapshot/"
)

libraryDependencies ++= Seq(
  "org.eclipse.jgit"                % "org.eclipse.jgit.http.server" % "5.0.1.201806211838-r",
  "org.eclipse.jgit"                % "org.eclipse.jgit.archive"     % "5.0.1.201806211838-r",
  "org.scalatra"                    %% "scalatra"                    % ScalatraVersion,
  "org.scalatra"                    %% "scalatra-json"               % ScalatraVersion,
  "org.scalatra"                    %% "scalatra-forms"              % ScalatraVersion,
  "org.json4s"                      %% "json4s-jackson"              % "3.5.3",
  "commons-io"                      % "commons-io"                   % "2.6",
  "io.github.gitbucket"             % "solidbase"                    % "1.0.2",
  "io.github.gitbucket"             % "markedj"                      % "1.0.15",
  "org.apache.commons"              % "commons-compress"             % "1.15",
  "org.apache.commons"              % "commons-email"                % "1.5",
  "org.apache.httpcomponents"       % "httpclient"                   % "4.5.4",
  "org.apache.sshd"                 % "apache-sshd"                  % "1.6.0" exclude ("org.slf4j", "slf4j-jdk14"),
  "org.apache.tika"                 % "tika-core"                    % "1.17",
  "com.github.takezoe"              %% "blocking-slick-32"           % "0.0.10",
  "com.novell.ldap"                 % "jldap"                        % "2009-10-07",
  "com.h2database"                  % "h2"                           % "1.4.196",
  "org.mariadb.jdbc"                % "mariadb-java-client"          % "2.2.5",
  "org.postgresql"                  % "postgresql"                   % "42.1.4",
  "ch.qos.logback"                  % "logback-classic"              % "1.2.3",
  "com.zaxxer"                      % "HikariCP"                     % "2.7.4",
  "com.typesafe"                    % "config"                       % "1.3.2",
  "com.typesafe.akka"               %% "akka-actor"                  % "2.5.8",
  "fr.brouillard.oss.security.xhub" % "xhub4j-core"                  % "1.0.0",
  "com.github.bkromhout"            % "java-diff-utils"              % "2.1.1",
  "org.cache2k"                     % "cache2k-all"                  % "1.0.1.Final",
  "com.enragedginger"               %% "akka-quartz-scheduler"       % "1.6.1-akka-2.5.x" exclude ("c3p0", "c3p0"),
  "net.coobird"                     % "thumbnailator"                % "0.4.8",
  "com.github.zafarkhaja"           % "java-semver"                  % "0.9.0",
  "com.nimbusds"                    % "oauth2-oidc-sdk"              % "5.45",
  "org.eclipse.jetty"               % "jetty-webapp"                 % JettyVersion % "provided",
  "javax.servlet"                   % "javax.servlet-api"            % "3.1.0" % "provided",
  "junit"                           % "junit"                        % "4.12" % "test",
  "org.scalatra"                    %% "scalatra-scalatest"          % ScalatraVersion % "test",
  "org.mockito"                     % "mockito-core"                 % "2.13.0" % "test",
  "com.wix"                         % "wix-embedded-mysql"           % "3.0.0" % "test",
  "ru.yandex.qatools.embed"         % "postgresql-embedded"          % "2.6" % "test",
  "net.i2p.crypto"                  % "eddsa"                        % "0.2.0",
  "is.tagomor.woothee"              % "woothee-java"                 % "1.7.0"
)

// Compiler settings
scalacOptions := Seq("-deprecation", "-language:postfixOps", "-opt:l:method", "-Xfuture")
javacOptions in compile ++= Seq("-target", "8", "-source", "8")
javaOptions in Jetty += "-Dlogback.configurationFile=/logback-dev.xml"

// Test settings
//testOptions in Test += Tests.Argument("-l", "ExternalDBTest")
javaOptions in Test += "-Dgitbucket.home=target/gitbucket_home_for_test"
testOptions in Test += Tests.Setup(() => new java.io.File("target/gitbucket_home_for_test").mkdir())
fork in Test := true

// Packaging options
packageOptions += Package.MainClass("JettyLauncher")

// Assembly settings
test in assembly := {}
assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) =>
    (xs map { _.toLowerCase }) match {
      case ("manifest.mf" :: Nil) => MergeStrategy.discard
      case _                      => MergeStrategy.discard
    }
  case x => MergeStrategy.first
}

// JRebel
//Seq(jrebelSettings: _*)

//jrebel.webLinks += (target in webappPrepare).value
//jrebel.enabled := System.getenv().get("JREBEL") != null
javaOptions in Jetty ++= Option(System.getenv().get("JREBEL")).toSeq.flatMap { path =>
  if (path.endsWith(".jar")) {
    // Legacy JRebel agent
    Seq("-noverify", "-XX:+UseConcMarkSweepGC", "-XX:+CMSClassUnloadingEnabled", s"-javaagent:${path}")
  } else {
    // New JRebel agent
    Seq(s"-agentpath:${path}")
  }
}

// Exclude a war file from published artifacts
signedArtifacts := {
  signedArtifacts.value.filterNot {
    case (_, file) => file.getName.endsWith(".war") || file.getName.endsWith(".war.asc")
  }
}

// Create executable war file
val ExecutableConfig = config("executable").hide
Keys.ivyConfigurations += ExecutableConfig
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
  import java.util.jar.Attributes.{Name => AttrName}
  import java.util.jar.{Manifest => JarManifest}

  val workDir = Keys.target.value / "executable"
  val warName = Keys.name.value + ".war"

  val log = streams.value.log
  log info s"building executable webapp in ${workDir}"

  // initialize temp directory
  val temp = workDir / "webapp"
  IO delete temp

  // include jetty classes
  val jettyJars = Keys.update.value select configurationFilter(name = ExecutableConfig.name)
  jettyJars foreach { jar =>
    IO unzip (jar, temp, (name: String) =>
      (name startsWith "javax/") ||
        (name startsWith "org/"))
  }

  // include original war file
  val warFile = (Keys.`package`).value
  IO unzip (warFile, temp)

  // include launcher classes
  val classDir = (Keys.classDirectory in Compile).value
  val launchClasses = Seq("JettyLauncher.class" /*, "HttpsSupportConnector.class" */ )
  launchClasses foreach { name =>
    IO copyFile (classDir / name, temp / name)
  }

  // include plugins
  val pluginsDir = temp / "WEB-INF" / "classes" / "plugins"
  IO createDirectory (pluginsDir)

  val plugins = IO readLines (Keys.baseDirectory.value / "src" / "main" / "resources" / "bundle-plugins.txt")
  plugins.foreach { plugin =>
    plugin.trim.split(":") match {
      case Array(pluginId, pluginVersion) =>
        val url = "https://plugins.gitbucket-community.org/releases/" +
          s"gitbucket-${pluginId}-plugin/gitbucket-${pluginId}-plugin-gitbucket_${version.value}-${pluginVersion}.jar"
        log info s"Download: ${url}"
        IO transfer (new java.net.URL(url).openStream, pluginsDir / url.substring(url.lastIndexOf("/") + 1))
      case _ => ()
    }
  }

  // zip it up
  IO delete (temp / "META-INF" / "MANIFEST.MF")
  val contentMappings = (temp.allPaths --- PathFinder(temp)).get pair { file =>
    IO.relativizeFile(temp, file)
  }
  val manifest = new JarManifest
  manifest.getMainAttributes put (AttrName.MANIFEST_VERSION, "1.0")
  manifest.getMainAttributes put (AttrName.MAIN_CLASS, "JettyLauncher")
  val outputFile = workDir / warName
  IO jar (contentMappings.map { case (file, path) => (file, path.toString) }, outputFile, manifest)

  // generate checksums
  Seq(
    "md5" -> "MD5",
    "sha1" -> "SHA-1",
    "sha256" -> "SHA-256"
  ).foreach {
    case (extension, algorithm) =>
      val checksumFile = workDir / (warName + "." + extension)
      Checksums generate (outputFile, checksumFile, algorithm)
  }

  // done
  log info s"built executable webapp ${outputFile}"
  outputFile
}
publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (version.value.trim.endsWith("SNAPSHOT")) Some("snapshots" at nexus + "content/repositories/snapshots")
  else Some("releases" at nexus + "service/local/staging/deploy/maven2")
}
publishMavenStyle := true
pomIncludeRepository := { _ =>
  false
}
pomExtra := (
  <url>https://github.com/gitbucket/gitbucket</url>
  <licenses>
    <license>
      <name>The Apache Software License, Version 2.0</name>
      <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
    </license>
  </licenses>
  <scm>
    <url>https://github.com/gitbucket/gitbucket</url>
    <connection>scm:git:https://github.com/gitbucket/gitbucket.git</connection>
  </scm>
  <developers>
    <developer>
      <id>takezoe</id>
      <name>Naoki Takezoe</name>
      <url>https://github.com/takezoe</url>
    </developer>
    <developer>
      <id>shimamoto</id>
      <name>Takako Shimamoto</name>
      <url>https://github.com/shimamoto</url>
    </developer>
    <developer>
      <id>tanacasino</id>
      <name>Tomofumi Tanaka</name>
      <url>https://github.com/tanacasino</url>
    </developer>
    <developer>
      <id>mrkm4ntr</id>
      <name>Shintaro Murakami</name>
      <url>https://github.com/mrkm4ntr</url>
    </developer>
    <developer>
      <id>nazoking</id>
      <name>nazoking</name>
      <url>https://github.com/nazoking</url>
    </developer>
    <developer>
      <id>McFoggy</id>
      <name>Matthieu Brouillard</name>
      <url>https://github.com/McFoggy</url>
    </developer>
  </developers>
)

licenseOverrides := {
  case DepModuleInfo("com.github.bkromhout", "java-diff-utils", _) =>
    LicenseInfo(LicenseCategory.Apache, "Apache-2.0", "http://www.apache.org/licenses/LICENSE-2.0")
}
