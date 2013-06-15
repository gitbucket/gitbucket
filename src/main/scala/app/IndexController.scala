package app

import service._

class IndexController extends IndexControllerBase with RepositoryService with AccountService

trait IndexControllerBase extends ControllerBase { self: RepositoryService =>
  
  get("/"){
    html.index(getAccessibleRepositories(context.loginAccount, baseUrl))
  }

}