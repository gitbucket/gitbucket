package gitbucket.core.plugin

import gitbucket.core.util.Version

/**
 * Trait for define plugin interface.
 * To provide plugin, put Plugin class which mixed in this trait into the package root.
 */
trait Plugin {

  val pluginId: String
  val pluginName: String
  val description: String
  val versions: Seq[Version]

  /**
   * This method is invoked in initialization of plugin system.
   * Register plugin functionality to PluginRegistry.
   */
  def initialize(registry: PluginRegistry): Unit

  /**
   * This method is invoked in shutdown of plugin system.
   * If the plugin has any resources, release them in this method.
   */
  def shutdown(registry: PluginRegistry): Unit

}
