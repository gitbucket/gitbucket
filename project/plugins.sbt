scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

addSbtPlugin("com.typesafe.sbt"     % "sbt-twirl"         % "1.3.0")
addSbtPlugin("com.eed3si9n"         % "sbt-assembly"      % "0.14.3")
addSbtPlugin("com.earldouglas"      % "xsbt-web-plugin"   % "2.1.1")
addSbtPlugin("fi.gekkio.sbtplugins" % "sbt-jrebel-plugin" % "0.10.0")
addSbtPlugin("com.typesafe.sbt"     % "sbt-pgp"           % "0.8.3")
addSbtPlugin("io.get-coursier"      % "sbt-coursier"      % "1.0.0-M15")
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.8.2")