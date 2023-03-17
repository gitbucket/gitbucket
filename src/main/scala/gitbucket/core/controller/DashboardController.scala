package gitbucket.core.controller

import gitbucket.core.dashboard.html
import gitbucket.core.model.Account
import gitbucket.core.service._
import gitbucket.core.util.{Keys, UsersAuthenticator}
import gitbucket.core.util.Implicits._
import gitbucket.core.service.IssuesService._
import gitbucket.core.service.ActivityService._

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
    context.withLoginAccount { loginAccount =>
      val repos = getVisibleRepositories(
        context.loginAccount,
        None,
        withoutPhysicalInfo = true,
        limit = context.settings.basicBehavior.limitVisibleRepositories
      )
      html.repos(getGroupNames(loginAccount.userName), repos, repos, isNewsFeedEnabled())
    }
  })

  get("/dashboard/issues")(usersOnly {
    context.withLoginAccount { loginAccount =>
      searchIssues(loginAccount, "created_by")
    }
  })

  get("/dashboard/issues/assigned")(usersOnly {
    context.withLoginAccount { loginAccount =>
      searchIssues(loginAccount, "assigned")
    }
  })

  get("/dashboard/issues/created_by")(usersOnly {
    context.withLoginAccount { loginAccount =>
      searchIssues(loginAccount, "created_by")
    }
  })

  get("/dashboard/issues/mentioned")(usersOnly {
    context.withLoginAccount { loginAccount =>
      searchIssues(loginAccount, "mentioned")
    }
  })

  get("/dashboard/pulls")(usersOnly {
    context.withLoginAccount { loginAccount =>
      searchPullRequests(loginAccount, "created_by")
    }
  })

  get("/dashboard/pulls/created_by")(usersOnly {
    context.withLoginAccount { loginAccount =>
      searchPullRequests(loginAccount, "created_by")
    }
  })

  get("/dashboard/pulls/assigned")(usersOnly {
    context.withLoginAccount { loginAccount =>
      searchPullRequests(loginAccount, "assigned")
    }
  })

  get("/dashboard/pulls/mentioned")(usersOnly {
    context.withLoginAccount { loginAccount =>
      searchPullRequests(loginAccount, "mentioned")
    }
  })

  private def getOrCreateCondition(key: String, filter: String, userName: String) = {
    val condition = IssueSearchCondition(request)

    filter match {
      case "assigned"  => condition.copy(assigned = Some(Some(userName)), author = None, mentioned = None)
      case "mentioned" => condition.copy(assigned = None, author = None, mentioned = Some(userName))
      case _           => condition.copy(assigned = None, author = Some(userName), mentioned = None)
    }
  }

  private def searchIssues(loginAccount: Account, filter: String) = {
    import IssuesService._

    val userName = loginAccount.userName
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
        limit = context.settings.basicBehavior.limitVisibleRepositories
      ),
      isNewsFeedEnabled()
    )
  }

  private def searchPullRequests(loginAccount: Account, filter: String) = {
    import IssuesService._
    import PullRequestService._

    val userName = loginAccount.userName
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
        limit = context.settings.basicBehavior.limitVisibleRepositories
      ),
      isNewsFeedEnabled()
    )
  }

}
