package plugin

import plugin.PluginSystem._
import java.sql.Connection

trait Plugin {
  val id: String
  val version: String
  val author: String
  val url: String
  val description: String

  def repositoryMenus       : List[RepositoryMenu]
  def globalMenus           : List[GlobalMenu]
  def repositoryActions     : List[RepositoryAction]
  def globalActions         : List[Action]
  def javaScripts           : List[JavaScript]
}

object PluginConnectionHolder {
  val threadLocal = new ThreadLocal[Connection]
}