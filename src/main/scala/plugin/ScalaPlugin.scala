package plugin

import app.Context
import scala.collection.mutable.ListBuffer
import plugin.PluginSystem._
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import service.RepositoryService.RepositoryInfo

// TODO This is a sample implementation for Scala based plug-ins.
class ScalaPlugin(val id: String, val version: String,
                  val author: String, val url: String, val description: String) extends Plugin {

  private val repositoryMenuList   = ListBuffer[RepositoryMenu]()
  private val globalMenuList       = ListBuffer[GlobalMenu]()
  private val repositoryActionList = ListBuffer[RepositoryAction]()
  private val globalActionList     = ListBuffer[Action]()

  def repositoryMenus   : List[RepositoryMenu]   = repositoryMenuList.toList
  def globalMenus       : List[GlobalMenu]       = globalMenuList.toList
  def repositoryActions : List[RepositoryAction] = repositoryActionList.toList
  def globalActions     : List[Action]           = globalActionList.toList

  def addRepositoryMenu(label: String, name: String, url: String, icon: String)(condition: (Context) => Boolean): Unit = {
    repositoryMenuList += RepositoryMenu(label, name, url, icon, condition)
  }

  def addGlobalMenu(label: String, url: String, icon: String)(condition: (Context) => Boolean): Unit = {
    globalMenuList += GlobalMenu(label, url, icon, condition)
  }

  def addGlobalAction(path: String)(function: (HttpServletRequest, HttpServletResponse) => Any): Unit = {
    globalActionList += Action(path, function)
  }

  def addRepositoryAction(path: String)(function: (HttpServletRequest, HttpServletResponse, RepositoryInfo) => Any): Unit = {
    repositoryActionList += RepositoryAction(path, function)
  }

}
