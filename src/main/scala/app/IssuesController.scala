package app

import jp.sf.amateras.scalatra.forms._

import service._
import util.UsersOnlyAuthenticator

class IssuesController extends IssuesControllerBase
  with IssuesService with RepositoryService with AccountService with UsersOnlyAuthenticator

trait IssuesControllerBase extends ControllerBase {
  self: IssuesService with RepositoryService with UsersOnlyAuthenticator =>

  case class IssueForm(title: String, content: Option[String])

  val form = mapping(
      "title"   -> trim(label("Title", text(required))),
      "content" -> trim(optional(text()))
    )(IssueForm.apply)

  get("/:owner/:repository/issues"){
    val owner = params("owner")
    val repository = params("repository")

    // search condition
    val closed = params.get("state") collect {
      case "closed" => true
    } getOrElse false

    issues.html.issues(searchIssue(owner, repository, closed),
        getRepository(params("owner"), params("repository"), baseUrl).get)
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

}