package gitbucket.core.controller

import gitbucket.core.dashboard.html
import gitbucket.core.service._
import gitbucket.core.util.{Keys, UsersAuthenticator}
import gitbucket.core.util.Implicits._
import gitbucket.core.service.IssuesService._

class DashboardController
    extends DashboardControllerBase
    with IssuesService
    with MergeService
    with PullRequestService
    with RepositoryService
    with AccountService
    with ActivityService
    with CommitsService
    with LabelsService
    with PrioritiesService
    with WebHookService
    with WebHookPullRequestService
    with WebHookPullRequestReviewCommentService
    with MilestonesService
    with CommitStatusService
    with UsersAuthenticator
    with RequestCache

trait DashboardControllerBase extends ControllerBase {
  self: IssuesService
    with PullRequestService
    with RepositoryService
    with AccountService
    with CommitStatusService
    with UsersAuthenticator =>

  get("/dashboard/repos")(usersOnly {
    val repos = getVisibleRepositories(
      context.loginAccount,
      None,
      withoutPhysicalInfo = true,
      limit = context.settings.limitVisibleRepositories
    )
    html.repos(getGroupNames(context.loginAccount.get.userName), repos, repos)
  })

  get("/dashboard/issues")(usersOnly {
    searchIssues("created_by")
  })

  get("/dashboard/issues/assigned")(usersOnly {
    searchIssues("assigned")
  })

  get("/dashboard/issues/created_by")(usersOnly {
    searchIssues("created_by")
  })

  get("/dashboard/issues/mentioned")(usersOnly {
    searchIssues("mentioned")
  })

  get("/dashboard/pulls")(usersOnly {
    searchPullRequests("created_by")
  })

  get("/dashboard/pulls/created_by")(usersOnly {
    searchPullRequests("created_by")
  })

  get("/dashboard/pulls/assigned")(usersOnly {
    searchPullRequests("assigned")
  })

  get("/dashboard/pulls/mentioned")(usersOnly {
    searchPullRequests("mentioned")
  })

  private def getOrCreateCondition(key: String, filter: String, userName: String) = {
    val condition = IssueSearchCondition(request)

    filter match {
      case "assigned"  => condition.copy(assigned = Some(Some(userName)), author = None, mentioned = None)
      case "mentioned" => condition.copy(assigned = None, author = None, mentioned = Some(userName))
      case _           => condition.copy(assigned = None, author = Some(userName), mentioned = None)
    }
  }

  private def searchIssues(filter: String) = {
    import IssuesService._

    val userName = context.loginAccount.get.userName
    val condition = getOrCreateCondition(Keys.Session.DashboardIssues, filter, userName)
    val userRepos = getUserRepositories(userName, true).map(repo => repo.owner -> repo.name)
    val page = IssueSearchCondition.page(request)
    val issues = searchIssue(condition, IssueSearchOption.Issues, (page - 1) * IssueLimit, IssueLimit, userRepos: _*)

    html.issues(
      issues.map(issue => (issue, None)),
      page,
      countIssue(condition.copy(state = "open"), IssueSearchOption.Issues, userRepos: _*),
      countIssue(condition.copy(state = "closed"), IssueSearchOption.Issues, userRepos: _*),
      filter match {
        case "assigned"  => condition.copy(assigned = Some(Some(userName)))
        case "mentioned" => condition.copy(mentioned = Some(userName))
        case _           => condition.copy(author = Some(userName))
      },
      filter,
      getGroupNames(userName),
      getVisibleRepositories(
        context.loginAccount,
        None,
        withoutPhysicalInfo = true,
        limit = context.settings.limitVisibleRepositories
      )
    )
  }

  private def searchPullRequests(filter: String) = {
    import IssuesService._
    import PullRequestService._

    val userName = context.loginAccount.get.userName
    val condition = getOrCreateCondition(Keys.Session.DashboardPulls, filter, userName)
    val allRepos = getAllRepositories(userName)
    val page = IssueSearchCondition.page(request)
    val issues = searchIssue(
      condition,
      IssueSearchOption.PullRequests,
      (page - 1) * PullRequestLimit,
      PullRequestLimit,
      allRepos: _*
    )
    val status = issues.map { issue =>
      issue.commitId.flatMap { commitId =>
        getCommitStatusWithSummary(issue.issue.userName, issue.issue.repositoryName, commitId)
      }
    }

    html.pulls(
      issues.zip(status),
      page,
      countIssue(condition.copy(state = "open"), IssueSearchOption.PullRequests, allRepos: _*),
      countIssue(condition.copy(state = "closed"), IssueSearchOption.PullRequests, allRepos: _*),
      filter match {
        case "assigned"  => condition.copy(assigned = Some(Some(userName)))
        case "mentioned" => condition.copy(mentioned = Some(userName))
        case _           => condition.copy(author = Some(userName))
      },
      filter,
      getGroupNames(userName),
      getVisibleRepositories(
        context.loginAccount,
        None,
        withoutPhysicalInfo = true,
        limit = context.settings.limitVisibleRepositories
      )
    )
  }

}
