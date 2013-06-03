package app

import service._

class SettingsController extends SettingsControllerBase with RepositoryService with AccountService


trait SettingsControllerBase extends ControllerBase { self: RepositoryService =>
  
  get("/:owner/:repository/settings") {
    val owner      = params("owner")
    val repository = params("repository")
    redirect("/%s/%s/settings/options".format(owner, repository))
  }
  
  get("/:owner/:repository/settings/options") {
    val owner      = params("owner")
    val repository = params("repository")
    
    settings.html.options(getRepository(owner, repository, servletContext).get)
  }
  
  get("/:owner/:repository/settings/collaborators") {
    val owner      = params("owner")
    val repository = params("repository")
    
    settings.html.collaborators(getRepository(owner, repository, servletContext).get)
  }

}