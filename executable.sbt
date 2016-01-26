val executableJetty		= "8.1.16.v20140903"
val executableConfig	= config("executable").hide
Keys.ivyConfigurations	+= executableConfig
libraryDependencies		++= Seq(
	"org.eclipse.jetty"	%	"jetty-security"     % executableJetty % "executable",
	"org.eclipse.jetty"	%	"jetty-webapp"       % executableJetty % "executable",
	"org.eclipse.jetty"	%	"jetty-continuation" % executableJetty % "executable",
	"org.eclipse.jetty"	%	"jetty-server"       % executableJetty % "executable",
	"org.eclipse.jetty"	%	"jetty-xml"          % executableJetty % "executable",
	"org.eclipse.jetty"	%	"jetty-http"         % executableJetty % "executable",
	"org.eclipse.jetty"	%	"jetty-servlet"      % executableJetty % "executable",
	"org.eclipse.jetty"	%	"jetty-io"           % executableJetty % "executable",
	"org.eclipse.jetty"	%	"jetty-util"         % executableJetty % "executable"
)

val executableKey	= TaskKey[File]("executable")
executableKey		:= {
	import org.apache.ivy.util.ChecksumHelper
	import java.util.jar.{ Manifest => JarManifest }
	import java.util.jar.Attributes.{ Name => AttrName }
	
	val workDir	= Keys.target.value / "executable"
	val warName	= Keys.name.value + "-" + Keys.version.value + "-executable.war"
	
	val log		= streams.value.log
	log info s"building executable webapp in ${workDir}"
	
	// initialize temp directory
	val temp	= workDir / "webapp"
	IO delete temp
	
	// include jetty classes
	val jettyJars	= Keys.update.value select configurationFilter(name = executableConfig.name)
	jettyJars foreach { jar =>
		IO unzip (jar, temp, (name:String) =>
			(name startsWith "javax/") ||
			(name startsWith "org/")
		)
	}
	
	// include original war file
	val warFile	= (Keys.`package`).value
	IO unzip (warFile, temp)
	
	// include launcher classes
	val classDir		= (Keys.classDirectory in Compile).value
	val launchClasses	= Seq("JettyLauncher.class" /*, "HttpsSupportConnector.class" */)
	launchClasses foreach { name =>
		IO copyFile (classDir / name, temp / name)
	}
	
	// zip it up
	IO delete (temp / "META-INF" / "MANIFEST.MF")
	val contentMappings	= (temp.*** --- PathFinder(temp)).get pair relativeTo(temp)
	val manifest		= new JarManifest
	manifest.getMainAttributes put (AttrName.MANIFEST_VERSION,	"1.0")
	manifest.getMainAttributes put (AttrName.MAIN_CLASS,		"JettyLauncher")
	val outputFile		= workDir / warName
	IO jar (contentMappings, outputFile, manifest)
	
	// generate checksums
	Seq("md5", "sha1") foreach { algorithm =>
		IO.write(
			workDir / (warName + "." + algorithm),
			ChecksumHelper computeAsString (outputFile, algorithm)
		)
	}
	
	// done
	log info s"built executable webapp ${outputFile}"
	outputFile
}
/*
Keys.artifact in (Compile, executableKey) ~= {
	_ copy (`type` = "war", extension = "war"))
}
addArtifact(Keys.artifact in (Compile, executableKey), executableKey)
*/
