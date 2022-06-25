scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

addSbtPlugin("org.scalameta"    % "sbt-scalafmt"       % "2.4.6")
addSbtPlugin("com.typesafe.sbt" % "sbt-twirl"          % "1.5.1")
addSbtPlugin("com.eed3si9n"     % "sbt-assembly"       % "1.2.0")
addSbtPlugin("org.scalatra.sbt" % "sbt-scalatra"       % "1.0.4")
addSbtPlugin("com.github.sbt"   % "sbt-pgp"            % "2.1.2")
addSbtPlugin("com.typesafe.sbt" % "sbt-license-report" % "1.2.0")
addSbtPlugin("org.scoverage"    % "sbt-scoverage"      % "2.0.0")

addDependencyTreePlugin
