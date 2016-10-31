package gitbucket.core.controller

import gitbucket.core.issues.html
import gitbucket.core.service.IssuesService._
import gitbucket.core.service._
import gitbucket.core.util.ControlUtil._
import gitbucket.core.util.Implicits._
import gitbucket.core.util._
import gitbucket.core.view
import gitbucket.core.view.Markdown

import io.github.gitbucket.scalatra.forms._
import org.scalatra.Ok


class IssuesController extends IssuesControllerBase
  with IssuesService with RepositoryService with AccountService with LabelsService with MilestonesService with ActivityService with HandleCommentService
  with ReadableUsersAuthenticator with ReferrerAuthenticator with CollaboratorsAuthenticator with PullRequestService with WebHookIssueCommentService

trait IssuesControllerBase extends ControllerBase {
  self: IssuesService with RepositoryService with AccountService with LabelsService with MilestonesService with ActivityService with HandleCommentService
    with ReadableUsersAuthenticator with ReferrerAuthenticator with CollaboratorsAuthenticator with PullRequestService with WebHookIssueCommentService =>

  case class IssueCreateForm(title: String, content: Option[String],
    assignedUserName: Option[String], milestoneId: Option[Int], labelNames: Option[String])
  case class CommentForm(issueId: Int, content: String)
  case class IssueStateForm(issueId: Int, content: Option[String])

  val issueCreateForm = mapping(
      "title"            -> trim(label("Title", text(required))),
      "content"          -> trim(optional(text())),
      "assignedUserName" -> trim(optional(text())),
      "milestoneId"      -> trim(optional(number())),
      "labelNames"       -> trim(optional(text()))
    )(IssueCreateForm.apply)

  val issueTitleEditForm = mapping(
    "title" -> trim(label("Title", text(required)))
    )(x => x)
  val issueEditForm = mapping(
    "content" -> trim(optional(text()))
    )(x => x)

  val commentForm = mapping(
      "issueId" -> label("Issue Id", number()),
      "content" -> trim(label("Comment", text(required)))
    )(CommentForm.apply)

  val issueStateForm = mapping(
      "issueId" -> label("Issue Id", number()),
      "content" -> trim(optional(text()))
    )(IssueStateForm.apply)

  get("/:owner/:repository/issues")(referrersOnly { repository =>
    val q = request.getParameter("q")
    if(Option(q).exists(_.contains("is:pr"))){
      redirect(s"/${repository.owner}/${repository.name}/pulls?q=" + StringUtil.urlEncode(q))
    } else {
      searchIssues(repository)
    }
  })

  get("/:owner/:repository/issues/:id")(referrersOnly { repository =>
    defining(repository.owner, repository.name, params("id")){ case (owner, name, issueId) =>
      getIssue(owner, name, issueId) map {
        html.issue(
          _,
          getComments(owner, name, issueId.toInt),
          getIssueLabels(owner, name, issueId.toInt),
          getAssignableUserNames(owner, name),
          getMilestonesWithIssueCount(owner, name),
          getLabels(owner, name),
          hasWritePermission(owner, name, context.loginAccount),
          repository)
      } getOrElse NotFound()
    }
  })

  get("/:owner/:repository/issues/new")(readableUsersOnly { repository =>
    defining(repository.owner, repository.name){ case (owner, name) =>
      html.create(
        getAssignableUserNames(owner, name),
        getMilestones(owner, name),
        getLabels(owner, name),
        hasWritePermission(owner, name, context.loginAccount),
        repository)
    }
  })

  post("/:owner/:repository/issues/new", issueCreateForm)(readableUsersOnly { (form, repository) =>
    defining(repository.owner, repository.name){ case (owner, name) =>
      val writable = hasWritePermission(owner, name, context.loginAccount)
      val userName = context.loginAccount.get.userName

      // insert issue
      val issueId = createIssue(owner, name, userName, form.title, form.content,
        if(writable) form.assignedUserName else None,
        if(writable) form.milestoneId else None)

      // insert labels
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

      // record activity
      recordCreateIssueActivity(owner, name, userName, issueId, form.title)

      getIssue(owner, name, issueId.toString).foreach { issue =>
        // extract references and create refer comment
        createReferComment(owner, name, issue, form.title + " " + form.content.getOrElse(""), context.loginAccount.get)

        // call web hooks
        callIssuesWebHook("opened", repository, issue, context.baseUrl, context.loginAccount.get)

        // notifications
        Notifier().toNotify(repository, issue, form.content.getOrElse("")){
          Notifier.msgIssue(s"${context.baseUrl}/${owner}/${name}/issues/${issueId}")
        }
      }

      redirect(s"/${owner}/${name}/issues/${issueId}")
    }
  })

  ajaxPost("/:owner/:repository/issues/edit_title/:id", issueTitleEditForm)(readableUsersOnly { (title, repository) =>
    defining(repository.owner, repository.name){ case (owner, name) =>
      getIssue(owner, name, params("id")).map { issue =>
        if(isEditable(owner, name, issue.openedUserName)){
          // update issue
          updateIssue(owner, name, issue.issueId, title, issue.content)
          // extract references and create refer comment
          createReferComment(owner, name, issue.copy(title = title), title, context.loginAccount.get)

          redirect(s"/${owner}/${name}/issues/_data/${issue.issueId}")
        } else Unauthorized()
      } getOrElse NotFound()
    }
  })

  ajaxPost("/:owner/:repository/issues/edit/:id", issueEditForm)(readableUsersOnly { (content, repository) =>
    defining(repository.owner, repository.name){ case (owner, name) =>
      getIssue(owner, name, params("id")).map { issue =>
        if(isEditable(owner, name, issue.openedUserName)){
          // update issue
          updateIssue(owner, name, issue.issueId, issue.title, content)
          // extract references and create refer comment
          createReferComment(owner, name, issue, content.getOrElse(""), context.loginAccount.get)

          redirect(s"/${owner}/${name}/issues/_data/${issue.issueId}")
        } else Unauthorized()
      } getOrElse NotFound()
    }
  })

  post("/:owner/:repository/issue_comments/new", commentForm)(readableUsersOnly { (form, repository) =>
    getIssue(repository.owner, repository.name, form.issueId.toString).flatMap { issue =>
      val actionOpt = params.get("action").filter(_ => isEditable(issue.userName, issue.repositoryName, issue.openedUserName))
      handleComment(issue, Some(form.content), repository, actionOpt) map { case (issue, id) =>
        redirect(s"/${repository.owner}/${repository.name}/${
          if(issue.isPullRequest) "pull" else "issues"}/${form.issueId}#comment-${id}")
      }
    } getOrElse NotFound()
  })

  post("/:owner/:repository/issue_comments/state", issueStateForm)(readableUsersOnly { (form, repository) =>
    getIssue(repository.owner, repository.name, form.issueId.toString).flatMap { issue =>
      val actionOpt = params.get("action").filter(_ => isEditable(issue.userName, issue.repositoryName, issue.openedUserName))
      handleComment(issue, form.content, repository, actionOpt) map { case (issue, id) =>
        redirect(s"/${repository.owner}/${repository.name}/${
          if(issue.isPullRequest) "pull" else "issues"}/${form.issueId}#comment-${id}")
      }
    } getOrElse NotFound()
  })

  ajaxPost("/:owner/:repository/issue_comments/edit/:id", commentForm)(readableUsersOnly { (form, repository) =>
    defining(repository.owner, repository.name){ case (owner, name) =>
      getComment(owner, name, params("id")).map { comment =>
        if(isEditable(owner, name, comment.commentedUserName)){
          updateComment(comment.commentId, form.content)
          redirect(s"/${owner}/${name}/issue_comments/_data/${comment.commentId}")
        } else Unauthorized()
      } getOrElse NotFound()
    }
  })

  ajaxPost("/:owner/:repository/issue_comments/delete/:id")(readableUsersOnly { repository =>
    defining(repository.owner, repository.name){ case (owner, name) =>
      getComment(owner, name, params("id")).map { comment =>
        if(isEditable(owner, name, comment.commentedUserName)){
          Ok(deleteComment(comment.commentId))
        } else Unauthorized()
      } getOrElse NotFound()
    }
  })

  ajaxGet("/:owner/:repository/issues/_data/:id")(readableUsersOnly { repository =>
    getIssue(repository.owner, repository.name, params("id")) map { x =>
      if(isEditable(x.userName, x.repositoryName, x.openedUserName)){
        params.get("dataType") collect {
          case t if t == "html" => html.editissue(x.content, x.issueId, repository)
        } getOrElse {
          contentType = formats("json")
          org.json4s.jackson.Serialization.write(
            Map(
              "title"   -> x.title,
              "content" -> Markdown.toHtml(
                markdown = x.content getOrElse "No description given.",
                repository = repository,
                enableWikiLink = false,
                enableRefsLink = true,
                enableAnchor = true,
                enableLineBreaks = true,
                enableTaskList = true,
                hasWritePermission = isEditable(x.userName, x.repositoryName, x.openedUserName)
              )
            )
          )
        }
      } else Unauthorized()
    } getOrElse NotFound()
  })

  ajaxGet("/:owner/:repository/issue_comments/_data/:id")(readableUsersOnly { repository =>
    getComment(repository.owner, repository.name, params("id")) map { x =>
      if(isEditable(x.userName, x.repositoryName, x.commentedUserName)){
        params.get("dataType") collect {
          case t if t == "html" => html.editcomment(x.content, x.commentId, repository)
        } getOrElse {
          contentType = formats("json")
          org.json4s.jackson.Serialization.write(
            Map(
              "content" -> view.Markdown.toHtml(
                markdown = x.content,
                repository = repository,
                enableWikiLink = false,
                enableRefsLink = true,
                enableAnchor = true,
                enableLineBreaks = true,
                enableTaskList = true,
                hasWritePermission = isEditable(x.userName, x.repositoryName, x.commentedUserName)
              )
            )
          )
        }
      } else Unauthorized()
    } getOrElse NotFound()
  })

  ajaxPost("/:owner/:repository/issues/new/label")(collaboratorsOnly { repository =>
    val labelNames = params("labelNames").split(",")
    val labels = getLabels(repository.owner, repository.name).filter(x => labelNames.contains(x.labelName))
    html.labellist(labels)
  })

  ajaxPost("/:owner/:repository/issues/:id/label/new")(collaboratorsOnly { repository =>
    defining(params("id").toInt){ issueId =>
      registerIssueLabel(repository.owner, repository.name, issueId, params("labelId").toInt)
      html.labellist(getIssueLabels(repository.owner, repository.name, issueId))
    }
  })

  ajaxPost("/:owner/:repository/issues/:id/label/delete")(collaboratorsOnly { repository =>
    defining(params("id").toInt){ issueId =>
      deleteIssueLabel(repository.owner, repository.name, issueId, params("labelId").toInt)
      html.labellist(getIssueLabels(repository.owner, repository.name, issueId))
    }
  })

  ajaxPost("/:owner/:repository/issues/:id/assign")(collaboratorsOnly { repository =>
    updateAssignedUserName(repository.owner, repository.name, params("id").toInt, assignedUserName("assignedUserName"))
    Ok("updated")
  })

  ajaxPost("/:owner/:repository/issues/:id/milestone")(collaboratorsOnly { repository =>
    updateMilestoneId(repository.owner, repository.name, params("id").toInt, milestoneId("milestoneId"))
    milestoneId("milestoneId").map { milestoneId =>
      getMilestonesWithIssueCount(repository.owner, repository.name)
          .find(_._1.milestoneId == milestoneId).map { case (_, openCount, closeCount) =>
        gitbucket.core.issues.milestones.html.progress(openCount + closeCount, closeCount)
      } getOrElse NotFound()
    } getOrElse Ok()
  })

  post("/:owner/:repository/issues/batchedit/state")(collaboratorsOnly { repository =>
    defining(params.get("value")){ action =>
      action match {
        case Some("open")  => executeBatch(repository) { issueId =>
          getIssue(repository.owner, repository.name, issueId.toString).foreach { issue =>
            handleComment(issue, None, repository, Some("reopen"))
          }
        }
        case Some("close") => executeBatch(repository) { issueId =>
          getIssue(repository.owner, repository.name, issueId.toString).foreach { issue =>
            handleComment(issue, None, repository, Some("close"))
          }
        }
        case _ => // TODO BadRequest
      }
    }
  })

  post("/:owner/:repository/issues/batchedit/label")(collaboratorsOnly { repository =>
    params("value").toIntOpt.map{ labelId =>
      executeBatch(repository) { issueId =>
        getIssueLabel(repository.owner, repository.name, issueId, labelId) getOrElse {
          registerIssueLabel(repository.owner, repository.name, issueId, labelId)
        }
      }
    } getOrElse NotFound()
  })

  post("/:owner/:repository/issues/batchedit/assign")(collaboratorsOnly { repository =>
    defining(assignedUserName("value")){ value =>
      executeBatch(repository) {
        updateAssignedUserName(repository.owner, repository.name, _, value)
      }
    }
  })

  post("/:owner/:repository/issues/batchedit/milestone")(collaboratorsOnly { repository =>
    defining(milestoneId("value")){ value =>
      executeBatch(repository) {
        updateMilestoneId(repository.owner, repository.name, _, value)
      }
    }
  })

  get("/:owner/:repository/_attached/:file")(referrersOnly { repository =>
    (Directory.getAttachedDir(repository.owner, repository.name) match {
      case dir if(dir.exists && dir.isDirectory) =>
        dir.listFiles.find(_.getName.startsWith(params("file") + ".")).map { file =>
          response.setHeader("Content-Disposition", f"""inline; filename=${file.getName}""")
          RawData(FileUtil.getMimeType(file.getName), file)
        }
      case _ => None
    }) getOrElse NotFound()
  })

  val assignedUserName = (key: String) => params.get(key) filter (_.trim != "")
  val milestoneId: String => Option[Int] = (key: String) => params.get(key).flatMap(_.toIntOpt)

  private def isEditable(owner: String, repository: String, author: String)(implicit context: Context): Boolean =
    hasWritePermission(owner, repository, context.loginAccount) || author == context.loginAccount.get.userName

  private def executeBatch(repository: RepositoryService.RepositoryInfo)(execute: Int => Unit) = {
    params("checked").split(',') map(_.toInt) foreach execute
    params("from") match {
      case "issues" => redirect(s"/${repository.owner}/${repository.name}/issues")
      case "pulls"  => redirect(s"/${repository.owner}/${repository.name}/pulls")
    }
  }

  private def searchIssues(repository: RepositoryService.RepositoryInfo) = {
    defining(repository.owner, repository.name){ case (owner, repoName) =>
      val page       = IssueSearchCondition.page(request)
      val sessionKey = Keys.Session.Issues(owner, repoName)

      // retrieve search condition
      val condition = IssueSearchCondition(request)

      html.list(
          "issues",
          searchIssue(condition, false, (page - 1) * IssueLimit, IssueLimit, owner -> repoName),
          page,
          getAssignableUserNames(owner, repoName),
          getMilestones(owner, repoName),
          getLabels(owner, repoName),
          countIssue(condition.copy(state = "open"  ), false, owner -> repoName),
          countIssue(condition.copy(state = "closed"), false, owner -> repoName),
          condition,
          repository,
          hasWritePermission(owner, repoName, context.loginAccount))
    }
  }

  // TODO Move to IssuesService?
  private def getAssignableUserNames(owner: String, repository: String): List[String] =
    (getCollaboratorUserNames(owner, repository, Seq("ADMIN", "WRITE")).map(_._1) :::
      (if(getAccountByUserName(owner).get.isGroupAccount) getGroupMembers(owner).map(_.userName) else List(owner))).sorted

}
