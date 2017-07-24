import com.eclipsesource.json.Json
import scala.collection.JavaConverters._

object PluginsJson {

  def parse(json: String): Seq[(String, String)] = {
    val value = Json.parse(json)
    value.asArray.values.asScala.map { plugin =>
      val obj = plugin.asObject.get("versions").asArray.asScala.head.asObject
      val pluginName = obj.get("file").asString.split("_2.12-").head
      val version = obj.get("version").asString
      (pluginName, version)
    }
  }

}

