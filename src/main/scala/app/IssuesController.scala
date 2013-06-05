package app

import service._

class IssuesController extends IssuesControllerBase
  with RepositoryService with AccountService

trait IssuesControllerBase extends ControllerBase { self: RepositoryService =>

  get("/:owner/:repository/issues"){
    issues.html.issues(getRepository(params("owner"), params("repository"), servletContext).get)
  }

  get("/:owner/:repository/issues/:id"){
    issues.html.issue(getRepository(params("owner"), params("repository"), servletContext).get)
  }
  
  
}