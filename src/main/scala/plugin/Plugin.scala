package plugin

import plugin.PluginSystem.{Action, GlobalMenu, RepositoryMenu}

trait Plugin {
  val id: String
  val author: String
  val url: String
  val description: String

  def repositoryMenus   : List[RepositoryMenu]
  def globalMenus       : List[GlobalMenu]
  def repositoryActions : List[Action]
  def globalActions     : List[Action]
}
