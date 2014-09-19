package plugin

import scala.collection.mutable.ListBuffer
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import app.Context
import plugin.PluginSystem._
import plugin.PluginSystem.RepositoryMenu
import plugin.Security._
import service.RepositoryService.RepositoryInfo
import scala.reflect.runtime.currentMirror
import scala.tools.reflect.ToolBox
import play.twirl.compiler.TwirlCompiler
import scala.io.Codec

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

  def addGlobalAction(method: String, path: String, security: Security = All())(function: (HttpServletRequest, HttpServletResponse, Context) => Any): Unit = {
    globalActionList += Action(method, path, security, function)
  }

  def addRepositoryAction(method: String, path: String, security: Security = All())(function: (HttpServletRequest, HttpServletResponse, Context, RepositoryInfo) => Any): Unit = {
    repositoryActionList += RepositoryAction(method, path, security, function)
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

  def compileTemplate(packageName: String, name: String, source: String): String = {
    val result = TwirlCompiler.parseAndGenerateCodeNewParser(
      Array(packageName, name),
      source.getBytes("UTF-8"),
      Codec(scala.util.Properties.sourceEncoding),
      "",
      "play.twirl.api.HtmlFormat.Appendable",
      "play.twirl.api.HtmlFormat",
      "",
      false)

    result.replaceFirst("package .*", "")
  }
}
