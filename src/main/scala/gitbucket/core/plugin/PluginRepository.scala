package gitbucket.core.plugin

import org.json4s._
import org.apache.commons.io.IOUtils
import org.slf4j.LoggerFactory

object PluginRepository {
  private val logger = LoggerFactory.getLogger(getClass)
  implicit val formats = DefaultFormats

  def parsePluginJson(json: String): Seq[PluginMetadata] = {
    org.json4s.jackson.JsonMethods.parse(json).extract[Seq[PluginMetadata]]
  }

  def getPlugins(): Seq[PluginMetadata] = {
    try {
      val url = new java.net.URL("https://plugins.gitbucket-community.org/releases/plugins.json")
      val str = IOUtils.toString(url, "UTF-8")
      parsePluginJson(str)
    } catch {
      case t: Throwable =>
        logger.warn("Failed to access to the plugin repository: " + t.toString)
        Nil
    }
  }

}

// Mapped from plugins.json
case class PluginMetadata(
  id: String,
  name: String,
  description: String,
  versions: Seq[VersionDef],
  default: Boolean = false
) {
  lazy val latestVersion: VersionDef = versions.last
}

case class VersionDef(
  version: String,
  url: String,
  gitbucketVersion: String
)
