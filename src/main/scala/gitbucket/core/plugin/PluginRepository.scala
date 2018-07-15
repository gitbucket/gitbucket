package gitbucket.core.plugin

import java.net.{InetSocketAddress, PasswordAuthentication}
import java.net.{Proxy => JProxy}

import gitbucket.core.controller.Context
import org.json4s._
import org.apache.commons.io.IOUtils
import java.net.Authenticator

object PluginRepository {
  implicit val formats = DefaultFormats

  def parsePluginJson(json: String): Seq[PluginMetadata] = {
    org.json4s.jackson.JsonMethods.parse(json).extract[Seq[PluginMetadata]]
  }

  def getPlugins()(implicit context: Context): Seq[PluginMetadata] = {
    val url = new java.net.URL("https://plugins.gitbucket-community.org/releases/plugins.json")

    val in = context.settings.proxy match {
      case Some(proxy) =>
        (proxy.user, proxy.password) match {
          case (Some(user), Some(password)) =>
            Authenticator.setDefault(new Authenticator() {
              override def getPasswordAuthentication = new PasswordAuthentication(user, password.toCharArray)
            })
          case _ =>
            Authenticator.setDefault(null)
        }

        url
          .openConnection(new JProxy(JProxy.Type.HTTP, new InetSocketAddress(proxy.host, proxy.port)))
          .getInputStream
      case None => url.openStream()
    }

    val str = IOUtils.toString(in, "UTF-8")
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
