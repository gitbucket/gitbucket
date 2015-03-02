package gitbucket.core.service

import gitbucket.core.model.Plugin
import gitbucket.core.model.Profile._
import profile.simple._

trait PluginService {

  def getPlugins()(implicit s: Session): List[Plugin] =
    Plugins.sortBy(_.pluginId).list

  def registerPlugin(plugin: Plugin)(implicit s: Session): Unit =
    Plugins.insert(plugin)

  def updatePlugin(plugin: Plugin)(implicit s: Session): Unit =
    Plugins.filter(_.pluginId === plugin.pluginId.bind).map(_.version).update(plugin.version)

  def deletePlugin(pluginId: String)(implicit s: Session): Unit =
    Plugins.filter(_.pluginId === pluginId.bind).delete

  def getPlugin(pluginId: String)(implicit s: Session): Option[Plugin] =
    Plugins.filter(_.pluginId === pluginId.bind).firstOption

}
