package app

import service._

class IndexController extends IndexControllerBase with RepositoryService with AccountService with SystemSettingsService

trait IndexControllerBase extends ControllerBase { self: RepositoryService with SystemSettingsService =>
  
  get("/"){
    html.index(getAccessibleRepositories(context.loginAccount, baseUrl), loadSystemSettings(),
      context.loginAccount.map{ account => getRepositoryNamesOfUser(account.userName) }.getOrElse(Nil))
  }

}