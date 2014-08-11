package plugin

import app.Context
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean
import util.Directory._
import util.ControlUtil._
import org.apache.commons.io.FileUtils
import service.RepositoryService.RepositoryInfo
import Security._


/**
 * Provides extension points to plug-ins.
 */
object PluginSystem {

  private val logger = LoggerFactory.getLogger(PluginSystem.getClass)

  private val initialized = new AtomicBoolean(false)
  private val pluginsMap = scala.collection.mutable.Map[String, Plugin]()
  private val repositoriesList = scala.collection.mutable.ListBuffer[PluginRepository]()

  def install(plugin: Plugin): Unit = {
    pluginsMap.put(plugin.id, plugin)
  }

  def plugins: List[Plugin] = pluginsMap.values.toList

  def uninstall(id: String): Unit = {
    pluginsMap.remove(id)
  }

  def repositories: List[PluginRepository] = repositoriesList.toList

  /**
   * Initializes the plugin system. Load scripts from GITBUCKET_HOME/plugins.
   */
  def init(): Unit = {
    if(initialized.compareAndSet(false, true)){
      // Load installed plugins
      val pluginDir = new java.io.File(PluginHome)
      if(pluginDir.exists && pluginDir.isDirectory){
        pluginDir.listFiles.filter(f => f.isDirectory && !f.getName.startsWith(".")).foreach { dir =>
          installPlugin(dir.getName)
        }
      }
      // Add default plugin repositories
      repositoriesList += PluginRepository("central", "https://github.com/takezoe/gitbucket_plugins.git")
    }
  }

  // TODO Method name seems to not so good.
  def installPlugin(id: String): Unit = {
    val pluginDir = new java.io.File(PluginHome)

    val scalaFile = new java.io.File(pluginDir, id + "/plugin.scala")
    if(scalaFile.exists && scalaFile.isFile){
      val properties = new java.util.Properties()
      using(new java.io.FileInputStream(new java.io.File(pluginDir, id + "/plugin.properties"))){ in =>
        properties.load(in)
      }

      val source = s"""
        |val id          = "${properties.getProperty("id")}"
        |val version     = "${properties.getProperty("version")}"
        |val author      = "${properties.getProperty("author")}"
        |val url         = "${properties.getProperty("url")}"
        |val description = "${properties.getProperty("description")}"
      """.stripMargin + FileUtils.readFileToString(scalaFile, "UTF-8")

      try {
        ScalaPlugin.eval(source)
      } catch {
        case e: Exception => logger.warn(s"Error in plugin loading for ${scalaFile.getAbsolutePath}", e)
      }
    }
  }

  def repositoryMenus       : List[RepositoryMenu]   = pluginsMap.values.flatMap(_.repositoryMenus).toList
  def globalMenus           : List[GlobalMenu]       = pluginsMap.values.flatMap(_.globalMenus).toList
  def repositoryActions     : List[RepositoryAction] = pluginsMap.values.flatMap(_.repositoryActions).toList
  def globalActions         : List[Action]           = pluginsMap.values.flatMap(_.globalActions).toList
  def javaScripts           : List[JavaScript]       = pluginsMap.values.flatMap(_.javaScripts).toList

  // Case classes to hold plug-ins information internally in GitBucket
  case class PluginRepository(id: String, url: String)
  case class GlobalMenu(label: String, url: String, icon: String, condition: Context => Boolean)
  case class RepositoryMenu(label: String, name: String, url: String, icon: String, condition: Context => Boolean)
  case class Action(path: String, security: Security, function: (HttpServletRequest, HttpServletResponse) => Any)
  case class RepositoryAction(path: String, security: Security, function: (HttpServletRequest, HttpServletResponse, RepositoryInfo) => Any)
  case class Button(label: String, href: String)
  case class JavaScript(filter: String => Boolean, script: String)

  /**
   * Checks whether the plugin is updatable.
   */
  def isUpdatable(oldVersion: String, newVersion: String): Boolean = {
    if(oldVersion == newVersion){
      false
    } else {
      val dim1 = oldVersion.split("\\.").map(_.toInt)
      val dim2 = newVersion.split("\\.").map(_.toInt)
      dim1.zip(dim2).foreach { case (a, b) =>
        if(a < b){
          return true
        } else if(a > b){
          return false
        }
      }
      return false
    }
  }

}

