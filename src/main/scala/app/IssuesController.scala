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
    issues.html.issues(getRepository(params("owner"), params("repository"), baseUrl).get)
  }

  get("/:owner/:repository/issues/:id"){
    issues.html.issue(getRepository(params("owner"), params("repository"), baseUrl).get)
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