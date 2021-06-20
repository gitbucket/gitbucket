package gitbucket.core.controller

import gitbucket.core.issues.html
import gitbucket.core.model.Account
import gitbucket.core.service.IssuesService._
import gitbucket.core.service._
import gitbucket.core.util.Implicits._
import gitbucket.core.util._
import gitbucket.core.view
import gitbucket.core.view.Markdown
import org.scalatra.forms._
import org.scalatra.{BadRequest, Ok}

class IssuesController
    extends IssuesControllerBase
    with IssuesService
    with RepositoryService
    with AccountService
    with LabelsService
    with MilestonesService
    with ActivityService
    with HandleCommentService
    with IssueCreationService
    with ReadableUsersAuthenticator
    with ReferrerAuthenticator
    with WritableUsersAuthenticator
    with MergeService
    with PullRequestService
    with WebHookIssueCommentService
    with WebHookPullRequestReviewCommentService
    with CommitsService
    with PrioritiesService
    with RequestCache

trait IssuesControllerBase extends ControllerBase {
  self: IssuesService
    with RepositoryService
    with AccountService
    with LabelsService
    with MilestonesService
    with ActivityService
    with HandleCommentService
    with IssueCreationService
    with ReadableUsersAuthenticator
    with ReferrerAuthenticator
    with WritableUsersAuthenticator
    with PullRequestService
    with WebHookIssueCommentService
    with PrioritiesService =>

  case class IssueCreateForm(
    title: String,
    content: Option[String],
    assignedUserName: Option[String],
    milestoneId: Option[Int],
    priorityId: Option[Int],
    labelNames: Option[String]
  )
  case class CommentForm(issueId: Int, content: String)
  case class IssueStateForm(issueId: Int, content: Option[String])

  val issueCreateForm = mapping(
    "title" -> trim(label("Title", text(required))),
    "content" -> trim(optional(text())),
    "assignedUserName" -> trim(optional(text())),
    "milestoneId" -> trim(optional(number())),
    "priorityId" -> trim(optional(number())),
    "labelNames" -> trim(optional(text()))
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
    if (Option(q).exists(_.contains("is:pr"))) {
      redirect(s"/${repository.owner}/${repository.name}/pulls?q=${StringUtil.urlEncode(q)}")
    } else {
      searchIssues(repository)
    }
  })

  get("/:owner/:repository/issues/:id")(referrersOnly { repository =>
    val issueId = params("id")
    getIssue(repository.owner, repository.name, issueId) map {
      issue =>
        if (issue.isPullRequest) {
          redirect(s"/${repository.owner}/${repository.name}/pull/${issueId}")
        } else {
          html.issue(
            issue,
            getComments(repository.owner, repository.name, issueId.toInt),
            getIssueLabels(repository.owner, repository.name, issueId.toInt),
            getAssignableUserNames(repository.owner, repository.name),
            getMilestonesWithIssueCount(repository.owner, repository.name),
            getPriorities(repository.owner, repository.name),
            getLabels(repository.owner, repository.name),
            isIssueEditable(repository),
            isIssueManageable(repository),
            isIssueCommentManageable(repository),
            repository
          )
        }
    } getOrElse NotFound()
  })

  get("/:owner/:repository/issues/new")(readableUsersOnly { repository =>
    if (isIssueEditable(repository)) { // TODO Should this check is provided by authenticator?
      html.create(
        getAssignableUserNames(repository.owner, repository.name),
        getMilestones(repository.owner, repository.name),
        getPriorities(repository.owner, repository.name),
        getDefaultPriority(repository.owner, repository.name),
        getLabels(repository.owner, repository.name),
        isIssueManageable(repository),
        getContentTemplate(repository, "ISSUE_TEMPLATE"),
        repository
      )
    } else Unauthorized()
  })

  post("/:owner/:repository/issues/new", issueCreateForm)(readableUsersOnly { (form, repository) =>
    context.withLoginAccount {
      loginAccount =>
        if (isIssueEditable(repository)) { // TODO Should this check is provided by authenticator?
          val issue = createIssue(
            repository,
            form.title,
            form.content,
            form.assignedUserName,
            form.milestoneId,
            form.priorityId,
            form.labelNames.toSeq.flatMap(_.split(",")),
            loginAccount
          )
          redirect(s"/${issue.userName}/${issue.repositoryName}/issues/${issue.issueId}")
        } else Unauthorized()
    }
  })

  ajaxPost("/:owner/:repository/issues/edit_title/:id", issueTitleEditForm)(readableUsersOnly { (title, repository) =>
    context.withLoginAccount {
      loginAccount =>
        getIssue(repository.owner, repository.name, params("id")).map {
          issue =>
            if (isEditableContent(repository.owner, repository.name, issue.openedUserName, loginAccount)) {
              if (issue.title != title) {
                // update issue
                updateIssue(repository.owner, repository.name, issue.issueId, title, issue.content)
                // extract references and create refer comment
                createReferComment(repository.owner, repository.name, issue.copy(title = title), title, loginAccount)
                createComment(
                  repository.owner,
                  repository.name,
                  loginAccount.userName,
                  issue.issueId,
                  issue.title + "\r\n" + title,
                  "change_title"
                )
              }
              redirect(s"/${repository.owner}/${repository.name}/issues/_data/${issue.issueId}")
            } else Unauthorized()
        } getOrElse NotFound()
    }
  })

  ajaxPost("/:owner/:repository/issues/edit/:id", issueEditForm)(readableUsersOnly { (content, repository) =>
    context.withLoginAccount {
      loginAccount =>
        getIssue(repository.owner, repository.name, params("id")).map { issue =>
          if (isEditableContent(repository.owner, repository.name, issue.openedUserName, loginAccount)) {
            // update issue
            updateIssue(repository.owner, repository.name, issue.issueId, issue.title, content)
            // extract references and create refer comment
            createReferComment(repository.owner, repository.name, issue, content.getOrElse(""), loginAccount)

            redirect(s"/${repository.owner}/${repository.name}/issues/_data/${issue.issueId}")
          } else Unauthorized()
        } getOrElse NotFound()
    }
  })

  post("/:owner/:repository/issue_comments/new", commentForm)(readableUsersOnly { (form, repository) =>
    context.withLoginAccount {
      loginAccount =>
        getIssue(repository.owner, repository.name, form.issueId.toString).flatMap { issue =>
          val actionOpt =
            params
              .get("action")
              .filter(_ => isEditableContent(issue.userName, issue.repositoryName, issue.openedUserName, loginAccount))
          handleComment(issue, Some(form.content), repository, actionOpt) map {
            case (issue, id) =>
              redirect(
                s"/${repository.owner}/${repository.name}/${if (issue.isPullRequest) "pull" else "issues"}/${form.issueId}#comment-${id}"
              )
          }
        } getOrElse NotFound()
    }
  })

  post("/:owner/:repository/issue_comments/state", issueStateForm)(readableUsersOnly { (form, repository) =>
    context.withLoginAccount {
      loginAccount =>
        getIssue(repository.owner, repository.name, form.issueId.toString).flatMap { issue =>
          val actionOpt =
            params
              .get("action")
              .filter(_ => isEditableContent(issue.userName, issue.repositoryName, issue.openedUserName, loginAccount))
          handleComment(issue, form.content, repository, actionOpt) map {
            case (issue, id) =>
              redirect(
                s"/${repository.owner}/${repository.name}/${if (issue.isPullRequest) "pull" else "issues"}/${form.issueId}#comment-${id}"
              )
          }
        } getOrElse NotFound()
    }
  })

  ajaxPost("/:owner/:repository/issue_comments/edit/:id", commentForm)(readableUsersOnly { (form, repository) =>
    context.withLoginAccount {
      loginAccount =>
        getComment(repository.owner, repository.name, params("id")).map { comment =>
          if (isEditableContent(repository.owner, repository.name, comment.commentedUserName, loginAccount)) {
            updateComment(comment.issueId, comment.commentId, form.content)
            redirect(s"/${repository.owner}/${repository.name}/issue_comments/_data/${comment.commentId}")
          } else Unauthorized()
        } getOrElse NotFound()
    }
  })

  ajaxPost("/:owner/:repository/issue_comments/delete/:id")(readableUsersOnly { repository =>
    context.withLoginAccount { loginAccount =>
      getComment(repository.owner, repository.name, params("id")).map { comment =>
        if (isDeletableComment(repository.owner, repository.name, comment.commentedUserName, loginAccount)) {
          Ok(deleteComment(repository.owner, repository.name, comment.issueId, comment.commentId))
        } else Unauthorized()
      } getOrElse NotFound()
    }
  })

  ajaxGet("/:owner/:repository/issues/_data/:id")(readableUsersOnly { repository =>
    context.withLoginAccount {
      loginAccount =>
        getIssue(repository.owner, repository.name, params("id")) map {
          x =>
            if (isEditableContent(x.userName, x.repositoryName, x.openedUserName, loginAccount)) {
              params.get("dataType") collect {
                case t if t == "html" => html.editissue(x.content, x.issueId, repository)
              } getOrElse {
                contentType = formats("json")
                org.json4s.jackson.Serialization.write(
                  Map(
                    "title" -> x.title,
                    "content" -> Markdown.toHtml(
                      markdown = x.content getOrElse "No description given.",
                      repository = repository,
                      branch = repository.repository.defaultBranch,
                      enableWikiLink = false,
                      enableRefsLink = true,
                      enableAnchor = true,
                      enableLineBreaks = true,
                      enableTaskList = true,
                      hasWritePermission = true
                    )
                  )
                )
              }
            } else Unauthorized()
        } getOrElse NotFound()
    }
  })

  ajaxGet("/:owner/:repository/issue_comments/_data/:id")(readableUsersOnly { repository =>
    context.withLoginAccount {
      loginAccount =>
        getComment(repository.owner, repository.name, params("id")) map {
          x =>
            if (isEditableContent(x.userName, x.repositoryName, x.commentedUserName, loginAccount)) {
              params.get("dataType") collect {
                case t if t == "html" => html.editcomment(x.content, x.commentId, repository)
              } getOrElse {
                contentType = formats("json")
                org.json4s.jackson.Serialization.write(
                  Map(
                    "content" -> view.Markdown.toHtml(
                      markdown = x.content,
                      repository = repository,
                      branch = repository.repository.defaultBranch,
                      enableWikiLink = false,
                      enableRefsLink = true,
                      enableAnchor = true,
                      enableLineBreaks = true,
                      enableTaskList = true,
                      hasWritePermission = true
                    )
                  )
                )
              }
            } else Unauthorized()
        } getOrElse NotFound()
    }
  })

  ajaxPost("/:owner/:repository/issues/new/label")(writableUsersOnly { repository =>
    val labelNames = params("labelNames").split(",")
    val labels = getLabels(repository.owner, repository.name).filter(x => labelNames.contains(x.labelName))
    html.labellist(labels)
  })

  ajaxPost("/:owner/:repository/issues/:id/label/new")(writableUsersOnly { repository =>
    val issueId = params("id").toInt
    registerIssueLabel(repository.owner, repository.name, issueId, params("labelId").toInt, true)
    html.labellist(getIssueLabels(repository.owner, repository.name, issueId))
  })

  ajaxPost("/:owner/:repository/issues/:id/label/delete")(writableUsersOnly { repository =>
    val issueId = params("id").toInt
    deleteIssueLabel(repository.owner, repository.name, issueId, params("labelId").toInt, true)
    html.labellist(getIssueLabels(repository.owner, repository.name, issueId))
  })

  ajaxPost("/:owner/:repository/issues/:id/assign")(writableUsersOnly { repository =>
    updateAssignedUserName(
      repository.owner,
      repository.name,
      params("id").toInt,
      assignedUserName("assignedUserName"),
      true
    )
    Ok("updated")
  })

  ajaxPost("/:owner/:repository/issues/:id/milestone")(writableUsersOnly { repository =>
    updateMilestoneId(repository.owner, repository.name, params("id").toInt, milestoneId("milestoneId"), true)
    milestoneId("milestoneId").map { milestoneId =>
      getMilestonesWithIssueCount(repository.owner, repository.name)
        .find(_._1.milestoneId == milestoneId)
        .map {
          case (_, openCount, closeCount) =>
            gitbucket.core.issues.milestones.html.progress(openCount + closeCount, closeCount)
        } getOrElse NotFound()
    } getOrElse Ok()
  })

  ajaxPost("/:owner/:repository/issues/:id/priority")(writableUsersOnly { repository =>
    val priority = priorityId("priorityId")
    updatePriorityId(repository.owner, repository.name, params("id").toInt, priority, true)
    Ok("updated")
  })

  post("/:owner/:repository/issues/batchedit/state")(writableUsersOnly { repository =>
    val action = params.get("value")
    action match {
      case Some("open") =>
        executeBatch(repository) { issueId =>
          getIssue(repository.owner, repository.name, issueId.toString).foreach { issue =>
            handleComment(issue, None, repository, Some("reopen"))
          }
        }
        if (params("uri").nonEmpty) {
          redirect(params("uri"))
        }
      case Some("close") =>
        executeBatch(repository) { issueId =>
          getIssue(repository.owner, repository.name, issueId.toString).foreach { issue =>
            handleComment(issue, None, repository, Some("close"))
          }
        }
        if (params("uri").nonEmpty) {
          redirect(params("uri"))
        }
      case _ => BadRequest()
    }
  })

  post("/:owner/:repository/issues/batchedit/label")(writableUsersOnly { repository =>
    params("value").toIntOpt.map { labelId =>
      executeBatch(repository) { issueId =>
        getIssueLabel(repository.owner, repository.name, issueId, labelId) getOrElse {
          registerIssueLabel(repository.owner, repository.name, issueId, labelId, true)
          if (params("uri").nonEmpty) {
            redirect(params("uri"))
          }
        }
      }
    } getOrElse NotFound()
  })

  post("/:owner/:repository/issues/batchedit/assign")(writableUsersOnly { repository =>
    val value = assignedUserName("value")
    executeBatch(repository) {
      updateAssignedUserName(repository.owner, repository.name, _, value, true)
    }
    if (params("uri").nonEmpty) {
      redirect(params("uri"))
    }
  })

  post("/:owner/:repository/issues/batchedit/milestone")(writableUsersOnly { repository =>
    val value = milestoneId("value")
    executeBatch(repository) {
      updateMilestoneId(repository.owner, repository.name, _, value, true)
    }
  })

  post("/:owner/:repository/issues/batchedit/priority")(writableUsersOnly { repository =>
    val value = priorityId("value")
    executeBatch(repository) {
      updatePriorityId(repository.owner, repository.name, _, value, true)
    }
  })

  get("/:owner/:repository/_attached/:file")(referrersOnly { repository =>
    (Directory.getAttachedDir(repository.owner, repository.name) match {
      case dir if (dir.exists && dir.isDirectory) =>
        dir.listFiles.find(_.getName.startsWith(params("file") + ".")).map { file =>
          response.setHeader("Content-Disposition", f"""inline; filename=${file.getName}""")
          RawData(FileUtil.getSafeMimeType(file.getName), file)
        }
      case _ => None
    }) getOrElse NotFound()
  })

  /**
   * JSON API for issue and PR completion.
   */
  ajaxGet("/:owner/:repository/_issue/proposals")(writableUsersOnly { repository =>
    contentType = formats("json")
    org.json4s.jackson.Serialization.write(
      Map(
        "options" -> (
          getOpenIssues(repository.owner, repository.name)
            .map { t =>
              Map(
                "label" -> s"""${if (t.isPullRequest) "<i class='octicon octicon-git-pull-request'></i>"
                else "<i class='octicon octicon-issue-opened'></i>"}<b> #${StringUtil
                  .escapeHtml(t.issueId.toString)} ${StringUtil
                  .escapeHtml(StringUtil.cutTail(t.title, 50, "..."))}</b>""",
                "value" -> t.issueId.toString
              )
            }
        )
      )
    )
  })

  val assignedUserName = (key: String) => params.get(key) filter (_.trim != "")
  val milestoneId: String => Option[Int] = (key: String) => params.get(key).flatMap(_.toIntOpt)
  val priorityId: String => Option[Int] = (key: String) => params.get(key).flatMap(_.toIntOpt)

  private def executeBatch(repository: RepositoryService.RepositoryInfo)(execute: Int => Unit) = {
    params("checked").split(',') map (_.toInt) foreach execute
    params("from") match {
      case "issues" => redirect(s"/${repository.owner}/${repository.name}/issues")
      case "pulls"  => redirect(s"/${repository.owner}/${repository.name}/pulls")
      case _        =>
    }
  }

  private def searchIssues(repository: RepositoryService.RepositoryInfo) = {
    val page = IssueSearchCondition.page(request)
    // retrieve search condition
    val condition = IssueSearchCondition(request)
    // search issues
    val issues =
      searchIssue(
        condition,
        IssueSearchOption.Issues,
        (page - 1) * IssueLimit,
        IssueLimit,
        repository.owner -> repository.name
      )

    html.list(
      "issues",
      issues.map(issue => (issue, None)),
      page,
      getAssignableUserNames(repository.owner, repository.name),
      getMilestones(repository.owner, repository.name),
      getPriorities(repository.owner, repository.name),
      getLabels(repository.owner, repository.name),
      countIssue(condition.copy(state = "open"), IssueSearchOption.Issues, repository.owner -> repository.name),
      countIssue(condition.copy(state = "closed"), IssueSearchOption.Issues, repository.owner -> repository.name),
      condition,
      repository,
      isIssueEditable(repository),
      isIssueManageable(repository)
    )
  }

  /**
   * Tests whether an issue or a comment is editable by a logged-in user.
   */
  private def isEditableContent(owner: String, repository: String, author: String, loginAccount: Account)(
    implicit context: Context
  ): Boolean = {
    hasDeveloperRole(owner, repository, context.loginAccount) || author == loginAccount.userName
  }

  /**
   * Tests whether an issue comment is deletable by a logged-in user.
   */
  private def isDeletableComment(owner: String, repository: String, author: String, loginAccount: Account)(
    implicit context: Context
  ): Boolean = {
    hasOwnerRole(owner, repository, context.loginAccount) || author == loginAccount.userName
  }
}
