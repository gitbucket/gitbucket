package plugin

import app.Context

/**
 * Provides extension points to plug-ins.
 */
object PluginSystem {

  private val repositoryMenuList = scala.collection.mutable.ListBuffer[Menu]()
  private val globalMenuList = scala.collection.mutable.ListBuffer[Menu]()

  case class Menu(label: String, url: String, icon: String, condition: Context => Boolean)

  def addRepositoryMenu(label: String, url: String, icon: String = "")(condition: Context => Boolean): Unit = {
    repositoryMenuList += Menu(label, url, icon, condition)
  }

  def addGlobalMenu(label: String, url: String, icon: String = "")(condition: Context => Boolean): Unit = {
    globalMenuList += Menu(label, url, icon, condition)
  }

  def addAction(path: String): Unit = {
    // TODO
  }

  def repositoryMenus: List[Menu] = repositoryMenuList.toList
  def globalMenus: List[Menu] = globalMenuList.toList

  // TODO This is a test
  addGlobalMenu("Google", "http://www.google.co.jp/"){ context => context.loginAccount.isDefined }

}


