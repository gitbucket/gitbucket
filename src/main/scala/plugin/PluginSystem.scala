package plugin

import app.Context
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean
import util.Directory._
import util.ControlUtil._
import org.apache.commons.io.FileUtils
import util.JGitUtil
import org.eclipse.jgit.api.Git
import service.RepositoryService.RepositoryInfo

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
    val javaScriptFile = new java.io.File(pluginDir, id + "/plugin.js")

    if(javaScriptFile.exists && javaScriptFile.isFile){
      val properties = new java.util.Properties()
      using(new java.io.FileInputStream(new java.io.File(pluginDir, id + "/plugin.properties"))){ in =>
        properties.load(in)
      }

      val script = FileUtils.readFileToString(javaScriptFile, "UTF-8")
      try {
        JavaScriptPlugin.evaluateJavaScript(script, Map(
          "id"          -> properties.getProperty("id"),
          "version"     -> properties.getProperty("version"),
          "author"      -> properties.getProperty("author"),
          "url"         -> properties.getProperty("url"),
          "description" -> properties.getProperty("description")
        ))
      } catch {
        case e: Exception => logger.warn(s"Error in plugin loading for ${javaScriptFile.getAbsolutePath}", e)
      }
    }
  }

  def repositoryMenus   : List[RepositoryMenu]   = pluginsMap.values.flatMap(_.repositoryMenus).toList
  def globalMenus       : List[GlobalMenu]       = pluginsMap.values.flatMap(_.globalMenus).toList
  def repositoryActions : List[RepositoryAction] = pluginsMap.values.flatMap(_.repositoryActions).toList
  def globalActions     : List[Action]           = pluginsMap.values.flatMap(_.globalActions).toList

  // Case classes to hold plug-ins information internally in GitBucket
  case class PluginRepository(id: String, url: String)
  case class GlobalMenu(label: String, url: String, icon: String, condition: Context => Boolean)
  case class RepositoryMenu(label: String, name: String, url: String, icon: String, condition: Context => Boolean)
  case class Action(path: String, function: (HttpServletRequest, HttpServletResponse) => Any)
  case class RepositoryAction(path: String, function: (HttpServletRequest, HttpServletResponse, RepositoryInfo) => Any)

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

  // TODO This is a test
//  addGlobalMenu("Google", "http://www.google.co.jp/", "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABgAAAAYCAYAAADgdz34AAAABHNCSVQICAgIfAhkiAAAAAlwSFlzAAAEvwAABL8BkeKJvAAAABl0RVh0U29mdHdhcmUAd3d3Lmlua3NjYXBlLm9yZ5vuPBoAAAIgSURBVEiJtdZNiI1hFAfw36ORhSFFPgYLszOKJAsWRLGzks1gYyFZKFs7C7K2Y2XDRiwmq9kIJWQjJR9Tk48xRtTIRwjH4p473nm99yLNqdNTz/mf//+555x7ektEmEmbNaPs6OkUKKX0YBmWp6/IE8bwIs8xjEfEt0aiiJBl6sEuXMRLfEf8pX/PnIvJ0TPFWxE4+w+Ef/Kzbd5qDx5l8H8tkku7LG17gH7sxWatevdhEUoXsjda5RnDTZzH6jagtMe0lHIa23AJw3iOiSRZlmJ9mfcyfTzFl2AldmI3rkbEkbrAYKrX7S1eVRyWVnxhQ87eiLjQ+o2/mtyve+PuYy3W4+EfsP2/TVGKTHRI+Iz9Fdx8XOmAnZjGWRMYqoF/4ESW4hpOYk1iZ2WsLjDUTeBYBfgeuyux2XiNT5hXud+DD5W8Y90EtifoSfultfjx7MVtrKzcr8No5m7vJtCLx1hQJ8/4IZzClpyoy5ibsYUYQW81Z9o2jYgPeKr15+poEXE9+1XF9WIkOaasaV2P4k4pZUdDbEm+VEQcjIgtEfGxlLIVd/Gs6TX1MhzQquU3HK1t23f4IsuS94fxNXMO/MbXIDBg+tidw5yMbcCmylSdqWEH/kagYLKWeAt9Fcxi3KhhJuXq6SqQBMO15NDalvswmLWux4cbuToIbMS9BpJOfg8bm7imtmmTlVJWaa3hpnU9nufziBjtyDHTny0/AaA7Qnb4AM4aAAAAAElFTkSuQmCC")
//    { context => context.loginAccount.isDefined }
//
//  addRepositoryMenu("Board", "board", "/board", "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABgAAAAYCAYAAADgdz34AAAABHNCSVQICAgIfAhkiAAAAAlwSFlzAAAEvwAABL8BkeKJvAAAABl0RVh0U29mdHdhcmUAd3d3Lmlua3NjYXBlLm9yZ5vuPBoAAAIgSURBVEiJtdZNiI1hFAfw36ORhSFFPgYLszOKJAsWRLGzks1gYyFZKFs7C7K2Y2XDRiwmq9kIJWQjJR9Tk48xRtTIRwjH4p473nm99yLNqdNTz/mf//+555x7ektEmEmbNaPs6OkUKKX0YBmWp6/IE8bwIs8xjEfEt0aiiJBl6sEuXMRLfEf8pX/PnIvJ0TPFWxE4+w+Ef/Kzbd5qDx5l8H8tkku7LG17gH7sxWatevdhEUoXsjda5RnDTZzH6jagtMe0lHIa23AJw3iOiSRZlmJ9mfcyfTzFl2AldmI3rkbEkbrAYKrX7S1eVRyWVnxhQ87eiLjQ+o2/mtyve+PuYy3W4+EfsP2/TVGKTHRI+Iz9Fdx8XOmAnZjGWRMYqoF/4ESW4hpOYk1iZ2WsLjDUTeBYBfgeuyux2XiNT5hXud+DD5W8Y90EtifoSfultfjx7MVtrKzcr8No5m7vJtCLx1hQJ8/4IZzClpyoy5ibsYUYQW81Z9o2jYgPeKr15+poEXE9+1XF9WIkOaasaV2P4k4pZUdDbEm+VEQcjIgtEfGxlLIVd/Gs6TX1MhzQquU3HK1t23f4IsuS94fxNXMO/MbXIDBg+tidw5yMbcCmylSdqWEH/kagYLKWeAt9Fcxi3KhhJuXq6SqQBMO15NDalvswmLWux4cbuToIbMS9BpJOfg8bm7imtmmTlVJWaa3hpnU9nufziBjtyDHTny0/AaA7Qnb4AM4aAAAAAElFTkSuQmCC")
//    { context => true}
//
//  addGlobalAction("/hello"){ (request, response) =>
//    "Hello World!"
//  }

}


