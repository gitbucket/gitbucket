scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

addSbtPlugin("com.geirsson"     % "sbt-scalafmt" % "1.5.0")
addSbtPlugin("com.typesafe.sbt" % "sbt-twirl"    % "1.3.13")
addSbtPlugin("com.eed3si9n"     % "sbt-assembly" % "0.14.5")
//addSbtPlugin("com.earldouglas"      % "xsbt-web-plugin"   % "4.0.0")
//addSbtPlugin("fi.gekkio.sbtplugins" % "sbt-jrebel-plugin" % "0.10.0")
addSbtPlugin("org.scalatra.sbt" % "sbt-scalatra" % "1.0.1")
addSbtPlugin("com.jsuereth"     % "sbt-pgp"      % "1.1.0")
addSbtCoursier
addSbtPlugin("com.typesafe.sbt"       % "sbt-license-report" % "1.2.0")
addSbtPlugin("com.github.stonexx.sbt" % "sbt-webpack"        % "1.3.1")
