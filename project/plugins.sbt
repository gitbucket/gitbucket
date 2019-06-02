scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

addSbtPlugin("com.geirsson"     % "sbt-scalafmt"         % "1.5.0")
addSbtPlugin("com.typesafe.sbt" % "sbt-twirl"            % "1.4.1")
addSbtPlugin("com.eed3si9n"     % "sbt-assembly"         % "0.14.9")
addSbtPlugin("org.scalatra.sbt" % "sbt-scalatra"         % "1.0.3")
addSbtPlugin("com.jsuereth"     % "sbt-pgp"              % "1.1.2")
addSbtPlugin("com.typesafe.sbt" % "sbt-license-report"   % "1.2.0")
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.9.2")
addSbtPlugin("org.scoverage"    % "sbt-scoverage"        % "1.5.1")

addSbtCoursier
