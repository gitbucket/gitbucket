package plugin

import util.Version

trait Plugin {

  val pluginId: String
  val pluginName: String
  val description: String
  val versions: Seq[Version]

  def initialize(registry: PluginRegistry): Unit

  def shutdown(registry: PluginRegistry): Unit

}
