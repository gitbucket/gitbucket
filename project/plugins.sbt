scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

addSbtPlugin("com.typesafe.sbt"     % "sbt-twirl"         % "1.0.4")
addSbtPlugin("com.eed3si9n"         % "sbt-assembly"      % "0.12.0")
addSbtPlugin("com.earldouglas"      % "xsbt-web-plugin"   % "2.1.0")
addSbtPlugin("fi.gekkio.sbtplugins" % "sbt-jrebel-plugin" % "0.10.0")
addSbtPlugin("com.typesafe.sbt"     % "sbt-pgp"           % "0.8.3")
