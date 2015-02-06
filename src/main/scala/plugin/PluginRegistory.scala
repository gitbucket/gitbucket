package plugin

import java.io.{FilenameFilter, File}
import java.net.URLClassLoader

import org.slf4j.LoggerFactory
import util.Directory._

import scala.collection.mutable.ListBuffer

class PluginRegistry {

  private val plugins = new ListBuffer[PluginInfo]
  private val javaScripts = new ListBuffer[(String, String)]

  def addPlugin(pluginInfo: PluginInfo): Unit = {
    plugins += pluginInfo
  }

  def getPlugins(): List[PluginInfo] = plugins.toList

  def addJavaScript(path: String, script: String): Unit = {
    javaScripts += Tuple2(path, script)
  }

  def getJavaScripts(): List[(String, String)] = javaScripts.toList

  def getJavaScript(currentPath: String): Option[String] = {
    println(currentPath)
    getJavaScripts().find(x => currentPath.matches(x._1)).map(_._2)
  }

}

object PluginRegistry {

  private val logger = LoggerFactory.getLogger(classOf[PluginRegistry])

  private val instance = new PluginRegistry()

  def apply(): PluginRegistry = instance

  def initialize(): Unit = {
    new File(PluginHome).listFiles(new FilenameFilter {
      override def accept(dir: File, name: String): Boolean = name.endsWith(".jar")
    }).foreach { pluginJar =>
      val classLoader = new URLClassLoader(Array(pluginJar.toURI.toURL), Thread.currentThread.getContextClassLoader)
      try {
        val plugin = classLoader.loadClass("Plugin").newInstance().asInstanceOf[Plugin]
        plugin.initialize(instance)
        instance.addPlugin(PluginInfo(
          pluginId    = plugin.pluginId,
          pluginName  = plugin.pluginName,
          version     = plugin.version,
          description = plugin.description,
          pluginClass = plugin
        ))
      } catch {
        case e: Exception => {
          logger.error(s"Error during plugin initialization", e)
        }
      }
    }
  }

  def shutdown(): Unit = {
    instance.getPlugins().foreach { pluginInfo =>
      try {
        pluginInfo.pluginClass.shutdown(instance)
      } catch {
        case e: Exception => {
          logger.error(s"Error during plugin shutdown", e)
        }
      }
    }
  }


}

case class PluginInfo(
  pluginId: String,
  pluginName: String,
  version: String,
  description: String,
  pluginClass: Plugin
)