scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

addSbtPlugin("com.typesafe.sbt"     % "sbt-twirl"         % "1.3.7")
addSbtPlugin("com.eed3si9n"         % "sbt-assembly"      % "0.14.5")
//addSbtPlugin("com.earldouglas"      % "xsbt-web-plugin"   % "4.0.0")
//addSbtPlugin("fi.gekkio.sbtplugins" % "sbt-jrebel-plugin" % "0.10.0")
addSbtPlugin("org.scalatra.sbt"     % "sbt-scalatra"      % "1.0.1")
addSbtPlugin("com.jsuereth"         % "sbt-pgp"           % "1.1.0")
addSbtPlugin("io.get-coursier"      % "sbt-coursier"      % "1.0.0-RC11")
