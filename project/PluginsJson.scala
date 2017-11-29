import com.eclipsesource.json.Json
import scala.collection.JavaConverters._

object PluginsJson {

  def parse(json: String): Seq[(String, String, String)] = {
    val value = Json.parse(json)
    value.asArray.values.asScala.map { plugin =>
      val pluginObject = plugin.asObject
      val pluginName = "gitbucket-" + pluginObject.get("id").asString + "-plugin"

      val latestVersionObject = pluginObject.get("versions").asArray.asScala.head.asObject
      val file = latestVersionObject.get("file").asString
      val version = latestVersionObject.get("version").asString

      (pluginName, version, file)
    }
  }

}

