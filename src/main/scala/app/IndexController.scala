package app

import service._

class IndexController extends IndexControllerBase with ProjectService with AccountService

trait IndexControllerBase extends ControllerBase { self: ProjectService =>
  
  get("/"){
    html.index(getAccessibleRepositories(context.loginAccount, servletContext))
  }

}