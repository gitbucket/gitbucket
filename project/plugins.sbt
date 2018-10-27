scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

addSbtPlugin("com.geirsson"     % "sbt-scalafmt" % "1.5.0")
addSbtPlugin("com.typesafe.sbt" % "sbt-twirl"    % "1.3.15")
addSbtPlugin("com.eed3si9n"     % "sbt-assembly" % "0.14.8")
addSbtPlugin("org.scalatra.sbt" % "sbt-scalatra" % "1.0.1")
addSbtPlugin("com.jsuereth"     % "sbt-pgp"      % "1.1.2")
addSbtCoursier
addSbtPlugin("com.typesafe.sbt" % "sbt-license-report" % "1.2.0")
