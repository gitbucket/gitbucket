scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

addSbtPlugin("org.scalameta"     % "sbt-scalafmt"       % "2.5.1")
addSbtPlugin("com.typesafe.play" % "sbt-twirl"          % "1.5.2")
addSbtPlugin("com.eed3si9n"      % "sbt-assembly"       % "2.1.1")
addSbtPlugin("org.scalatra.sbt"  % "sbt-scalatra"       % "1.0.4")
addSbtPlugin("com.github.sbt"    % "sbt-pgp"            % "2.2.1")
addSbtPlugin("com.github.sbt"    % "sbt-license-report" % "1.6.1")
addSbtPlugin("org.scoverage"     % "sbt-scoverage"      % "2.0.8")

addDependencyTreePlugin
