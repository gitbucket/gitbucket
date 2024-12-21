package gitbucket.core.controller

import gitbucket.core.model.activity.DeleteBranchInfo
import gitbucket.core.pulls.html
import gitbucket.core.service.CommitStatusService
import gitbucket.core.service.MergeService
import gitbucket.core.service.IssuesService.*
import gitbucket.core.service.PullRequestService.*
import gitbucket.core.service.RepositoryService.RepositoryInfo
import gitbucket.core.service.*
import gitbucket.core.util.Directory.*
import gitbucket.core.util.Implicits.*
import gitbucket.core.util.*
import org.scalatra.forms.*
import org.eclipse.jgit.api.Git
import org.scalatra.BadRequest

import scala.util.Using

class PullRequestsController
    extends PullRequestsControllerBase
    with RepositoryService
    with AccountService
    with IssuesService
    with PullRequestService
    with MilestonesService
    with LabelsService
    with CustomFieldsService
    with CommitsService
    with ActivityService
    with WebHookPullRequestService
    with WebHookPullRequestReviewCommentService
    with ReadableUsersAuthenticator
    with ReferrerAuthenticator
    with WritableUsersAuthenticator
    with CommitStatusService
    with MergeService
    with ProtectedBranchService
    with PrioritiesService
    with RequestCache

trait PullRequestsControllerBase extends ControllerBase {
  self: RepositoryService & AccountService & IssuesService & MilestonesService & LabelsService & CustomFieldsService &
    CommitsService & ActivityService & PullRequestService & WebHookPullRequestService & ReadableUsersAuthenticator &
    ReferrerAuthenticator & WritableUsersAuthenticator & CommitStatusService & MergeService & ProtectedBranchService &
    PrioritiesService =>

  private val pullRequestForm = mapping(
    "title" -> trim(label("Title", text(required))),
    "content" -> trim(label("Content", optional(text()))),
    "targetUserName" -> trim(text(required, maxlength(100))),
    "targetBranch" -> trim(text(required, maxlength(255))),
    "requestUserName" -> trim(text(required, maxlength(100))),
    "requestRepositoryName" -> trim(text(required, maxlength(100))),
    "requestBranch" -> trim(text(required, maxlength(255))),
    "commitIdFrom" -> trim(text(required, maxlength(40))),
    "commitIdTo" -> trim(text(required, maxlength(40))),
    "isDraft" -> trim(boolean(required)),
    "assigneeUserNames" -> trim(optional(text())),
    "milestoneId" -> trim(optional(number())),
    "priorityId" -> trim(optional(number())),
    "labelNames" -> trim(optional(text()))
  )(PullRequestForm.apply)

  private val mergeForm = mapping(
    "message" -> trim(label("Message", text(required))),
    "strategy" -> trim(label("Strategy", text(required))),
    "isDraft" -> trim(boolean(required))
  )(MergeForm.apply)

  private case class PullRequestForm(
    title: String,
    content: Option[String],
    targetUserName: String,
    targetBranch: String,
    requestUserName: String,
    requestRepositoryName: String,
    requestBranch: String,
    commitIdFrom: String,
    commitIdTo: String,
    isDraft: Boolean,
    assigneeUserNames: Option[String],
    milestoneId: Option[Int],
    priorityId: Option[Int],
    labelNames: Option[String]
  )

  private case class MergeForm(message: String, strategy: String, isDraft: Boolean)

  get("/:owner/:repository/pulls")(referrersOnly { repository =>
    val q = request.getParameter("q")
    Option(q) match {
      case Some(filter) if filter.contains("is:issue") =>
        redirect(s"/${repository.owner}/${repository.name}/issues?q=${StringUtil.urlEncode(q)}")
      case Some(filter) =>
        val condition = IssueSearchCondition(filter)
        if (condition.isEmpty) {
          // Redirect to keyword search
          redirect(s"/${repository.owner}/${repository.name}/search?q=${StringUtil.urlEncode(q)}&type=pulls")
        } else {
          searchPullRequests(repository, IssueSearchCondition(filter), IssueSearchCondition.page(request))
        }
      case None =>
        searchPullRequests(repository, IssueSearchCondition(request), IssueSearchCondition.page(request))
    }
  })

  get("/:owner/:repository/pull/:id")(referrersOnly { repository =>
    params("id").toIntOpt.flatMap { issueId =>
      getPullRequest(repository.owner, repository.name, issueId) map { case (issue, pullreq) =>
        val (commits, diffs) =
          getRequestCompareInfo(
            repository.owner,
            repository.name,
            pullreq.commitIdFrom,
            repository.owner,
            repository.name,
            pullreq.commitIdTo,
            context.settings
          )

        html.conversation(
          issue,
          pullreq,
          commits.flatten,
          getPullRequestComments(repository.owner, repository.name, issue.issueId, commits.flatten),
          diffs.size,
          getIssueLabels(repository.owner, repository.name, issueId),
          getIssueAssignees(repository.owner, repository.name, issueId),
          getAssignableUserNames(repository.owner, repository.name),
          getMilestonesWithIssueCount(repository.owner, repository.name),
          getPriorities(repository.owner, repository.name),
          getLabels(repository.owner, repository.name),
          getCustomFieldsWithValue(repository.owner, repository.name, issueId).filter(_._1.enableForPullRequests),
          isEditable(repository),
          isManageable(repository),
          hasDeveloperRole(pullreq.requestUserName, pullreq.requestRepositoryName, context.loginAccount),
          repository,
          getRepository(pullreq.requestUserName, pullreq.requestRepositoryName),
          flash.iterator.map(f => f._1 -> f._2.toString).toMap
        )
      }
    } getOrElse NotFound()
  })

  get("/:owner/:repository/pull/:id/commits")(referrersOnly { repository =>
    params("id").toIntOpt.flatMap { issueId =>
      getPullRequest(repository.owner, repository.name, issueId) map { case (issue, pullreq) =>
        val (commits, diffs) =
          getRequestCompareInfo(
            repository.owner,
            repository.name,
            pullreq.commitIdFrom,
            repository.owner,
            repository.name,
            pullreq.commitIdTo,
            context.settings
          )

        val commitsWithStatus = commits.map { day =>
          day.map { commit =>
            (commit, getCommitStatusWithSummary(repository.owner, repository.name, commit.id))
          }
        }

        html.commits(
          issue,
          pullreq,
          commitsWithStatus,
          getPullRequestComments(repository.owner, repository.name, issue.issueId, commits.flatten),
          diffs.size,
          isManageable(repository),
          repository
        )
      }
    } getOrElse NotFound()
  })

  get("/:owner/:repository/pull/:id/files")(referrersOnly { repository =>
    params("id").toIntOpt.flatMap { issueId =>
      getPullRequest(repository.owner, repository.name, issueId) map { case (issue, pullreq) =>
        val (commits, diffs) =
          getRequestCompareInfo(
            repository.owner,
            repository.name,
            pullreq.commitIdFrom,
            repository.owner,
            repository.name,
            pullreq.commitIdTo,
            context.settings
          )

        html.files(
          issue,
          pullreq,
          diffs,
          commits.flatten,
          getPullRequestComments(repository.owner, repository.name, issue.issueId, commits.flatten),
          isManageable(repository),
          repository
        )
      }
    } getOrElse NotFound()
  })

  ajaxGet("/:owner/:repository/pull/:id/mergeguide")(referrersOnly { repository =>
    params("id").toIntOpt.flatMap { issueId =>
      getPullRequest(repository.owner, repository.name, issueId) map { case (issue, pullreq) =>
        val conflictMessage = LockUtil.lock(s"${repository.owner}/${repository.name}") {
          checkConflict(repository.owner, repository.name, pullreq.branch, issueId)
        }
        val hasMergePermission = hasDeveloperRole(repository.owner, repository.name, context.loginAccount)
        val branchProtection = getProtectedBranchInfo(repository.owner, repository.name, pullreq.branch)
        val mergeStatus = PullRequestService.MergeStatus(
          conflictMessage = conflictMessage,
          commitStatuses = getCommitStatuses(repository.owner, repository.name, pullreq.commitIdTo),
          branchProtection = branchProtection,
          branchIsOutOfDate =
            !JGitUtil.getShaByRef(repository.owner, repository.name, pullreq.branch).contains(pullreq.commitIdFrom),
          needStatusCheck = context.loginAccount.forall { u =>
            branchProtection.needStatusCheck(u.userName)
          },
          hasUpdatePermission = hasDeveloperRole(
            pullreq.requestUserName,
            pullreq.requestRepositoryName,
            context.loginAccount
          ) &&
            context.loginAccount.exists { u =>
              !getProtectedBranchInfo(
                pullreq.requestUserName,
                pullreq.requestRepositoryName,
                pullreq.requestBranch
              ).needStatusCheck(u.userName)
            },
          hasMergePermission = hasMergePermission,
          commitIdTo = pullreq.commitIdTo
        )
        html.mergeguide(
          mergeStatus,
          issue,
          pullreq,
          repository,
          getRepository(pullreq.requestUserName, pullreq.requestRepositoryName).get
        )
      }
    } getOrElse NotFound()
  })

  get("/:owner/:repository/pull/:id/delete_branch")(readableUsersOnly { baseRepository =>
    (for {
      issueId <- params("id").toIntOpt
      loginAccount <- context.loginAccount
      case (issue, pullreq) <- getPullRequest(baseRepository.owner, baseRepository.name, issueId)
      owner = pullreq.requestUserName
      name = pullreq.requestRepositoryName
      if hasDeveloperRole(owner, name, context.loginAccount)
    } yield {
      val repository = getRepository(owner, name).get
      val branchProtection = getProtectedBranchInfo(owner, name, pullreq.requestBranch)
      if (branchProtection.enabled) {
        flash.update("error", s"branch ${pullreq.requestBranch} is protected.")
      } else {
        if (repository.repository.defaultBranch != pullreq.requestBranch) {
          val userName = context.loginAccount.get.userName
          Using.resource(Git.open(getRepositoryDir(repository.owner, repository.name))) { git =>
            git.branchDelete().setForce(true).setBranchNames(pullreq.requestBranch).call()
            val deleteBranchInfo = DeleteBranchInfo(repository.owner, repository.name, userName, pullreq.requestBranch)
            recordActivity(deleteBranchInfo)
          }
          createComment(
            baseRepository.owner,
            baseRepository.name,
            userName,
            issueId,
            pullreq.requestBranch,
            "delete_branch"
          )
        } else {
          flash.update("error", s"""Can't delete the default branch "${pullreq.requestBranch}".""")
        }
      }

      redirect(s"/${baseRepository.owner}/${baseRepository.name}/pull/${issueId}")
    }) getOrElse NotFound()
  })

  post("/:owner/:repository/pull/:id/update_branch")(readableUsersOnly { baseRepository =>
    (for {
      issueId <- params("id").toIntOpt
      loginAccount <- context.loginAccount
      case (issue, pullreq) <- getPullRequest(baseRepository.owner, baseRepository.name, issueId)
      repository <- getRepository(pullreq.requestUserName, pullreq.requestRepositoryName)
      remoteRepository <- getRepository(pullreq.userName, pullreq.repositoryName)
      owner = pullreq.requestUserName
      name = pullreq.requestRepositoryName
      if hasDeveloperRole(owner, name, context.loginAccount)
    } yield {
      val branchProtection = getProtectedBranchInfo(owner, name, pullreq.requestBranch)
      if (branchProtection.needStatusCheck(loginAccount.userName)) {
        flash.update("error", s"branch ${pullreq.requestBranch} is protected need status check.")
      } else {
        LockUtil.lock(s"$owner/$name") {
          val alias =
            if (
              pullreq.repositoryName == pullreq.requestRepositoryName && pullreq.userName == pullreq.requestUserName
            ) {
              pullreq.branch
            } else {
              s"${pullreq.userName}:${pullreq.branch}"
            }
//          val existIds = Using
//            .resource(Git.open(Directory.getRepositoryDir(owner, name))) { git =>
//              JGitUtil.getAllCommitIds(git)
//            }
//            .toSet
          pullRemote(
            repository,
            pullreq.requestBranch,
            remoteRepository,
            pullreq.branch,
            loginAccount,
            s"Merge branch '$alias' into ${pullreq.requestBranch}",
            Some(pullreq),
            context.settings
          ) match {
            case None => // conflict
              flash.update("error", s"Can't automatic merging branch '$alias' into ${pullreq.requestBranch}.")
            case Some(oldId) =>
              // update pull request
              updatePullRequests(owner, name, pullreq.requestBranch, loginAccount, "synchronize", context.settings)
              flash.update("info", s"Merge branch '$alias' into ${pullreq.requestBranch}")
          }
        }
      }
      redirect(s"/${baseRepository.owner}/${baseRepository.name}/pull/${issueId}")

    }) getOrElse NotFound()
  })

  post("/:owner/:repository/pull/:id/update_draft")(readableUsersOnly { baseRepository =>
    (for {
      issueId <- params("id").toIntOpt
      case (_, pullreq) <- getPullRequest(baseRepository.owner, baseRepository.name, issueId)
      owner = pullreq.requestUserName
      name = pullreq.requestRepositoryName
      if hasDeveloperRole(owner, name, context.loginAccount)
    } yield {
      updateDraftToPullRequest(baseRepository.owner, baseRepository.name, issueId)
    }) getOrElse NotFound()
  })

  post("/:owner/:repository/pull/:id/merge", mergeForm)(writableUsersOnly { (form, repository) =>
    context.withLoginAccount { loginAccount =>
      params("id").toIntOpt.flatMap { issueId =>
        mergePullRequest(
          repository,
          issueId,
          loginAccount,
          form.message,
          form.strategy,
          form.isDraft,
          context.settings
        ) match {
          case Right(objectId) => redirect(s"/${repository.owner}/${repository.name}/pull/$issueId")
          case Left(message)   => Some(BadRequest(message))
        }
      } getOrElse NotFound()
    }
  })

  get("/:owner/:repository/compare")(referrersOnly { forkedRepository =>
    val headBranch = params.get("head")
    (forkedRepository.repository.originUserName, forkedRepository.repository.originRepositoryName) match {
      case (Some(originUserName), Some(originRepositoryName)) =>
        getRepository(originUserName, originRepositoryName).map { originRepository =>
          Using.resources(
            Git.open(getRepositoryDir(originUserName, originRepositoryName)),
            Git.open(getRepositoryDir(forkedRepository.owner, forkedRepository.name))
          ) { (oldGit, newGit) =>
            val newBranch = headBranch.getOrElse(JGitUtil.getDefaultBranch(newGit, forkedRepository).get._2)
            val oldBranch = originRepository.branchList
              .find(_ == newBranch)
              .getOrElse(JGitUtil.getDefaultBranch(oldGit, originRepository).get._2)

            redirect(
              s"/${forkedRepository.owner}/${forkedRepository.name}/compare/$originUserName:$oldBranch...$newBranch"
            )
          }
        } getOrElse NotFound()
      case _ =>
        Using.resource(Git.open(getRepositoryDir(forkedRepository.owner, forkedRepository.name))) { git =>
          JGitUtil.getDefaultBranch(git, forkedRepository).map { case (_, defaultBranch) =>
            redirect(
              s"/${forkedRepository.owner}/${forkedRepository.name}/compare/$defaultBranch...${headBranch.getOrElse(defaultBranch)}"
            )
          } getOrElse {
            redirect(s"/${forkedRepository.owner}/${forkedRepository.name}")
          }
        }
    }
  })

  private def getOriginRepositoryName(
    originOwner: String,
    forkedOwner: String,
    forkedRepository: RepositoryInfo
  ): Option[String] = {
    if (originOwner == forkedOwner) {
      // Self repository
      Some(forkedRepository.name)
    } else if (forkedRepository.repository.originUserName.isEmpty) {
      // when ForkedRepository is the original repository
      getForkedRepositories(forkedRepository.owner, forkedRepository.name)
        .find(_.userName == originOwner)
        .map(_.repositoryName)
    } else if (forkedRepository.repository.originUserName.contains(originOwner)) {
      // Original repository
      forkedRepository.repository.originRepositoryName
    } else {
      // Sibling repository
      getUserRepositories(originOwner)
        .find { x =>
          x.repository.originUserName == forkedRepository.repository.originUserName &&
          x.repository.originRepositoryName == forkedRepository.repository.originRepositoryName
        }
        .map(_.repository.repositoryName)
    }
  }

  get("/:owner/:repository/compare/*...*")(referrersOnly { forkedRepository =>
    val Seq(origin, forked) = multiParams("splat")
    val (originOwner, originId) = parseCompareIdentifier(origin, forkedRepository.owner)
    val (forkedOwner, forkedId) = parseCompareIdentifier(forked, forkedRepository.owner)

    (for (
      originRepositoryName <- getOriginRepositoryName(originOwner, forkedOwner, forkedRepository);
      originRepository <- getRepository(originOwner, originRepositoryName)
    ) yield {
      val (oldId, newId) =
        getPullRequestCommitFromTo(originRepository, forkedRepository, originId, forkedId)

      (oldId, newId) match {
        case (Some(oldId), Some(newId)) =>
          val (commits, diffs) = getRequestCompareInfo(
            originRepository.owner,
            originRepository.name,
            oldId.getName,
            forkedRepository.owner,
            forkedRepository.name,
            newId.getName,
            context.settings
          )

          val title = if (commits.flatten.length == 1) {
            commits.flatten.head.shortMessage
          } else {
            val text = forkedId.replaceAll("[\\-_]", " ")
            text.substring(0, 1).toUpperCase + text.substring(1)
          }

          html.compare(
            title,
            commits,
            diffs,
            ((forkedRepository.repository.originUserName, forkedRepository.repository.originRepositoryName) match {
              case (Some(userName), Some(repositoryName)) =>
                getRepository(userName, repositoryName) match {
                  case Some(x) => x.repository :: getForkedRepositories(userName, repositoryName)
                  case None    => getForkedRepositories(userName, repositoryName)
                }
              case _ =>
                forkedRepository.repository :: getForkedRepositories(forkedRepository.owner, forkedRepository.name)
            }).map { repository =>
              (repository.userName, repository.repositoryName, repository.defaultBranch)
            },
            commits.flatten
              .flatMap(commit =>
                getCommitComments(forkedRepository.owner, forkedRepository.name, commit.id, includePullRequest = false)
              )
              .toList,
            originId,
            forkedId,
            oldId.getName,
            newId.getName,
            getContentTemplate(originRepository, "PULL_REQUEST_TEMPLATE"),
            forkedRepository,
            originRepository,
            forkedRepository,
            hasDeveloperRole(originRepository.owner, originRepository.name, context.loginAccount),
            getAssignableUserNames(originRepository.owner, originRepository.name),
            getMilestones(originRepository.owner, originRepository.name),
            getPriorities(originRepository.owner, originRepository.name),
            getDefaultPriority(originRepository.owner, originRepository.name),
            getLabels(originRepository.owner, originRepository.name),
            getCustomFields(originRepository.owner, originRepository.name).filter(_.enableForPullRequests)
          )
        case (oldId, newId) =>
          redirect(
            s"/${forkedRepository.owner}/${forkedRepository.name}/compare/" +
              s"$originOwner:${oldId.map(_ => originId).getOrElse(originRepository.repository.defaultBranch)}..." +
              s"$forkedOwner:${newId.map(_ => forkedId).getOrElse(forkedRepository.repository.defaultBranch)}"
          )

      }
    }) getOrElse NotFound()
  })

  ajaxGet("/:owner/:repository/diff/:id")(referrersOnly { repository =>
    (for {
      commitId <- params.get("id")
      path <- params.get("path")
      diff <- getSingleDiff(repository.owner, repository.name, commitId, path)
    } yield {
      contentType = formats("json")
      org.json4s.jackson.Serialization.write(
        Map(
          "oldContent" -> diff.oldContent,
          "newContent" -> diff.newContent
        )
      )
    }) getOrElse NotFound()
  })

  ajaxGet("/:owner/:repository/diff/*...*")(referrersOnly { forkedRepository =>
    val Seq(origin, forked) = multiParams("splat")
    val (originOwner, originId) = parseCompareIdentifier(origin, forkedRepository.owner)
    val (forkedOwner, forkedId) = parseCompareIdentifier(forked, forkedRepository.owner)

    (for {
      path <- params.get("path")
      originRepositoryName <- getOriginRepositoryName(originOwner, forkedOwner, forkedRepository)
      originRepository <- getRepository(originOwner, originRepositoryName)
      (oldId, newId) = getPullRequestCommitFromTo(originRepository, forkedRepository, originId, forkedId)
      oldId <- oldId
      newId <- newId
      diff <- getSingleDiff(
        originRepository.owner,
        originRepository.name,
        oldId.getName,
        forkedRepository.owner,
        forkedRepository.name,
        newId.getName,
        path
      )
    } yield {
      contentType = formats("json")
      org.json4s.jackson.Serialization.write(
        Map(
          "oldContent" -> diff.oldContent,
          "newContent" -> diff.newContent
        )
      )
    }) getOrElse NotFound()
  })

  ajaxGet("/:owner/:repository/compare/*...*/mergecheck")(readableUsersOnly { forkedRepository =>
    val Seq(origin, forked) = multiParams("splat")
    val (originOwner, tmpOriginBranch) = parseCompareIdentifier(origin, forkedRepository.owner)
    val (forkedOwner, tmpForkedBranch) = parseCompareIdentifier(forked, forkedRepository.owner)

    (for {
      originRepositoryName <-
        if (originOwner == forkedOwner) {
          Some(forkedRepository.name)
        } else {
          forkedRepository.repository.originRepositoryName.orElse {
            getForkedRepositories(forkedRepository.owner, forkedRepository.name)
              .find(_.userName == originOwner)
              .map(_.repositoryName)
          }
        }
      originRepository <- getRepository(originOwner, originRepositoryName)
    } yield {
      Using.resources(
        Git.open(getRepositoryDir(originRepository.owner, originRepository.name)),
        Git.open(getRepositoryDir(forkedRepository.owner, forkedRepository.name))
      ) { case (oldGit, newGit) =>
        val originBranch = JGitUtil.getDefaultBranch(oldGit, originRepository, tmpOriginBranch).get._2
        val forkedBranch = JGitUtil.getDefaultBranch(newGit, forkedRepository, tmpForkedBranch).get._2
        val conflict = LockUtil.lock(s"${originRepository.owner}/${originRepository.name}") {
          checkConflict(
            originRepository.owner,
            originRepository.name,
            originBranch,
            forkedRepository.owner,
            forkedRepository.name,
            forkedBranch
          )
        }
        html.mergecheck(conflict.isDefined)
      }
    }) getOrElse NotFound()
  })

  post("/:owner/:repository/pulls/new", pullRequestForm)(readableUsersOnly { (form, repository) =>
    context.withLoginAccount { loginAccount =>
      val manageable = isManageable(repository)

      val issueId = insertIssue(
        owner = repository.owner,
        repository = repository.name,
        loginUser = loginAccount.userName,
        title = form.title,
        content = form.content,
        milestoneId = if (manageable) form.milestoneId else None,
        priorityId = if (manageable) form.priorityId else None,
        isPullRequest = true
      )

      createPullRequest(
        originRepository = repository,
        issueId = issueId,
        originBranch = form.targetBranch,
        requestUserName = form.requestUserName,
        requestRepositoryName = form.requestRepositoryName,
        requestBranch = form.requestBranch,
        commitIdFrom = form.commitIdFrom,
        commitIdTo = form.commitIdTo,
        isDraft = form.isDraft,
        loginAccount = loginAccount,
        settings = context.settings
      )

      if (manageable) {
        // insert assignees
        form.assigneeUserNames.foreach { value =>
          value.split(",").foreach { userName =>
            registerIssueAssignee(repository.owner, repository.name, issueId, userName)
          }
        }
        // insert labels
        form.labelNames.foreach { value =>
          val labels = getLabels(repository.owner, repository.name)
          value.split(",").foreach { labelName =>
            labels.find(_.labelName == labelName).map { label =>
              registerIssueLabel(repository.owner, repository.name, issueId, label.labelId)
            }
          }
        }
      }

      redirect(s"/${repository.owner}/${repository.name}/pull/$issueId")
    }
  })

  ajaxGet("/:owner/:repository/pulls/proposals")(readableUsersOnly { repository =>
    val thresholdTime = System.currentTimeMillis() - (1000 * 60 * 60)
    val mailAddresses =
      context.loginAccount.map(x => Seq(x.mailAddress) ++ getAccountExtraMailAddresses(x.userName)).getOrElse(Nil)

    val branches =
      Using.resource(Git.open(getRepositoryDir(repository.owner, repository.name))) { git =>
        JGitUtil
          .getBranches(
            git = git,
            defaultBranch = repository.repository.defaultBranch,
            origin = repository.repository.originUserName.isEmpty
          )
          .filter { x =>
            x.mergeInfo.map(_.ahead).getOrElse(0) > 0 && x.mergeInfo.map(_.behind).getOrElse(0) == 0 &&
            x.commitTime.getTime > thresholdTime &&
            mailAddresses.contains(x.committerEmailAddress)
          }
          .sortBy { br =>
            (br.mergeInfo.isEmpty, br.commitTime)
          }
          .map(_.name)
          .reverse
      }

    val targetRepository = (for {
      parentUserName <- repository.repository.parentUserName
      parentRepoName <- repository.repository.parentRepositoryName
      parentRepository <- getRepository(parentUserName, parentRepoName)
    } yield {
      parentRepository
    }).getOrElse {
      repository
    }

    val proposedBranches = branches.filter { branch =>
      getPullRequestsByRequest(repository.owner, repository.name, branch, None).isEmpty
    }

    html.proposals(proposedBranches, targetRepository, repository)
  })

  private def searchPullRequests(
    repository: RepositoryService.RepositoryInfo,
    condition: IssueSearchCondition,
    page: Int
  ) = {
    // search issues
    val issues = searchIssue(
      condition,
      IssueSearchOption.PullRequests,
      (page - 1) * PullRequestLimit,
      PullRequestLimit,
      repository.owner -> repository.name
    )
    // commit status
    val status = issues.map { issue =>
      issue.commitId.flatMap { commitId =>
        getCommitStatusWithSummary(repository.owner, repository.name, commitId)
      }
    }

    gitbucket.core.issues.html.list(
      "pulls",
      issues.zip(status),
      page,
      getAssignableUserNames(repository.owner, repository.name),
      getMilestones(repository.owner, repository.name),
      getPriorities(repository.owner, repository.name),
      getLabels(repository.owner, repository.name),
      countIssue(condition.copy(state = "open"), IssueSearchOption.PullRequests, repository.owner -> repository.name),
      countIssue(condition.copy(state = "closed"), IssueSearchOption.PullRequests, repository.owner -> repository.name),
      condition,
      repository,
      isEditable(repository),
      isManageable(repository)
    )
  }

  /**
   * Tests whether an logged-in user can manage pull requests.
   */
  private def isManageable(repository: RepositoryInfo)(implicit context: Context): Boolean = {
    hasDeveloperRole(repository.owner, repository.name, context.loginAccount)
  }

  /**
   * Tests whether an logged-in user can post pull requests.
   */
  private def isEditable(repository: RepositoryInfo)(implicit context: Context): Boolean = {
    repository.repository.options.issuesOption match {
      case "ALL"     => !repository.repository.isPrivate && context.loginAccount.isDefined
      case "PUBLIC"  => hasGuestRole(repository.owner, repository.name, context.loginAccount)
      case "PRIVATE" => hasDeveloperRole(repository.owner, repository.name, context.loginAccount)
      case "DISABLE" => false
    }
  }

}
