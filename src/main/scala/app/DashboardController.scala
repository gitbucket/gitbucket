package app

import service._
import util.{UsersAuthenticator, Keys}
import util.Implicits._

class DashboardController extends DashboardControllerBase
  with IssuesService with PullRequestService with RepositoryService with AccountService
  with UsersAuthenticator

trait DashboardControllerBase extends ControllerBase {
  self: IssuesService with PullRequestService with RepositoryService with UsersAuthenticator =>

  get("/dashboard/issues/repos")(usersOnly {
    searchIssues("all")
  })

  get("/dashboard/issues/assigned")(usersOnly {
    searchIssues("assigned")
  })

  get("/dashboard/issues/created_by")(usersOnly {
    searchIssues("created_by")
  })

  get("/dashboard/pulls")(usersOnly {
    searchPullRequests("created_by", None)
  })

  get("/dashboard/pulls/owned")(usersOnly {
    searchPullRequests("created_by", None)
  })

  get("/dashboard/pulls/public")(usersOnly {
    searchPullRequests("not_created_by", None)
  })

  get("/dashboard/pulls/for/:owner/:repository")(usersOnly {
    searchPullRequests("all", Some(params("owner") + "/" + params("repository")))
  })

  private def searchIssues(filter: String) = {
    import IssuesService._

    // condition
    val condition = session.putAndGet(Keys.Session.DashboardIssues,
      if(request.hasQueryString) IssueSearchCondition(request)
      else session.getAs[IssueSearchCondition](Keys.Session.DashboardIssues).getOrElse(IssueSearchCondition())
    )

    val userName = context.loginAccount.get.userName
    val repositories = getUserRepositories(userName, context.baseUrl).map(repo => repo.owner -> repo.name)
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
        countIssueGroupByRepository(condition, filterUser, false, repositories: _*),
        condition,
        filter)    
    
  }

  private def searchPullRequests(filter: String, repository: Option[String]) = {
    import IssuesService._
    import PullRequestService._

    // condition
    val condition = session.putAndGet(Keys.Session.DashboardPulls, {
      if(request.hasQueryString) IssueSearchCondition(request)
      else session.getAs[IssueSearchCondition](Keys.Session.DashboardPulls).getOrElse(IssueSearchCondition())
    }.copy(repo = repository))

    val userName = context.loginAccount.get.userName
    val repositories = getUserRepositories(userName, context.baseUrl).map(repo => repo.owner -> repo.name)
    val filterUser = Map(filter -> userName)
    val page = IssueSearchCondition.page(request)

    val counts = countIssueGroupByRepository(
      IssueSearchCondition().copy(state = condition.state), Map.empty, true, repositories: _*)

    dashboard.html.pulls(
      pulls.html.listparts(
        searchIssue(condition, filterUser, true, (page - 1) * PullRequestLimit, PullRequestLimit, repositories: _*),
        page,
        countIssue(condition.copy(state = "open"), filterUser, true, repositories: _*),
        countIssue(condition.copy(state = "closed"), filterUser, true, repositories: _*),
        condition,
        None,
        false),
      getPullRequestCountGroupByUser(condition.state == "closed", userName, None),
      getRepositoryNamesOfUser(userName).map { RepoName =>
        (userName, RepoName, counts.collectFirst { case (_, RepoName, count) => count }.getOrElse(0))
      }.sortBy(_._3).reverse,
      condition,
      filter)

  }


}
