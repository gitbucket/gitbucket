package app

import service._
import util.UsersAuthenticator

class DashboardController extends DashboardControllerBase
  with IssuesService with RepositoryService with AccountService
  with UsersAuthenticator

trait DashboardControllerBase extends ControllerBase {
  self: IssuesService with RepositoryService with UsersAuthenticator =>

  get("/dashboard/issues/repos")(usersOnly {
    searchIssues("all")
  })

  get("/dashboard/issues/assigned")(usersOnly {
    searchIssues("assigned")
  })

  get("/dashboard/issues/created_by")(usersOnly {
    searchIssues("created_by")
  })

  private def searchIssues(filter: String) = {
    import IssuesService._

    // condition
    val sessionKey = "dashboard/issues"
    val condition = if(request.getQueryString == null)
      session.get(sessionKey).getOrElse(IssueSearchCondition()).asInstanceOf[IssueSearchCondition]
    else IssueSearchCondition(request)

    session.put(sessionKey, condition)

    val repositories = getUserRepositories(context.loginAccount.get.userName, baseUrl)
    // 
    dashboard.html.issues(
        issues.html.listparts(Nil, 0, 0, 0, condition),
        0,
        0,
        0,
        repositories,
        condition,
        filter)    
    
  }

}