package plugin

import org.mozilla.javascript.{Context => JsContext}
import org.mozilla.javascript.{Function => JsFunction}
import scala.collection.mutable.ListBuffer
import plugin.PluginSystem._
import util.ControlUtil._
import plugin.PluginSystem.GlobalMenu
import plugin.PluginSystem.RepositoryAction
import plugin.PluginSystem.Action
import plugin.PluginSystem.RepositoryMenu

class JavaScriptPlugin(val id: String, val version: String,
                       val author: String, val url: String, val description: String) extends Plugin {

  private val repositoryMenuList   = ListBuffer[RepositoryMenu]()
  private val globalMenuList       = ListBuffer[GlobalMenu]()
  private val repositoryActionList = ListBuffer[RepositoryAction]()
  private val globalActionList     = ListBuffer[Action]()

  def repositoryMenus   : List[RepositoryMenu]   = repositoryMenuList.toList
  def globalMenus       : List[GlobalMenu]       = globalMenuList.toList
  def repositoryActions : List[RepositoryAction] = repositoryActionList.toList
  def globalActions     : List[Action]           = globalActionList.toList

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
    repositoryActionList += RepositoryAction(path, (request, response, repository) => {
      val context = JsContext.enter()
      try {
        function.call(context, function, function, Array(request, response, repository))
      } finally {
        JsContext.exit()
      }
    })
  }

  object db {
    // TODO Use JavaScript Map instead of java.util.Map
    def select(sql: String): Array[java.util.Map[String, String]] = {
      defining(PluginConnectionHolder.threadLocal.get){ conn =>
        using(conn.prepareStatement(sql)){ stmt =>
          using(stmt.executeQuery()){ rs =>
            val list = new java.util.ArrayList[java.util.Map[String, String]]()
            while(rs.next){
              defining(rs.getMetaData){ meta =>
                val map = new java.util.HashMap[String, String]()
                Range(1, meta.getColumnCount).map { i =>
                  val name = meta.getColumnName(i)
                  map.put(name, rs.getString(name))
                }
                list.add(map)
              }
            }
            list.toArray(new Array[java.util.Map[String, String]](list.size))
          }
        }
      }
    }
  }

}

object JavaScriptPlugin {

  def define(id: String, version: String, author: String, url: String, description: String)
    = new JavaScriptPlugin(id, version, author, url, description)

  def evaluateJavaScript(script: String, vars: Map[String, Any] = Map.empty): Any = {
    val context = JsContext.enter()
    try {
      val scope = context.initStandardObjects()
      scope.put("PluginSystem", scope, PluginSystem)
      scope.put("JavaScriptPlugin", scope, this)
      vars.foreach { case (key, value) =>
        scope.put(key, scope, value)
      }
      val result = context.evaluateString(scope, script, "<cmd>", 1, null)
      result
    } finally {
      JsContext.exit
    }
  }

}