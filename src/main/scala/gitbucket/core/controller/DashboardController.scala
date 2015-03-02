package gitbucket.core.controller

import gitbucket.core.dashboard.html
import gitbucket.core.service.{RepositoryService, PullRequestService, AccountService, IssuesService}
import gitbucket.core.util.{StringUtil, Keys, UsersAuthenticator}
import gitbucket.core.util.Implicits._
import gitbucket.core.service.IssuesService._

class DashboardController extends DashboardControllerBase
  with IssuesService with PullRequestService with RepositoryService with AccountService
  with UsersAuthenticator

trait DashboardControllerBase extends ControllerBase {
  self: IssuesService with PullRequestService with RepositoryService with AccountService
    with UsersAuthenticator =>

  get("/dashboard/issues")(usersOnly {
    val q = request.getParameter("q")
    val account = context.loginAccount.get
    Option(q).map { q =>
      val condition = IssueSearchCondition(q, Map[String, Int]())
      q match {
        case q if(q.contains("is:pr")) => redirect(s"/dashboard/pulls?q=${StringUtil.urlEncode(q)}")
        case q if(q.contains(s"author:${account.userName}")) => redirect(s"/dashboard/issues/created_by${condition.toURL}")
        case q if(q.contains(s"assignee:${account.userName}")) => redirect(s"/dashboard/issues/assigned${condition.toURL}")
        case q if(q.contains(s"mentions:${account.userName}")) => redirect(s"/dashboard/issues/mentioned${condition.toURL}")
        case _ => searchIssues("created_by")
      }
    } getOrElse {
      searchIssues("created_by")
    }
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
    val q = request.getParameter("q")
    val account = context.loginAccount.get
    Option(q).map { q =>
      val condition = IssueSearchCondition(q, Map[String, Int]())
      q match {
        case q if(q.contains("is:issue")) => redirect(s"/dashboard/issues?q=${StringUtil.urlEncode(q)}")
        case q if(q.contains(s"author:${account.userName}")) => redirect(s"/dashboard/pulls/created_by${condition.toURL}")
        case q if(q.contains(s"assignee:${account.userName}")) => redirect(s"/dashboard/pulls/assigned${condition.toURL}")
        case q if(q.contains(s"mentions:${account.userName}")) => redirect(s"/dashboard/pulls/mentioned${condition.toURL}")
        case _ => searchPullRequests("created_by")
      }
    } getOrElse {
      searchPullRequests("created_by")
    }
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
    val condition = session.putAndGet(key, if(request.hasQueryString){
      val q = request.getParameter("q")
      if(q == null){
        IssueSearchCondition(request)
      } else {
        IssueSearchCondition(q, Map[String, Int]())
      }
    } else session.getAs[IssueSearchCondition](key).getOrElse(IssueSearchCondition()))

    filter match {
      case "assigned"  => condition.copy(assigned = Some(userName), author = None          , mentioned = None)
      case "mentioned" => condition.copy(assigned = None          , author = None          , mentioned = Some(userName))
      case _           => condition.copy(assigned = None          , author = Some(userName), mentioned = None)
    }
  }

  private def searchIssues(filter: String) = {
    import IssuesService._

    val userName  = context.loginAccount.get.userName
    val condition = getOrCreateCondition(Keys.Session.DashboardIssues, filter, userName)
    val userRepos = getUserRepositories(userName, context.baseUrl, true).map(repo => repo.owner -> repo.name)
    val page      = IssueSearchCondition.page(request)

    html.issues(
      searchIssue(condition, false, (page - 1) * IssueLimit, IssueLimit, userRepos: _*),
      page,
      countIssue(condition.copy(state = "open"  ), false, userRepos: _*),
      countIssue(condition.copy(state = "closed"), false, userRepos: _*),
      filter match {
        case "assigned"  => condition.copy(assigned  = Some(userName))
        case "mentioned" => condition.copy(mentioned = Some(userName))
        case _           => condition.copy(author    = Some(userName))
      },
      filter,
      getGroupNames(userName))
  }

  private def searchPullRequests(filter: String) = {
    import IssuesService._
    import PullRequestService._

    val userName  = context.loginAccount.get.userName
    val condition = getOrCreateCondition(Keys.Session.DashboardPulls, filter, userName)
    val allRepos  = getAllRepositories(userName)
    val page      = IssueSearchCondition.page(request)

    html.pulls(
      searchIssue(condition, true, (page - 1) * PullRequestLimit, PullRequestLimit, allRepos: _*),
      page,
      countIssue(condition.copy(state = "open"  ), true, allRepos: _*),
      countIssue(condition.copy(state = "closed"), true, allRepos: _*),
      filter match {
        case "assigned"  => condition.copy(assigned  = Some(userName))
        case "mentioned" => condition.copy(mentioned = Some(userName))
        case _           => condition.copy(author    = Some(userName))
      },
      filter,
      getGroupNames(userName))
  }


}
