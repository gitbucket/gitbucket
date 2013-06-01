package app

import util.JGitUtil

class SettingsController extends ControllerBase {
  
  get("/:owner/:repository/settings") {
    val owner      = params("owner")
    val repository = params("repository")
    redirect("/%s/%s/settings/options".format(owner, repository))
  }
  
  get("/:owner/:repository/settings/options") {
    val owner      = params("owner")
    val repository = params("repository")
    
    settings.html.options(JGitUtil.getRepositoryInfo(owner, repository, servletContext))
  }
  
  get("/:owner/:repository/settings/collaborators") {
    val owner      = params("owner")
    val repository = params("repository")
    
    settings.html.collaborators(JGitUtil.getRepositoryInfo(owner, repository, servletContext))
  }

}