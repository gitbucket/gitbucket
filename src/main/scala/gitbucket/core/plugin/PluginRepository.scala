package gitbucket.core.plugin

import org.json4s._
import gitbucket.core.util.Directory._
import org.apache.commons.io.{FileUtils, IOUtils}

object PluginRepository {
  implicit val formats = DefaultFormats

  def parsePluginJson(json: String): Seq[PluginMetadata] = {
    org.json4s.jackson.JsonMethods.parse(json).extract[Seq[PluginMetadata]]
  }

  lazy val LocalRepositoryDir = new java.io.File(PluginHome, ".repository")
  lazy val LocalRepositoryIndexFile = new java.io.File(LocalRepositoryDir, "plugins.json")

  def getPlugins(): Seq[PluginMetadata] = {
    val url = new java.net.URL("https://plugins.gitbucket-community.org/releases/plugins.json")
    val str = IOUtils.toString(url, "UTF-8")
    parsePluginJson(str)
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
