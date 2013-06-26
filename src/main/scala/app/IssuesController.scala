package app

import jp.sf.amateras.scalatra.forms._

import service._
import IssuesService._
import util.UsersOnlyAuthenticator

class IssuesController extends IssuesControllerBase
  with IssuesService with RepositoryService with AccountService with LabelsService with MilestonesService
  with UsersOnlyAuthenticator

trait IssuesControllerBase extends ControllerBase {
  self: IssuesService with RepositoryService with LabelsService with MilestonesService
    with UsersOnlyAuthenticator =>

  case class IssueForm(title: String, content: Option[String])
  case class CommentForm(issueId: Int, content: String)

  val form = mapping(
      "title"   -> trim(label("Title", text(required))),
      "content" -> trim(optional(text()))
    )(IssueForm.apply)
  val commentForm = mapping(
      "issueId" -> label("Issue Id", number()),
      "content" -> trim(label("Comment", text(required)))
    )(CommentForm.apply)

  get("/:owner/:repository/issues"){
    searchIssues("all")
  }

  get("/:owner/:repository/issues/assigned/:userName"){
    searchIssues("assigned")
  }

  get("/:owner/:repository/issues/created_by/:userName"){
    searchIssues("created_by")
  }

  get("/:owner/:repository/issues/:id"){
    val owner = params("owner")
    val repository = params("repository")
    val issueId = params("id")

    getIssue(owner, repository, issueId) map {
      issues.html.issue(
          _,
          getComment(owner, repository, issueId.toInt),
          getRepository(owner, repository, baseUrl).get)
    } getOrElse NotFound
  }

  // TODO requires users only and readable repository checking
  get("/:owner/:repository/issues/new")( usersOnly {
    issues.html.issueedit(getRepository(params("owner"), params("repository"), baseUrl).get)
  })

  // TODO requires users only and readable repository checking
  post("/:owner/:repository/issues", form)( usersOnly { form =>
    val owner = params("owner")
    val repository = params("repository")

    redirect("/%s/%s/issues/%d".format(owner, repository,
        saveIssue(owner, repository, context.loginAccount.get.userName, form.title, form.content)))
  })

  // TODO requires users only and readable repository checking
  post("/:owner/:repository/issue_comments", commentForm)( usersOnly { form =>
    val owner = params("owner")
    val repository = params("repository")

    redirect("/%s/%s/issues/%d#comment-%d".format(owner, repository, form.issueId,
        saveComment(owner, repository, context.loginAccount.get.userName, form.issueId, form.content)))
  })

  private def searchIssues(filter: String) = {
    val owner      = params("owner")
    val repository = params("repository")
    val userName   = if(filter != "all") Some(params("userName")) else None
    val sessionKey = "%s/%s/issues".format(owner, repository)

    // retrieve search condition
    val condition = if(request.getQueryString == null){
      session.get(sessionKey).getOrElse(IssueSearchCondition()).asInstanceOf[IssueSearchCondition]
    } else IssueSearchCondition(request)

    session.put(sessionKey, condition)

    getRepository(owner, repository, baseUrl).map { repositoryInfo =>
      issues.html.issues(searchIssue(owner, repository, condition, filter, userName),
        getLabels(owner, repository),
        getMilestones(owner, repository).filter(_.closedDate.isEmpty),
        countIssue(owner, repository, condition.copy(state = "open"), filter, userName),
        countIssue(owner, repository, condition.copy(state = "closed"), filter, userName),
        countIssue(owner, repository, condition, "all", None),
        context.loginAccount.map(x => countIssue(owner, repository, condition, "assigned", Some(x.userName))),
        context.loginAccount.map(x => countIssue(owner, repository, condition, "created_by", Some(x.userName))),
        countIssueGroupByLabels(owner, repository, condition, filter, userName),
        condition, filter, repositoryInfo, isWritable(owner, repository, context.loginAccount))

    } getOrElse NotFound
  }

}
