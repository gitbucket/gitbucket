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

  val form = mapping(
      "title"   -> trim(label("Title", text(required))),
      "content" -> trim(optional(text()))
    )(IssueForm.apply)

  get("/:owner/:repository/issues"){
    val owner      = params("owner")
    val repository = params("repository")
    val condition  = IssueSearchCondition(request)

    println(condition)

    getRepository(owner, repository, baseUrl) match {
      case None => NotFound()
      case Some(repositoryInfo) => {
        // search condition
        val closed = params.get("state") collect {
          case "closed" => true
        } getOrElse false

        issues.html.issues(searchIssue(owner, repository, closed),
          getLabels(owner, repository),
          getMilestones(owner, repository).filter(_.closedDate.isEmpty),
          condition, repositoryInfo, isWritable(owner, repository, context.loginAccount))
      }
    }
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

  // TODO requires users only and redable repository checking
  get("/:owner/:repository/issues/new")( usersOnly {
    issues.html.issueedit(getRepository(params("owner"), params("repository"), baseUrl).get)
  })

  // TODO requires users only and redable repository checking
  post("/:owner/:repository/issues", form)( usersOnly { form =>
    val owner = params("owner")
    val repository = params("repository")

    redirect("/%s/%s/issues/%d".format(owner, repository,
        saveIssue(owner, repository, context.loginAccount.get.userName, form.title, form.content)))
  })

  // TODO requires users only and redable repository checking
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