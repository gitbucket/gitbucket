package app

import service._

class IssuesController extends IssuesControllerBase
  with RepositoryService with AccountService

trait IssuesControllerBase extends ControllerBase { self: RepositoryService =>

  get("/:owner/:repository/issues"){
    issues.html.issues(getRepository(params("owner"), params("repository"), baseUrl).get)
  }

  get("/:owner/:repository/issues/:id"){
    issues.html.issue(getRepository(params("owner"), params("repository"), baseUrl).get)
  }

  get("/:owner/:repository/issues/new"){
    issues.html.issueedit(getRepository(params("owner"), params("repository"), baseUrl).get)
  }

  post("/:owner/:repository/issues"){
    redirect("%s/%s/issues".format(params("owner"), params("repository")))
  }

}