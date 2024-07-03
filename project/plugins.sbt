scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

addSbtPlugin("org.scalameta"           % "sbt-scalafmt"       % "2.5.2")
addSbtPlugin("org.playframework.twirl" % "sbt-twirl"          % "2.0.7")
addSbtPlugin("com.eed3si9n"            % "sbt-assembly"       % "2.2.0")
addSbtPlugin("org.scalatra.sbt"        % "sbt-scalatra"       % "1.0.4")
addSbtPlugin("com.github.sbt"          % "sbt-pgp"            % "2.2.1")
addSbtPlugin("com.github.sbt"          % "sbt-license-report" % "1.6.1")
addSbtPlugin("org.scoverage"           % "sbt-scoverage"      % "2.1.0")

addDependencyTreePlugin
