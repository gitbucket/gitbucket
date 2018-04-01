import com.eclipsesource.json.Json
import scala.collection.JavaConverters._

object PluginsJson {

  def getUrls(json: String): Seq[String] = {
    val value = Json.parse(json)
    value.asArray.values.asScala.map { plugin =>
      val pluginObject = plugin.asObject
      val latestVersionObject = pluginObject.get("versions").asArray.asScala.head.asObject
      latestVersionObject.get("url").asString
    }
  }

}
