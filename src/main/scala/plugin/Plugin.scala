package plugin

trait Plugin {

  val pluginId: String
  val pluginName: String
  val description: String
  val version: String

  def initialize(registry: PluginRegistry): Unit

  def shutdown(registry: PluginRegistry): Unit

}
