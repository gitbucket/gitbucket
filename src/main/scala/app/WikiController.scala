package app

import util.{WikiUtil, JGitUtil}

class WikiController extends ControllerBase {

  get("/:owner/:repository/wiki"){
    val owner      = params("owner")
    val repository = params("repository")
    
    html.wiki(WikiUtil.getPage(owner, repository, "Home"), JGitUtil.getRepositoryInfo(owner, repository, servletContext))
  }
  
}