package plugin

import app.Context
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}

/**
 * Provides extension points to plug-ins.
 */
object PluginSystem {

  private val repositoryMenuList = scala.collection.mutable.ListBuffer[Menu]()
  private val globalMenuList = scala.collection.mutable.ListBuffer[Menu]()
  private val actionList = scala.collection.mutable.ListBuffer[Action]()

  case class Menu(label: String, url: String, icon: String, condition: Context => Boolean)
  case class Action(path: String, function: (HttpServletRequest, HttpServletResponse) => Any)

  def addRepositoryMenu(label: String, url: String, icon: String = "")(condition: Context => Boolean): Unit = {
    repositoryMenuList += Menu(label, url, icon, condition)
  }

  def addGlobalMenu(label: String, url: String, icon: String = "")(condition: Context => Boolean): Unit = {
    globalMenuList += Menu(label, url, icon, condition)
  }

  def addAction(path: String)(function: (HttpServletRequest, HttpServletResponse) => Any): Unit = {
    actionList += Action(path, function)
  }

  lazy val repositoryMenus: List[Menu] = repositoryMenuList.toList
  lazy val globalMenus: List[Menu] = globalMenuList.toList
  lazy val actions: List[Action] = actionList.toList

  // TODO This is a test
  addGlobalMenu("Google", "http://www.google.co.jp/"){ context => context.loginAccount.isDefined }

  addAction("/hello"){ (request, response) =>
    "Hello World!"
  }

}


