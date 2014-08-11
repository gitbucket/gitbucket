package plugin

import scala.collection.mutable.ListBuffer
import scala.collection.mutable.{Map => MutableMap}
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import plugin.PluginSystem._
import app.Context
import plugin.PluginSystem.RepositoryMenu
import service.RepositoryService.RepositoryInfo
import scala.reflect.runtime.currentMirror
import scala.tools.reflect.ToolBox

// TODO This is a sample implementation for Scala based plug-ins.
class ScalaPlugin(val id: String, val version: String,
                  val author: String, val url: String, val description: String) extends Plugin {

  private val repositoryMenuList   = ListBuffer[RepositoryMenu]()
  private val globalMenuList       = ListBuffer[GlobalMenu]()
  private val repositoryActionList = ListBuffer[RepositoryAction]()
  private val globalActionList     = ListBuffer[Action]()
  private val javaScriptList       = ListBuffer[JavaScript]()

  def repositoryMenus       : List[RepositoryMenu]   = repositoryMenuList.toList
  def globalMenus           : List[GlobalMenu]       = globalMenuList.toList
  def repositoryActions     : List[RepositoryAction] = repositoryActionList.toList
  def globalActions         : List[Action]           = globalActionList.toList
  def javaScripts           : List[JavaScript]       = javaScriptList.toList

  def addRepositoryMenu(label: String, name: String, url: String, icon: String)(condition: (Context) => Boolean): Unit = {
    repositoryMenuList += RepositoryMenu(label, name, url, icon, condition)
  }

  def addGlobalMenu(label: String, url: String, icon: String)(condition: (Context) => Boolean): Unit = {
    globalMenuList += GlobalMenu(label, url, icon, condition)
  }

  def addGlobalAction(path: String, security: String = "all")(function: (HttpServletRequest, HttpServletResponse) => Any): Unit = {
    globalActionList += Action(path, security, function)
  }

  def addRepositoryAction(path: String, security: String = "all")(function: (HttpServletRequest, HttpServletResponse, RepositoryInfo) => Any): Unit = {
    repositoryActionList += RepositoryAction(path, security, function)
  }

  def addJavaScript(filter: String => Boolean, script: String): Unit = {
    javaScriptList += JavaScript(filter, script)
  }

}

object ScalaPlugin {

  def define(id: String, version: String, author: String, url: String, description: String)
    = new ScalaPlugin(id, version, author, url, description)

  def eval(source: String): Any = {
    val toolbox = currentMirror.mkToolBox()
    val tree = toolbox.parse(source)
    toolbox.eval(tree)
  }

}
