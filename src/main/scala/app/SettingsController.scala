package app

import util.JGitUtil

class SettingsController extends ControllerBase {
  
  get("/:owner/:repository/settings") {
    val owner      = params("owner")
    val repository = params("repository")
    
    html.settings(JGitUtil.getRepositoryInfo(owner, repository, servletContext))
  }

}