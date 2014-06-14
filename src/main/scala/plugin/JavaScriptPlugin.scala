package plugin

import org.mozilla.javascript.{Context => JsContext}
import org.mozilla.javascript.{Function => JsFunction}
import scala.collection.mutable.ListBuffer
import plugin.PluginSystem.{Action, GlobalMenu, RepositoryMenu}

class JavaScriptPlugin(val id: String, val author: String, val url: String, val description: String) extends Plugin {

  private val repositoryMenuList   = ListBuffer[RepositoryMenu]()
  private val globalMenuList       = ListBuffer[GlobalMenu]()
  private val repositoryActionList = ListBuffer[Action]()
  private val globalActionList     = ListBuffer[Action]()

  def repositoryMenus   : List[RepositoryMenu] = repositoryMenuList.toList
  def globalMenus       : List[GlobalMenu]     = globalMenuList.toList
  def repositoryActions : List[Action]         = repositoryActionList.toList
  def globalActions     : List[Action]         = globalActionList.toList

  def addRepositoryMenu(label: String, name: String, url: String, icon: String, condition: JsFunction): Unit = {
    repositoryMenuList += RepositoryMenu(label, name, url, icon, (context) => {
      val context = JsContext.enter()
      try {
        condition.call(context, condition, condition, Array(context)).asInstanceOf[Boolean]
      } finally {
        JsContext.exit()
      }
    })
  }

  def addGlobalMenu(label: String, url: String, icon: String, condition: JsFunction): Unit = {
    globalMenuList += GlobalMenu(label, url, icon, (context) => {
      val context = JsContext.enter()
      try {
        condition.call(context, condition, condition, Array(context)).asInstanceOf[Boolean]
      } finally {
        JsContext.exit()
      }
    })
  }

  def addGlobalAction(path: String, function: JsFunction): Unit = {
    globalActionList += Action(path, (request, response) => {
      val context = JsContext.enter()
      try {
        function.call(context, function, function, Array(request, response))
      } finally {
        JsContext.exit()
      }
    })
  }

  def addRepositoryAction(path: String, function: JsFunction): Unit = {
    repositoryActionList += Action(path, (request, response) => {
      val context = JsContext.enter()
      try {
        function.call(context, function, function, Array(request, response))
      } finally {
        JsContext.exit()
      }
    })
  }

}

object JavaScriptPlugin {

  def define(id: String, author: String, url: String, description: String) = new JavaScriptPlugin(id, author, url, description)

  def evaluateJavaScript(script: String): Any = {
    val context = JsContext.enter()
    try {
      val scope = context.initStandardObjects()
      scope.put("PluginSystem", scope, PluginSystem)
      scope.put("JavaScriptPlugin", scope, this)
      val result = context.evaluateString(scope, script, "<cmd>", 1, null)
      result
    } finally {
      JsContext.exit
    }
  }

}