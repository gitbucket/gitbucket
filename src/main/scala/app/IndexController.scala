package app

import util._
import service._
import jp.sf.amateras.scalatra.forms._

class IndexController extends IndexControllerBase 
  with RepositoryService with AccountService with SystemSettingsService with ActivityService

trait IndexControllerBase extends ControllerBase { self: RepositoryService 
  with SystemSettingsService with ActivityService =>

  get("/"){
    val loginAccount = context.loginAccount

    html.index(getRecentActivities(),
      getAccessibleRepositories(loginAccount, baseUrl),
      loadSystemSettings(),
      loginAccount.map{ account => getRepositoryNamesOfUser(account.userName) }.getOrElse(Nil)
    )
  }

}
