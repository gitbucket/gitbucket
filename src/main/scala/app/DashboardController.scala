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

    val userName = context.loginAccount.get.userName
    val repositories = getUserRepositories(userName, baseUrl).map(repo => repo.owner -> repo.name)
    val filterUser = Map(filter -> userName)
    val page = IssueSearchCondition.page(request)
    // 
    dashboard.html.issues(
        issues.html.listparts(
            searchIssue(condition, filterUser, false, (page - 1) * IssueLimit, IssueLimit, repositories: _*),
            page,
            countIssue(condition.copy(state = "open"), filterUser, false, repositories: _*),
            countIssue(condition.copy(state = "closed"), filterUser, false, repositories: _*),
            condition),
        countIssue(condition, Map.empty, false, repositories: _*),
        countIssue(condition, Map("assigned" -> userName), false, repositories: _*),
        countIssue(condition, Map("created_by" -> userName), false, repositories: _*),
        countIssueGroupByRepository(condition, filterUser, repositories: _*),
        condition,
        filter)    
    
  }

}
