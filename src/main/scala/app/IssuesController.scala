package app

import jp.sf.amateras.scalatra.forms._

import service._
import util.{WritableRepositoryAuthenticator, ReadableRepositoryAuthenticator, UsersOnlyAuthenticator}

class IssuesController extends IssuesControllerBase
  with IssuesService with RepositoryService with AccountService with LabelsService
  with UsersOnlyAuthenticator with ReadableRepositoryAuthenticator with WritableRepositoryAuthenticator

trait IssuesControllerBase extends ControllerBase {
  self: IssuesService with RepositoryService with LabelsService
    with UsersOnlyAuthenticator with ReadableRepositoryAuthenticator with WritableRepositoryAuthenticator  =>

  case class IssueForm(title: String, content: Option[String])

  case class MilestoneForm(title: String, description: Option[String], dueDate: Option[java.util.Date])

  val form = mapping(
      "title"   -> trim(label("Title", text(required))),
      "content" -> trim(optional(text()))
    )(IssueForm.apply)

  val milestoneForm = mapping(
    "title"       -> trim(label("Title", text(required, maxlength(100)))),
    "description" -> trim(label("Description", optional(text()))),
    "dueDate"     -> trim(label("Due Date", optional(date())))
  )(MilestoneForm.apply)

  get("/:owner/:repository/issues"){
    val owner = params("owner")
    val repository = params("repository")

    // search condition
    val closed = params.get("state") collect {
      case "closed" => true
    } getOrElse false

    issues.html.issues(searchIssue(owner, repository, closed), getLabels(owner, repository),
      getRepository(owner, repository, baseUrl).get)
  }

  get("/:owner/:repository/issues/:id"){
    val owner = params("owner")
    val repository = params("repository")
    val issueId = params("id")

    getIssue(owner, repository, issueId) map {
      issues.html.issue(_, getRepository(owner, repository, baseUrl).get)
    } getOrElse NotFound
  }

  get("/:owner/:repository/issues/new")( usersOnly {
    issues.html.issueedit(getRepository(params("owner"), params("repository"), baseUrl).get)
  })

  post("/:owner/:repository/issues", form)( usersOnly { form =>
    val owner = params("owner")
    val repository = params("repository")

    redirect("/%s/%s/issues/%d".format(owner, repository,
        saveIssue(owner, repository, context.loginAccount.get.userName, form.title, form.content)))
  })

  post("/:owner/:repository/issue_comments")( usersOnly {
    val owner = params("owner")
    val repository = params("repository")
    val issueId = params("issueId")
    val content = params("content")

    // TODO Returns JSON
    redirect("/%s/%s/issues/%d".format(owner, repository, 1))
  })

}