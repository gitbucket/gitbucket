package gitbucket.core.controller

import gitbucket.core.admin.plugins.html
import gitbucket.core.plugin.PluginRegistry
import gitbucket.core.util.AdminAuthenticator

class PluginsController extends ControllerBase with AdminAuthenticator {
  get("/admin/plugins")(adminOnly {
    html.plugins(PluginRegistry().getPlugins())
  })
}
