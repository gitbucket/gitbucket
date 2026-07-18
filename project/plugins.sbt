scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

addSbtPlugin("org.scalameta"           % "sbt-scalafmt"       % "2.6.2")
addSbtPlugin("org.playframework.twirl" % "sbt-twirl"          % "2.0.9")
addSbtPlugin("com.eed3si9n"            % "sbt-assembly"       % "2.3.1")
addSbtPlugin("com.earldouglas"         % "xsbt-web-plugin"    % "4.2.5")
addSbtPlugin("com.github.sbt"          % "sbt-pgp"            % "2.3.1")
addSbtPlugin("com.github.sbt"          % "sbt-license-report" % "1.10.0")
addSbtPlugin("org.scoverage"           % "sbt-scoverage"      % "2.4.4")

addDependencyTreePlugin
