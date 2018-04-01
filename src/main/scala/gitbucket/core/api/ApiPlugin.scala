package gitbucket.core.api

import gitbucket.core.plugin.{PluginRegistry, PluginInfo}

case class ApiPlugin(
  id: String,
  name: String,
  version: String,
  description: String,
  jarFileName: String
)

object ApiPlugin {
  def apply(plugin: PluginInfo): ApiPlugin = {
    ApiPlugin(plugin.pluginId, plugin.pluginName, plugin.pluginVersion, plugin.description, plugin.pluginJar.getName)
  }
}
