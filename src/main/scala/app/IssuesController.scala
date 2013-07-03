package app

import jp.sf.amateras.scalatra.forms._

import service._
import IssuesService._
import util.{CollaboratorsAuthenticator, ReferrerAuthenticator, ReadableUsersAuthenticator}
import org.scalatra.Ok

class IssuesController extends IssuesControllerBase
  with IssuesService with RepositoryService with AccountService with LabelsService with MilestonesService
  with ReadableUsersAuthenticator with ReferrerAuthenticator with CollaboratorsAuthenticator

trait IssuesControllerBase extends ControllerBase {
  self: IssuesService with RepositoryService with LabelsService with MilestonesService
    with ReadableUsersAuthenticator with ReferrerAuthenticator with CollaboratorsAuthenticator =>

  case class IssueCreateForm(title: String, content: Option[String],
    assignedUserName: Option[String], milestoneId: Option[Int], labelNames: Option[String])

  case class IssueEditForm(title: String, content: Option[String])

  case class CommentForm(issueId: Int, content: String)

  val issueCreateForm = mapping(
      "title"            -> trim(label("Title", text(required))),
      "content"          -> trim(optional(text())),
      "assignedUserName" -> trim(optional(text())),
      "milestoneId"      -> trim(optional(number())),
      "labelNames"       -> trim(optional(text()))
    )(IssueCreateForm.apply)

  val issueEditForm = mapping(
      "title"   -> trim(label("Title", text(required))),
      "content" -> trim(optional(text()))
    )(IssueEditForm.apply)

  val commentForm = mapping(
      "issueId" -> label("Issue Id", number()),
      "content" -> trim(label("Comment", text(required)))
    )(CommentForm.apply)

  get("/:owner/:repository/issues")(referrersOnly {
    searchIssues("all", _)
  })

  get("/:owner/:repository/issues/assigned/:userName")(referrersOnly {
    searchIssues("assigned", _)
  })

  get("/:owner/:repository/issues/created_by/:userName")(referrersOnly {
    searchIssues("created_by", _)
  })

  get("/:owner/:repository/issues/:id")(referrersOnly { repository =>
    val owner   = repository.owner
    val name    = repository.name
    val issueId = params("id")

    getIssue(owner, name, issueId) map {
      issues.html.issue(
          _,
          getComments(owner, name, issueId.toInt),
          getIssueLabels(owner, name, issueId.toInt),
          (getCollaborators(owner, name) :+ owner).sorted,
          getMilestones(owner, name),
          getLabels(owner, name),
          hasWritePermission(owner, name, context.loginAccount),
          repository)
    } getOrElse NotFound
  })

  get("/:owner/:repository/issues/new")(readableUsersOnly { repository =>
    val owner = repository.owner
    val name  = repository.name

    issues.html.create(
        (getCollaborators(owner, name) :+ owner).sorted,
        getMilestones(owner, name),
        getLabels(owner, name),
        hasWritePermission(owner, name, context.loginAccount),
        repository)
  })

  post("/:owner/:repository/issues/new", issueCreateForm)(readableUsersOnly { (form, repository) =>
    val owner    = repository.owner
    val name     = repository.name
    val writable = hasWritePermission(owner, name, context.loginAccount)

    val issueId = createIssue(owner, name, context.loginAccount.get.userName, form.title, form.content,
      if(writable) form.assignedUserName else None,
      if(writable) form.milestoneId else None)

    if(writable){
      form.labelNames.map { value =>
        val labels = getLabels(owner, name)
        value.split(",").foreach { labelName =>
          labels.find(_.labelName == labelName).map { label =>
            registerIssueLabel(owner, name, issueId, label.labelId)
          }
        }
      }
    }

    redirect("/%s/%s/issues/%d".format(owner, name, issueId))
  })

  ajaxPost("/:owner/:repository/issues/edit/:id", issueEditForm)(readableUsersOnly { (form, repository) =>
    val owner = repository.owner
    val name  = repository.name

    getIssue(owner, name, params("id")).map { issue =>
      if(hasWritePermission(owner, name, context.loginAccount) ||
          issue.openedUserName == context.loginAccount.get.userName){
        updateIssue(owner, name, issue.issueId, form.title, form.content)
        redirect("/%s/%s/issues/_data/%d".format(owner, name, issue.issueId))
      } else Unauthorized
    } getOrElse NotFound
  })

  post("/:owner/:repository/issue_comments/new", commentForm)(readableUsersOnly { (form, repository) =>
    val owner = repository.owner
    val name  = repository.name

    redirect("/%s/%s/issues/%d#comment-%d".format(
        owner, name, form.issueId,
        createComment(owner, name, context.loginAccount.get.userName,
            form.issueId,
            form.content,
            params.get("action") filter { action =>
              updateClosed(owner, name, form.issueId, if(action == "close") true else false) > 0
            })
    ))
  })

  ajaxPost("/:owner/:repository/issue_comments/edit/:id", commentForm)(readableUsersOnly { (form, repository) =>
    val owner = repository.owner
    val name  = repository.name

    getComment(owner, name, params("id")).map { comment =>
      if(hasWritePermission(owner, name, context.loginAccount) ||
          comment.commentedUserName == context.loginAccount.get.userName){
        updateComment(comment.commentId, form.content)
        redirect("/%s/%s/issue_comments/_data/%d".format(owner, name, comment.commentId))
      } else Unauthorized
    } getOrElse NotFound
  })

  ajaxGet("/:owner/:repository/issues/_data/:id")(readableUsersOnly { repository =>
    getIssue(repository.owner, repository.name, params("id")) map { x =>
      if(hasWritePermission(x.userName, x.repositoryName, context.loginAccount) ||
          x.openedUserName == context.loginAccount.get.userName){
        params.get("dataType") collect {
          case t if t == "html" => issues.html.editissue(
              x.title, x.content, x.issueId, x.userName, x.repositoryName)
        } getOrElse {
          contentType = formats("json")
          org.json4s.jackson.Serialization.write(
              Map("title"   -> x.title,
                  "content" -> view.Markdown.toHtml(x.content getOrElse "No description given.",
                      repository, false, true, true)
              ))
        }
      } else Unauthorized
    } getOrElse NotFound
  })

  ajaxGet("/:owner/:repository/issue_comments/_data/:id")(readableUsersOnly { repository =>
    getComment(repository.owner, repository.name, params("id")) map { x =>
      if(hasWritePermission(x.userName, x.repositoryName, context.loginAccount) ||
          x.commentedUserName == context.loginAccount.get.userName){
        params.get("dataType") collect {
          case t if t == "html" => issues.html.editcomment(
              x.content, x.commentId, x.userName, x.repositoryName)
        } getOrElse {
          contentType = formats("json")
          org.json4s.jackson.Serialization.write(
              Map("content" -> view.Markdown.toHtml(x.content,
                  repository, false, true, true)
              ))
        }
      } else Unauthorized
    } getOrElse NotFound
  })

  ajaxPost("/:owner/:repository/issues/:id/label/new")(collaboratorsOnly { repository =>
    val issueId = params("id").toInt

    registerIssueLabel(repository.owner, repository.name, issueId, params("labelId").toInt)
    issues.html.labellist(getIssueLabels(repository.owner, repository.name, issueId))
  })

  ajaxPost("/:owner/:repository/issues/:id/label/delete")(collaboratorsOnly { repository =>
    val issueId = params("id").toInt

    deleteIssueLabel(repository.owner, repository.name, issueId, params("labelId").toInt)
    issues.html.labellist(getIssueLabels(repository.owner, repository.name, issueId))
  })

  ajaxPost("/:owner/:repository/issues/:id/assign")(collaboratorsOnly { repository =>
    updateAssignedUserName(repository.owner, repository.name, params("id").toInt,
        params.get("assignedUserName") filter (_.trim != ""))
    Ok("updated")
  })

  ajaxPost("/:owner/:repository/issues/:id/milestone")(collaboratorsOnly { repository =>
    updateMilestoneId(repository.owner, repository.name, params("id").toInt,
        params.get("milestoneId") collect { case x if x.trim != "" => x.toInt })
    Ok("updated")
  })

  private def searchIssues(filter: String, repository: RepositoryService.RepositoryInfo) = {
    val owner      = repository.owner
    val repoName   = repository.name
    val userName   = if(filter != "all") Some(params("userName")) else None
    val sessionKey = "%s/%s/issues".format(owner, repoName)

    val page = try {
      val i = params.getOrElse("page", "1").toInt
      if(i <= 0) 1 else i
    } catch {
      case e: NumberFormatException => 1
    }

    // retrieve search condition
    val condition = if(request.getQueryString == null){
      session.get(sessionKey).getOrElse(IssueSearchCondition()).asInstanceOf[IssueSearchCondition]
    } else IssueSearchCondition(request)

    session.put(sessionKey, condition)

    issues.html.list(
        searchIssue(owner, repoName, condition, filter, userName, (page - 1) * IssueLimit, IssueLimit),
        page,
        getLabels(owner, repoName),
        getMilestones(owner, repoName).filter(_.closedDate.isEmpty),
        countIssue(owner, repoName, condition.copy(state = "open"), filter, userName),
        countIssue(owner, repoName, condition.copy(state = "closed"), filter, userName),
        countIssue(owner, repoName, condition, "all", None),
        context.loginAccount.map(x => countIssue(owner, repoName, condition, "assigned", Some(x.userName))),
        context.loginAccount.map(x => countIssue(owner, repoName, condition, "created_by", Some(x.userName))),
        countIssueGroupByLabels(owner, repoName, condition, filter, userName),
        condition,
        filter,
        repository,
        hasWritePermission(owner, repoName, context.loginAccount))
  }

}
