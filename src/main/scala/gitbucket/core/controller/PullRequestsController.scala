package gitbucket.core.controller

import gitbucket.core.pulls.html
import gitbucket.core.service.CommitStatusService
import gitbucket.core.service.MergeService
import gitbucket.core.service.IssuesService._
import gitbucket.core.service.PullRequestService._
import gitbucket.core.service.RepositoryService.RepositoryInfo
import gitbucket.core.service._
import gitbucket.core.util.SyntaxSugars._
import gitbucket.core.util.Directory._
import gitbucket.core.util.Implicits._
import gitbucket.core.util._
import org.scalatra.forms._
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

trait PullRequestsControllerBase extends ControllerBase {
  self: RepositoryService
    with AccountService
    with IssuesService
    with MilestonesService
    with LabelsService
    with CommitsService
    with ActivityService
    with PullRequestService
    with WebHookPullRequestService
    with ReadableUsersAuthenticator
    with ReferrerAuthenticator
    with WritableUsersAuthenticator
    with CommitStatusService
    with MergeService
    with ProtectedBranchService
    with PrioritiesService =>

  val pullRequestForm = mapping(
    "title" -> trim(label("Title", text(required, maxlength(100)))),
    "content" -> trim(label("Content", optional(text()))),
    "targetUserName" -> trim(text(required, maxlength(100))),
    "targetBranch" -> trim(text(required, maxlength(100))),
    "requestUserName" -> trim(text(required, maxlength(100))),
    "requestRepositoryName" -> trim(text(required, maxlength(100))),
    "requestBranch" -> trim(text(required, maxlength(100))),
    "commitIdFrom" -> trim(text(required, maxlength(40))),
    "commitIdTo" -> trim(text(required, maxlength(40))),
    "isDraft" -> trim(boolean(required)),
    "assignedUserName" -> trim(optional(text())),
    "milestoneId" -> trim(optional(number())),
    "priorityId" -> trim(optional(number())),
    "labelNames" -> trim(optional(text()))
  )(PullRequestForm.apply)

  val mergeForm = mapping(
    "message" -> trim(label("Message", text(required))),
    "strategy" -> trim(label("Strategy", text(required))),
    "isDraft" -> trim(boolean(required))
  )(MergeForm.apply)

  case class PullRequestForm(
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
    assignedUserName: Option[String],
    milestoneId: Option[Int],
    priorityId: Option[Int],
    labelNames: Option[String]
  )

  case class MergeForm(message: String, strategy: String, isDraft: Boolean)

  get("/:owner/:repository/pulls")(referrersOnly { repository =>
    val q = request.getParameter("q")
    if (Option(q).exists(_.contains("is:issue"))) {
      redirect(s"/${repository.owner}/${repository.name}/issues?q=" + StringUtil.urlEncode(q))
    } else {
      searchPullRequests(None, repository)
    }
  })

  get("/:owner/:repository/pull/:id")(referrersOnly { repository =>
    params("id").toIntOpt.flatMap {
      issueId =>
        val owner = repository.owner
        val name = repository.name
        getPullRequest(owner, name, issueId) map {
          case (issue, pullreq) =>
            val (commits, diffs) =
              getRequestCompareInfo(owner, name, pullreq.commitIdFrom, owner, name, pullreq.commitIdTo)

            html.conversation(
              issue,
              pullreq,
              commits.flatten,
              getPullRequestComments(owner, name, issue.issueId, commits.flatten),
              diffs.size,
              getIssueLabels(owner, name, issueId),
              getAssignableUserNames(owner, name),
              getMilestonesWithIssueCount(owner, name),
              getPriorities(owner, name),
              getLabels(owner, name),
              isEditable(repository),
              isManageable(repository),
              hasDeveloperRole(pullreq.requestUserName, pullreq.requestRepositoryName, context.loginAccount),
              repository,
              getRepository(pullreq.requestUserName, pullreq.requestRepositoryName),
              flash.iterator.map(f => f._1 -> f._2.toString).toMap
            )

//                html.pullreq(
//                  issue,
//                  pullreq,
//                  comments,
//                  getIssueLabels(owner, name, issueId),
//                  getAssignableUserNames(owner, name),
//                  getMilestonesWithIssueCount(owner, name),
//                  getPriorities(owner, name),
//                  getLabels(owner, name),
//                  commits,
//                  diffs,
//                  isEditable(repository),
//                  isManageable(repository),
//                  hasDeveloperRole(pullreq.requestUserName, pullreq.requestRepositoryName, context.loginAccount),
//                  repository,
//                  getRepository(pullreq.requestUserName, pullreq.requestRepositoryName),
//                  flash.toMap.map(f => f._1 -> f._2.toString)
//                )
        }
    } getOrElse NotFound()
  })

  get("/:owner/:repository/pull/:id/commits")(referrersOnly { repository =>
    params("id").toIntOpt.flatMap {
      issueId =>
        val owner = repository.owner
        val name = repository.name
        getPullRequest(owner, name, issueId) map {
          case (issue, pullreq) =>
            val (commits, diffs) =
              getRequestCompareInfo(owner, name, pullreq.commitIdFrom, owner, name, pullreq.commitIdTo)

            html.commits(
              issue,
              pullreq,
              commits,
              getPullRequestComments(owner, name, issue.issueId, commits.flatten),
              diffs.size,
              isManageable(repository),
              repository
            )
        }
    } getOrElse NotFound()
  })

  get("/:owner/:repository/pull/:id/files")(referrersOnly { repository =>
    params("id").toIntOpt.flatMap {
      issueId =>
        val owner = repository.owner
        val name = repository.name
        getPullRequest(owner, name, issueId) map {
          case (issue, pullreq) =>
            val (commits, diffs) =
              getRequestCompareInfo(owner, name, pullreq.commitIdFrom, owner, name, pullreq.commitIdTo)

            html.files(
              issue,
              pullreq,
              diffs,
              commits.flatten,
              getPullRequestComments(owner, name, issue.issueId, commits.flatten),
              isManageable(repository),
              repository
            )
        }
    } getOrElse NotFound()
  })

  ajaxGet("/:owner/:repository/pull/:id/mergeguide")(referrersOnly { repository =>
    params("id").toIntOpt.flatMap {
      issueId =>
        val owner = repository.owner
        val name = repository.name
        getPullRequest(owner, name, issueId) map {
          case (issue, pullreq) =>
            val conflictMessage = LockUtil.lock(s"${owner}/${name}") {
              checkConflict(owner, name, pullreq.branch, issueId)
            }
            val hasMergePermission = hasDeveloperRole(owner, name, context.loginAccount)
            val branchProtection = getProtectedBranchInfo(owner, name, pullreq.branch)
            val mergeStatus = PullRequestService.MergeStatus(
              conflictMessage = conflictMessage,
              commitStatues = getCommitStatues(owner, name, pullreq.commitIdTo),
              branchProtection = branchProtection,
              branchIsOutOfDate = JGitUtil.getShaByRef(owner, name, pullreq.branch) != Some(pullreq.commitIdFrom),
              needStatusCheck = context.loginAccount
                .map { u =>
                  branchProtection.needStatusCheck(u.userName)
                }
                .getOrElse(true),
              hasUpdatePermission = hasDeveloperRole(
                pullreq.requestUserName,
                pullreq.requestRepositoryName,
                context.loginAccount
              ) &&
                context.loginAccount
                  .map { u =>
                    !getProtectedBranchInfo(
                      pullreq.requestUserName,
                      pullreq.requestRepositoryName,
                      pullreq.requestBranch
                    ).needStatusCheck(u.userName)
                  }
                  .getOrElse(false),
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
      (issue, pullreq) <- getPullRequest(baseRepository.owner, baseRepository.name, issueId)
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
            recordDeleteBranchActivity(repository.owner, repository.name, userName, pullreq.requestBranch)
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
      (issue, pullreq) <- getPullRequest(baseRepository.owner, baseRepository.name, issueId)
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
        LockUtil.lock(s"${owner}/${name}") {
          val alias =
            if (pullreq.repositoryName == pullreq.requestRepositoryName && pullreq.userName == pullreq.requestUserName) {
              pullreq.branch
            } else {
              s"${pullreq.userName}:${pullreq.branch}"
            }
          val existIds = Using
            .resource(Git.open(Directory.getRepositoryDir(owner, name))) { git =>
              JGitUtil.getAllCommitIds(git)
            }
            .toSet
          pullRemote(
            repository,
            pullreq.requestBranch,
            remoteRepository,
            pullreq.branch,
            loginAccount,
            s"Merge branch '${alias}' into ${pullreq.requestBranch}",
            Some(pullreq),
            context.settings
          ) match {
            case None => // conflict
              flash.update("error", s"Can't automatic merging branch '${alias}' into ${pullreq.requestBranch}.")
            case Some(oldId) =>
              // update pull request
              updatePullRequests(owner, name, pullreq.requestBranch, loginAccount, "synchronize", context.settings)
              flash.update("info", s"Merge branch '${alias}' into ${pullreq.requestBranch}")
          }
        }
      }
      redirect(s"/${baseRepository.owner}/${baseRepository.name}/pull/${issueId}")

    }) getOrElse NotFound()
  })

  post("/:owner/:repository/pull/:id/update_draft")(readableUsersOnly { baseRepository =>
    (for {
      issueId <- params("id").toIntOpt
      (_, pullreq) <- getPullRequest(baseRepository.owner, baseRepository.name, issueId)
      owner = pullreq.requestUserName
      name = pullreq.requestRepositoryName
      if hasDeveloperRole(owner, name, context.loginAccount)
    } yield {
      updateDraftToPullRequest(baseRepository.owner, baseRepository.name, issueId)
    }) getOrElse NotFound()
  })

  post("/:owner/:repository/pull/:id/merge", mergeForm)(writableUsersOnly { (form, repository) =>
    params("id").toIntOpt.flatMap { issueId =>
      val owner = repository.owner
      val name = repository.name

      mergePullRequest(
        repository,
        issueId,
        context.loginAccount.get,
        form.message,
        form.strategy,
        form.isDraft,
        context.settings
      ) match {
        case Right(objectId) => redirect(s"/${owner}/${name}/pull/${issueId}")
        case Left(message)   => Some(BadRequest(message))
      }
    } getOrElse NotFound()
  })

  get("/:owner/:repository/compare")(referrersOnly { forkedRepository =>
    val headBranch: Option[String] = params.get("head")
    (forkedRepository.repository.originUserName, forkedRepository.repository.originRepositoryName) match {
      case (Some(originUserName), Some(originRepositoryName)) => {
        getRepository(originUserName, originRepositoryName).map {
          originRepository =>
            Using.resources(
              Git.open(getRepositoryDir(originUserName, originRepositoryName)),
              Git.open(getRepositoryDir(forkedRepository.owner, forkedRepository.name))
            ) { (oldGit, newGit) =>
              val newBranch = headBranch.getOrElse(JGitUtil.getDefaultBranch(newGit, forkedRepository).get._2)
              val oldBranch = originRepository.branchList
                .find(_ == newBranch)
                .getOrElse(JGitUtil.getDefaultBranch(oldGit, originRepository).get._2)

              redirect(
                s"/${forkedRepository.owner}/${forkedRepository.name}/compare/${originUserName}:${oldBranch}...${newBranch}"
              )
            }
        } getOrElse NotFound()
      }
      case _ => {
        Using.resource(Git.open(getRepositoryDir(forkedRepository.owner, forkedRepository.name))) { git =>
          JGitUtil.getDefaultBranch(git, forkedRepository).map {
            case (_, defaultBranch) =>
              redirect(
                s"/${forkedRepository.owner}/${forkedRepository.name}/compare/${defaultBranch}...${headBranch.getOrElse(defaultBranch)}"
              )
          } getOrElse {
            redirect(s"/${forkedRepository.owner}/${forkedRepository.name}")
          }
        }
      }
    }
  })

  get("/:owner/:repository/compare/*...*")(referrersOnly { forkedRepository =>
    val Seq(origin, forked) = multiParams("splat")
    val (originOwner, originId) = parseCompareIdentifier(origin, forkedRepository.owner)
    val (forkedOwner, forkedId) = parseCompareIdentifier(forked, forkedRepository.owner)

    (for (originRepositoryName <- if (originOwner == forkedOwner) {
            // Self repository
            Some(forkedRepository.name)
          } else if (forkedRepository.repository.originUserName.isEmpty) {
            // when ForkedRepository is the original repository
            getForkedRepositories(forkedRepository.owner, forkedRepository.name)
              .find(_.userName == originOwner)
              .map(_.repositoryName)
          } else if (Some(originOwner) == forkedRepository.repository.originUserName) {
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
          };
          originRepository <- getRepository(originOwner, originRepositoryName)) yield {
      val (oldId, newId) =
        getPullRequestCommitFromTo(originRepository, forkedRepository, originId, forkedId)

      (oldId, newId) match {
        case (Some(oldId), Some(newId)) => {
          val (commits, diffs) = getRequestCompareInfo(
            originRepository.owner,
            originRepository.name,
            oldId.getName,
            forkedRepository.owner,
            forkedRepository.name,
            newId.getName
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
              .map(commit => getCommitComments(forkedRepository.owner, forkedRepository.name, commit.id, false))
              .flatten
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
            getLabels(originRepository.owner, originRepository.name)
          )
        }
        case (oldId, newId) =>
          redirect(
            s"/${forkedRepository.owner}/${forkedRepository.name}/compare/" +
              s"${originOwner}:${oldId.map(_ => originId).getOrElse(originRepository.repository.defaultBranch)}..." +
              s"${forkedOwner}:${newId.map(_ => forkedId).getOrElse(forkedRepository.repository.defaultBranch)}"
          )

      }
    }) getOrElse NotFound()
  })

  ajaxGet("/:owner/:repository/compare/*...*/mergecheck")(readableUsersOnly { forkedRepository =>
    val Seq(origin, forked) = multiParams("splat")
    val (originOwner, tmpOriginBranch) = parseCompareIdentifier(origin, forkedRepository.owner)
    val (forkedOwner, tmpForkedBranch) = parseCompareIdentifier(forked, forkedRepository.owner)

    (for (originRepositoryName <- if (originOwner == forkedOwner) {
            Some(forkedRepository.name)
          } else {
            forkedRepository.repository.originRepositoryName.orElse {
              getForkedRepositories(forkedRepository.owner, forkedRepository.name)
                .find(_.userName == originOwner)
                .map(_.repositoryName)
            }
          };
          originRepository <- getRepository(originOwner, originRepositoryName)) yield {
      Using.resources(
        Git.open(getRepositoryDir(originRepository.owner, originRepository.name)),
        Git.open(getRepositoryDir(forkedRepository.owner, forkedRepository.name))
      ) {
        case (oldGit, newGit) =>
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
    defining(repository.owner, repository.name) {
      case (owner, name) =>
        val manageable = isManageable(repository)
        val loginUserName = context.loginAccount.get.userName

        val issueId = insertIssue(
          owner = repository.owner,
          repository = repository.name,
          loginUser = loginUserName,
          title = form.title,
          content = form.content,
          assignedUserName = if (manageable) form.assignedUserName else None,
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
          loginAccount = context.loginAccount.get,
          settings = context.settings
        )

        // insert labels
        if (manageable) {
          form.labelNames.foreach { value =>
            val labels = getLabels(owner, name)
            value.split(",").foreach { labelName =>
              labels.find(_.labelName == labelName).map { label =>
                registerIssueLabel(repository.owner, repository.name, issueId, label.labelId)
              }
            }
          }
        }

        redirect(s"/${owner}/${name}/pull/${issueId}")
    }
  })

  ajaxGet("/:owner/:repository/pulls/proposals")(readableUsersOnly { repository =>
    val thresholdTime = System.currentTimeMillis() - (1000 * 60 * 60)
    val mailAddresses =
      context.loginAccount.map(x => Seq(x.mailAddress) ++ getAccountExtraMailAddresses(x.userName)).getOrElse(Nil)

    val branches =
      Using.resource(Git.open(getRepositoryDir(repository.owner, repository.name))) {
        git =>
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

  private def searchPullRequests(userName: Option[String], repository: RepositoryService.RepositoryInfo) =
    defining(repository.owner, repository.name) {
      case (owner, repoName) =>
        val page = IssueSearchCondition.page(request)

        // retrieve search condition
        val condition = IssueSearchCondition(request)

        gitbucket.core.issues.html.list(
          "pulls",
          searchIssue(condition, true, (page - 1) * PullRequestLimit, PullRequestLimit, owner -> repoName),
          page,
          getAssignableUserNames(owner, repoName),
          getMilestones(owner, repoName),
          getPriorities(owner, repoName),
          getLabels(owner, repoName),
          countIssue(condition.copy(state = "open"), true, owner -> repoName),
          countIssue(condition.copy(state = "closed"), true, owner -> repoName),
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
