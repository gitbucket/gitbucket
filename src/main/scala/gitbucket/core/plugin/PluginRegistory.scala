package gitbucket.core.plugin

import java.io.{File, FilenameFilter, InputStream}
import java.net.URLClassLoader
import javax.servlet.ServletContext
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

import gitbucket.core.controller.{Context, ControllerBase}
import gitbucket.core.service.RepositoryService.RepositoryInfo
import gitbucket.core.service.SystemSettingsService.SystemSettings
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
  private val renderers = mutable.Map[String, Renderer]()
  renderers ++= Seq(
    "md" -> MarkdownRenderer, "markdown" -> MarkdownRenderer
  )
  private val repositoryRoutings = new ListBuffer[GitRepositoryRouting]

  def addPlugin(pluginInfo: PluginInfo): Unit = {
    plugins += pluginInfo
  }

  def getPlugins(): List[PluginInfo] = plugins.toList

  def addImage(id: String, bytes: Array[Byte]): Unit = {
    val encoded = StringUtils.newStringUtf8(Base64.encodeBase64(bytes, false))
    images += ((id, encoded))
  }

  @deprecated("Use addImage(id: String, bytes: Array[Byte]) instead", "3.4.0")
  def addImage(id: String, in: InputStream): Unit = {
    val bytes = using(in){ in =>
      val bytes = new Array[Byte](in.available)
      in.read(bytes)
      bytes
    }
    addImage(id, bytes)
  }

  def getImage(id: String): String = images(id)

  def addController(path: String, controller: ControllerBase): Unit = {
    controllers += ((controller, path))
  }

  @deprecated("Use addController(path: String, controller: ControllerBase) instead", "3.4.0")
  def addController(controller: ControllerBase, path: String): Unit = {
    addController(path, controller)
  }

  def getControllers(): Seq[(ControllerBase, String)] = controllers.toSeq

  def addJavaScript(path: String, script: String): Unit = {
    javaScripts += ((path, script))
  }

  def getJavaScript(currentPath: String): List[String] = {
    javaScripts.filter(x => currentPath.matches(x._1)).toList.map(_._2)
  }

  def addRenderer(extension: String, renderer: Renderer): Unit = {
    renderers += ((extension, renderer))
  }

  def getRenderer(extension: String): Renderer = {
    renderers.get(extension).getOrElse(DefaultRenderer)
  }

  def renderableExtensions: Seq[String] = renderers.keys.toSeq

  def addRepositoryRouting(routing: GitRepositoryRouting): Unit = {
    repositoryRoutings += routing
  }

  def getRepositoryRoutings(): Seq[GitRepositoryRouting] = {
    repositoryRoutings.toSeq
  }

  def getRepositoryRouting(repositoryPath: String): Option[GitRepositoryRouting] = {
    PluginRegistry().getRepositoryRoutings().find {
      case GitRepositoryRouting(urlPath, _, _) => {
        repositoryPath.matches("/" + urlPath + "(/.*)?")
      }
    }
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
  def initialize(context: ServletContext, settings: SystemSettings, conn: java.sql.Connection): Unit = {
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
          plugin.initialize(instance, context, settings)
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

  def shutdown(context: ServletContext, settings: SystemSettings): Unit = {
    instance.getPlugins().foreach { pluginInfo =>
      try {
        pluginInfo.pluginClass.shutdown(instance, context, settings)
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
