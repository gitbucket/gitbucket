package app

import jp.sf.amateras.scalatra.forms._

import service._
import util.{WritableRepositoryAuthenticator, ReadableRepositoryAuthenticator, UsersOnlyAuthenticator}

class IssuesController extends IssuesControllerBase
  with IssuesService with RepositoryService with AccountService with LabelsService with MilestonesService
  with UsersOnlyAuthenticator with ReadableRepositoryAuthenticator with WritableRepositoryAuthenticator

trait IssuesControllerBase extends ControllerBase {
  self: IssuesService with RepositoryService with LabelsService with MilestonesService
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

    getRepository(owner, repository, baseUrl) match {
      case None    => NotFound()
      case Some(r) => {
        // search condition
        val closed = params.get("state") collect {
          case "closed" => true
        } getOrElse false

        issues.html.issues(searchIssue(owner, repository, closed),
          getLabels(owner, repository),
          getMilestones(owner, repository).filter(_.closedDate.isEmpty),
          r, isWritable(owner, repository, context.loginAccount))
      }
    }
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
    val issueId = params("issueId").toInt
    val content = params("content")	// TODO input check

    contentType = formats("json")
    saveComment(owner, repository, context.loginAccount.get.userName, issueId, content) map {
      model => org.json4s.jackson.Serialization.write(
          Map("commentedUserName" -> model.commentedUserName,
              "registeredDate"    -> view.helpers.datetime(model.registeredDate),
              "content"           -> view.Markdown.toHtml(
                  model.content, getRepository(owner, repository, baseUrl).get, false, true, true)
          ))
    } getOrElse ""
  })

}