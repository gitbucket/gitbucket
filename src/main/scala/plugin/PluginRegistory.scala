package plugin

import java.io.{FilenameFilter, File}
import java.net.URLClassLoader
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

import org.slf4j.LoggerFactory
import service.RepositoryService.RepositoryInfo
import util.Directory._
import util.JDBCUtil._

import scala.collection.mutable.ListBuffer

class PluginRegistry {

  private val plugins = new ListBuffer[PluginInfo]
  private val javaScripts = new ListBuffer[(String, String)]
  private val globalActions = new ListBuffer[GlobalAction]
  private val repositoryActions = new ListBuffer[RepositoryAction]

  def addPlugin(pluginInfo: PluginInfo): Unit = {
    plugins += pluginInfo
  }

  def getPlugins(): List[PluginInfo] = plugins.toList

  def addGlobalAction(method: String, path: String)(f: (HttpServletRequest, HttpServletResponse) => Any): Unit = {
    globalActions += GlobalAction(method.toLowerCase, path, f)
  }

  //def getGlobalActions(): List[GlobalAction] = globalActions.toList

  def getGlobalAction(method: String, path: String): Option[(HttpServletRequest, HttpServletResponse) => Any] = {
    globalActions.find { globalAction =>
      globalAction.method == method.toLowerCase && path.matches(globalAction.path)
    }.map(_.function)
  }

  def addRepositoryAction(method: String, path: String)(f: (HttpServletRequest, HttpServletResponse, RepositoryInfo) => Any): Unit = {
    repositoryActions += RepositoryAction(method.toLowerCase, path, f)
  }

  //def getRepositoryActions(): List[RepositoryAction] = repositoryActions.toList

  def getRepositoryAction(method: String, path: String): Option[(HttpServletRequest, HttpServletResponse, RepositoryInfo) => Any] = {
    // TODO
    null
  }

  def addJavaScript(path: String, script: String): Unit = {
    javaScripts += Tuple2(path, script)
  }

  //def getJavaScripts(): List[(String, String)] = javaScripts.toList

  def getJavaScript(currentPath: String): Option[String] = {
    javaScripts.find(x => currentPath.matches(x._1)).map(_._2)
  }

  private case class GlobalAction(
    method: String,
    path: String,
    function: (HttpServletRequest, HttpServletResponse) => Any
  )

  private case class RepositoryAction(
    method: String,
    path: String,
    function: (HttpServletRequest, HttpServletResponse, RepositoryInfo) => Any
  )

}

object PluginRegistry {

  private val logger = LoggerFactory.getLogger(classOf[PluginRegistry])

  private val instance = new PluginRegistry()

  def apply(): PluginRegistry = instance

  def initialize(conn: java.sql.Connection): Unit = {
    val pluginDir = new File(PluginHome)
    if(pluginDir.exists && pluginDir.isDirectory){
      pluginDir.listFiles(new FilenameFilter {
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

          conn.find("SELECT * FROM PLUGIN WHERE PLUGIN_ID = ?", plugin.pluginId)(_.getString("VERSION")) match {
            // Update if the plugin is already registered
            case Some(currentVersion) => conn.update("UPDATE PLUGIN SET VERSION = ? WHERE PLUGIN_ID = ?", plugin.version, plugin.pluginId)
            // Insert if the plugin does not exist
            case None => conn.update("INSERT INTO PLUGIN (PLUGIN_ID, VERSION) VALUES (?, ?)", plugin.pluginId, plugin.version)
          }

          // TODO Migration

        } catch {
          case e: Exception => {
            logger.error(s"Error during plugin initialization", e)
          }
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