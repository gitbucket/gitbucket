package app

import util._
import service._
import jp.sf.amateras.scalatra.forms._

class IndexController extends IndexControllerBase 
  with RepositoryService with SystemSettingsService with ActivityService with AccountService
with UsersAuthenticator

trait IndexControllerBase extends ControllerBase {
  self: RepositoryService with SystemSettingsService with ActivityService with AccountService
  with UsersAuthenticator =>

  get("/"){
    val loginAccount = context.loginAccount

    html.index(getRecentActivities(),
      getVisibleRepositories(loginAccount, baseUrl),
      loadSystemSettings(),
      loginAccount.map{ account => getUserRepositories(account.userName, baseUrl) }.getOrElse(Nil)
    )
  }

  /**
   * JSON API for collaborator completion.
   *
   * TODO Move to other controller?
   */
  get("/_user/proposals")(usersOnly {
    contentType = formats("json")
    org.json4s.jackson.Serialization.write(
      Map("options" -> getAllUsers.filter(!_.isGroupAccount).map(_.userName).toArray)
    )
  })


}
