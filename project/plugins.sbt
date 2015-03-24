scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "2.4.0")
addSbtPlugin("com.github.mpeltonen"    % "sbt-idea"          % "1.6.0")
addSbtPlugin("org.scalatra.sbt"        % "scalatra-sbt"      % "0.3.5")
addSbtPlugin("com.typesafe.sbt"        % "sbt-twirl"         % "1.0.4")
addSbtPlugin("com.timushev.sbt"        % "sbt-updates"       % "0.1.8")
addSbtPlugin("com.eed3si9n"            % "sbt-assembly"      % "0.12.0")
