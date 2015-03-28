package gitbucket.core.plugin

import java.io.{File, FilenameFilter, InputStream}
import java.net.URLClassLoader
import javax.servlet.ServletContext
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

import gitbucket.core.controller.{Context, ControllerBase}
import gitbucket.core.service.RepositoryService.RepositoryInfo
import gitbucket.core.util.ControlUtil._
import gitbucket.core.util.Directory._
import gitbucket.core.util.JDBCUtil._
import gitbucket.core.util.{Version, Versions}
import org.apache.commons.codec.binary.{Base64, StringUtils}
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

class PluginRegistry {

  private val plugins = new ListBuffer[PluginInfo]
  private val javaScripts = new ListBuffer[(String, String)]
  private val controllers = new ListBuffer[(ControllerBase, String)]
  private val images = mutable.Map[String, String]()

  def addPlugin(pluginInfo: PluginInfo): Unit = {
    plugins += pluginInfo
  }

  def getPlugins(): List[PluginInfo] = plugins.toList

  def addImage(id: String, in: InputStream): Unit = {
    val bytes = using(in){ in =>
      val bytes = new Array[Byte](in.available)
      in.read(bytes)
      bytes
    }
    val encoded = StringUtils.newStringUtf8(Base64.encodeBase64(bytes, false))
    images += ((id, encoded))
  }

  def getImage(id: String): String = images(id)

  def addController(controller: ControllerBase, path: String): Unit = {
    controllers += ((controller, path))
  }

  def getControllers(): List[(ControllerBase, String)] = controllers.toList

  def addJavaScript(path: String, script: String): Unit = {
    javaScripts += Tuple2(path, script)
  }

  //def getJavaScripts(): List[(String, String)] = javaScripts.toList

  def getJavaScript(currentPath: String): List[String] = {
    javaScripts.filter(x => currentPath.matches(x._1)).toList.map(_._2)
  }

  private case class GlobalAction(
    method: String,
    path: String,
    function: (HttpServletRequest, HttpServletResponse, Context) => Any
  )

  private case class RepositoryAction(
    method: String,
    path: String,
    function: (HttpServletRequest, HttpServletResponse, Context, RepositoryInfo) => Any
  )

}

/**
 * Provides entry point to PluginRegistry.
 */
object PluginRegistry {

  private val logger = LoggerFactory.getLogger(classOf[PluginRegistry])

  private val instance = new PluginRegistry()

  /**
   * Returns the PluginRegistry singleton instance.
   */
  def apply(): PluginRegistry = instance

  /**
   * Initializes all installed plugins.
   */
  def initialize(context: ServletContext, conn: java.sql.Connection): Unit = {
    val pluginDir = new File(PluginHome)
    if(pluginDir.exists && pluginDir.isDirectory){
      pluginDir.listFiles(new FilenameFilter {
        override def accept(dir: File, name: String): Boolean = name.endsWith(".jar")
      }).foreach { pluginJar =>
        val classLoader = new URLClassLoader(Array(pluginJar.toURI.toURL), Thread.currentThread.getContextClassLoader)
        try {
          val plugin = classLoader.loadClass("Plugin").newInstance().asInstanceOf[Plugin]

          // Migration
          val headVersion = plugin.versions.head
          val currentVersion = conn.find("SELECT * FROM PLUGIN WHERE PLUGIN_ID = ?", plugin.pluginId)(_.getString("VERSION")) match {
            case Some(x) => {
              val dim = x.split("\\.")
              Version(dim(0).toInt, dim(1).toInt)
            }
            case None => Version(0, 0)
          }

          Versions.update(conn, headVersion, currentVersion, plugin.versions, new URLClassLoader(Array(pluginJar.toURI.toURL))){ conn =>
            currentVersion.versionString match {
              case "0.0" =>
                conn.update("INSERT INTO PLUGIN (PLUGIN_ID, VERSION) VALUES (?, ?)", plugin.pluginId, headVersion.versionString)
              case _ =>
                conn.update("UPDATE PLUGIN SET VERSION = ? WHERE PLUGIN_ID = ?", headVersion.versionString, plugin.pluginId)
            }
          }

          // Initialize
          plugin.initialize(instance)
          instance.addPlugin(PluginInfo(
            pluginId    = plugin.pluginId,
            pluginName  = plugin.pluginName,
            version     = plugin.versions.head.versionString,
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
  }

  def shutdown(context: ServletContext): Unit = {
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
