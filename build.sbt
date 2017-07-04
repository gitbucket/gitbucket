val Organization = "io.github.gitbucket"
val Name = "gitbucket"
val GitBucketVersion = "4.14.1"
val ScalatraVersion = "2.5.0"
val JettyVersion = "9.3.19.v20170502"

lazy val root = (project in file(".")).enablePlugins(SbtTwirl, JettyPlugin)

sourcesInBase := false
organization := Organization
name := Name
version := GitBucketVersion
scalaVersion := "2.12.2"

// dependency settings
resolvers ++= Seq(
  Classpaths.typesafeReleases,
  Resolver.jcenterRepo,
  "amateras" at "http://amateras.sourceforge.jp/mvn/",
  "sonatype-snapshot" at "https://oss.sonatype.org/content/repositories/snapshots/",
  "amateras-snapshot" at "http://amateras.sourceforge.jp/mvn-snapshot/"
)
libraryDependencies ++= Seq(
  "org.eclipse.jgit"                %  "org.eclipse.jgit.http.server" % "4.7.0.201704051617-r",
  "org.eclipse.jgit"                %  "org.eclipse.jgit.archive"     % "4.7.0.201704051617-r",
  "org.scalatra"                    %% "scalatra"                     % ScalatraVersion,
  "org.scalatra"                    %% "scalatra-json"                % ScalatraVersion,
  "org.json4s"                      %% "json4s-jackson"               % "3.5.1",
  "io.github.gitbucket"             %% "scalatra-forms"               % "1.1.0",
  "commons-io"                      %  "commons-io"                   % "2.5",
  "io.github.gitbucket"             %  "solidbase"                    % "1.0.2",
  "io.github.gitbucket"             %  "markedj"                      % "1.0.12",
  "org.apache.commons"              %  "commons-compress"             % "1.13",
  "org.apache.commons"              %  "commons-email"                % "1.4",
  "org.apache.httpcomponents"       %  "httpclient"                   % "4.5.3",
  "org.apache.sshd"                 %  "apache-sshd"                  % "1.4.0" exclude("org.slf4j","slf4j-jdk14"),
  "org.apache.tika"                 %  "tika-core"                    % "1.14",
  "com.github.takezoe"              %% "blocking-slick-32"            % "0.0.8",
  "joda-time"                       %  "joda-time"                    % "2.9.9",
  "com.novell.ldap"                 %  "jldap"                        % "2009-10-07",
  "com.h2database"                  %  "h2"                           % "1.4.195",
  "org.mariadb.jdbc"                %  "mariadb-java-client"          % "2.0.3",
  "org.postgresql"                  %  "postgresql"                   % "42.0.0",
  "ch.qos.logback"                  %  "logback-classic"              % "1.2.3",
  "com.zaxxer"                      %  "HikariCP"                     % "2.6.1",
  "com.typesafe"                    %  "config"                       % "1.3.1",
  "com.typesafe.akka"               %% "akka-actor"                   % "2.5.0",
  "fr.brouillard.oss.security.xhub" %  "xhub4j-core"                  % "1.0.0",
  "com.github.bkromhout"            %  "java-diff-utils"              % "2.1.1",
  "org.cache2k"                     %  "cache2k-all"                  % "1.0.0.CR1",
  "com.enragedginger"               %% "akka-quartz-scheduler"        % "1.6.0-akka-2.4.x" exclude("c3p0","c3p0"),
  "net.coobird"                     %  "thumbnailator"                % "0.4.8",
  "org.eclipse.jetty"               %  "jetty-webapp"                 % JettyVersion     % "provided",
  "javax.servlet"                   %  "javax.servlet-api"            % "3.1.0"          % "provided",
  "junit"                           %  "junit"                        % "4.12"           % "test",
  "org.scalatra"                    %% "scalatra-scalatest"           % ScalatraVersion  % "test",
  "org.mockito"                     %  "mockito-core"                 % "2.7.22"         % "test",
  "com.wix"                         %  "wix-embedded-mysql"           % "2.1.4"          % "test",
  "ru.yandex.qatools.embed"         %  "postgresql-embedded"          % "2.0"            % "test"
)

// Compiler settings
scalacOptions := Seq("-deprecation", "-language:postfixOps", "-opt:l:method")
javacOptions in compile ++= Seq("-target", "8", "-source", "8")
javaOptions in Jetty += "-Dlogback.configurationFile=/logback-dev.xml"

// Test settings
//testOptions in Test += Tests.Argument("-l", "ExternalDBTest")
javaOptions in Test += "-Dgitbucket.home=target/gitbucket_home_for_test"
testOptions in Test += Tests.Setup( () => new java.io.File("target/gitbucket_home_for_test").mkdir() )
fork in Test := true

// Packaging options
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
Seq(jrebelSettings: _*)

jrebel.webLinks += (target in webappPrepare).value
jrebel.enabled := System.getenv().get("JREBEL") != null
javaOptions in Jetty ++= Option(System.getenv().get("JREBEL")).toSeq.flatMap { path =>
  Seq("-noverify", "-XX:+UseConcMarkSweepGC", "-XX:+CMSClassUnloadingEnabled", s"-javaagent:${path}")
}

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
publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (version.value.trim.endsWith("SNAPSHOT")) Some("snapshots" at nexus + "content/repositories/snapshots")
  else                             Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}
publishMavenStyle := true
pomIncludeRepository := { _ => false }
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
