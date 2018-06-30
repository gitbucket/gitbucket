package gitbucket.core.controller

import gitbucket.core.model.{CommitComment, CommitComments, IssueComment, WebHook}
import gitbucket.core.plugin.PluginRegistry
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
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.revwalk.RevWalk
import org.scalatra.BadRequest

import scala.collection.JavaConverters._

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
    "assignedUserName" -> trim(optional(text())),
    "milestoneId" -> trim(optional(number())),
    "priorityId" -> trim(optional(number())),
    "labelNames" -> trim(optional(text()))
  )(PullRequestForm.apply)

  val mergeForm = mapping(
    "message" -> trim(label("Message", text(required))),
    "strategy" -> trim(label("Strategy", text(required)))
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
    assignedUserName: Option[String],
    milestoneId: Option[Int],
    priorityId: Option[Int],
    labelNames: Option[String]
  )

  case class MergeForm(message: String, strategy: String)

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
              flash.toMap.map(f => f._1 -> f._2.toString)
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
        flash += "error" -> s"branch ${pullreq.requestBranch} is protected."
      } else {
        if (repository.repository.defaultBranch != pullreq.requestBranch) {
          val userName = context.loginAccount.get.userName
          using(Git.open(getRepositoryDir(repository.owner, repository.name))) { git =>
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
          flash += "error" -> s"""Can't delete the default branch "${pullreq.requestBranch}"."""
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
      owner = pullreq.requestUserName
      name = pullreq.requestRepositoryName
      if hasDeveloperRole(owner, name, context.loginAccount)
    } yield {
      val repository = getRepository(owner, name).get
      val branchProtection = getProtectedBranchInfo(owner, name, pullreq.requestBranch)
      if (branchProtection.needStatusCheck(loginAccount.userName)) {
        flash += "error" -> s"branch ${pullreq.requestBranch} is protected need status check."
      } else {
        LockUtil.lock(s"${owner}/${name}") {
          val alias =
            if (pullreq.repositoryName == pullreq.requestRepositoryName && pullreq.userName == pullreq.requestUserName) {
              pullreq.branch
            } else {
              s"${pullreq.userName}:${pullreq.branch}"
            }
          val existIds = using(Git.open(Directory.getRepositoryDir(owner, name))) { git =>
            JGitUtil.getAllCommitIds(git)
          }.toSet
          pullRemote(
            owner,
            name,
            pullreq.requestBranch,
            pullreq.userName,
            pullreq.repositoryName,
            pullreq.branch,
            loginAccount,
            s"Merge branch '${alias}' into ${pullreq.requestBranch}"
          ) match {
            case None => // conflict
              flash += "error" -> s"Can't automatic merging branch '${alias}' into ${pullreq.requestBranch}."
            case Some(oldId) =>
              // update pull request
              updatePullRequests(owner, name, pullreq.requestBranch)

              using(Git.open(Directory.getRepositoryDir(owner, name))) {
                git =>
                  //  after update branch
                  val newCommitId = git.getRepository.resolve(s"refs/heads/${pullreq.requestBranch}")
                  val commits = git.log
                    .addRange(oldId, newCommitId)
                    .call
                    .iterator
                    .asScala
                    .map(c => new JGitUtil.CommitInfo(c))
                    .toList

                  commits.foreach { commit =>
                    if (!existIds.contains(commit.id)) {
                      createIssueComment(owner, name, commit)
                    }
                  }

                  // record activity
                  recordPushActivity(owner, name, loginAccount.userName, pullreq.branch, commits)

                  // close issue by commit message
                  if (pullreq.requestBranch == repository.repository.defaultBranch) {
                    commits.map { commit =>
                      closeIssuesFromMessage(commit.fullMessage, loginAccount.userName, owner, name).foreach {
                        issueId =>
                          getIssue(repository.owner, repository.name, issueId.toString).map { issue =>
                            callIssuesWebHook("closed", repository, issue, baseUrl, loginAccount)
                            PluginRegistry().getIssueHooks
                              .foreach(
                                _.closedByCommitComment(issue, repository, commit.fullMessage, loginAccount)
                              )
                          }
                      }
                    }
                  }

                  // call web hook
                  callPullRequestWebHookByRequestBranch(
                    "synchronize",
                    repository,
                    pullreq.requestBranch,
                    baseUrl,
                    loginAccount
                  )
                  callWebHookOf(owner, name, WebHook.Push) {
                    for {
                      ownerAccount <- getAccountByUserName(owner)
                    } yield {
                      WebHookService.WebHookPushPayload(
                        git,
                        loginAccount,
                        pullreq.requestBranch,
                        repository,
                        commits,
                        ownerAccount,
                        oldId = oldId,
                        newId = newCommitId
                      )
                    }
                  }
              }
              flash += "info" -> s"Merge branch '${alias}' into ${pullreq.requestBranch}"
          }
        }
      }
      redirect(s"/${baseRepository.owner}/${baseRepository.name}/pull/${issueId}")

    }) getOrElse NotFound()
  })

  post("/:owner/:repository/pull/:id/merge", mergeForm)(writableUsersOnly { (form, repository) =>
    params("id").toIntOpt.flatMap {
      issueId =>
        val owner = repository.owner
        val name = repository.name
        if (repository.repository.options.mergeOptions.split(",").contains(form.strategy)) {
          LockUtil.lock(s"${owner}/${name}") {
            getPullRequest(owner, name, issueId).map {
              case (issue, pullreq) =>
                using(Git.open(getRepositoryDir(owner, name))) {
                  git =>
                    // mark issue as merged and close.
                    val loginAccount = context.loginAccount.get
                    val commentId = createComment(owner, name, loginAccount.userName, issueId, form.message, "merge")
                    createComment(owner, name, loginAccount.userName, issueId, "Close", "close")
                    updateClosed(owner, name, issueId, true)

                    // record activity
                    recordMergeActivity(owner, name, loginAccount.userName, issueId, form.message)

                    val (commits, _) = getRequestCompareInfo(
                      owner,
                      name,
                      pullreq.commitIdFrom,
                      pullreq.requestUserName,
                      pullreq.requestRepositoryName,
                      pullreq.commitIdTo
                    )

                    val revCommits = using(new RevWalk(git.getRepository)) { revWalk =>
                      commits.flatten.map { commit =>
                        revWalk.parseCommit(git.getRepository.resolve(commit.id))
                      }
                    }.reverse

                    // merge git repository
                    form.strategy match {
                      case "merge-commit" =>
                        mergePullRequest(
                          git,
                          pullreq.branch,
                          issueId,
                          s"Merge pull request #${issueId} from ${pullreq.requestUserName}/${pullreq.requestBranch}\n\n" + form.message,
                          new PersonIdent(loginAccount.fullName, loginAccount.mailAddress)
                        )
                      case "rebase" =>
                        rebasePullRequest(
                          git,
                          pullreq.branch,
                          issueId,
                          revCommits,
                          new PersonIdent(loginAccount.fullName, loginAccount.mailAddress)
                        )
                      case "squash" =>
                        squashPullRequest(
                          git,
                          pullreq.branch,
                          issueId,
                          s"${issue.title} (#${issueId})\n\n" + form.message,
                          new PersonIdent(loginAccount.fullName, loginAccount.mailAddress)
                        )
                    }

                    // close issue by content of pull request
                    val defaultBranch = getRepository(owner, name).get.repository.defaultBranch
                    if (pullreq.branch == defaultBranch) {
                      commits.flatten.foreach { commit =>
                        closeIssuesFromMessage(commit.fullMessage, loginAccount.userName, owner, name).foreach {
                          issueId =>
                            getIssue(owner, name, issueId.toString).map { issue =>
                              callIssuesWebHook("closed", repository, issue, baseUrl, loginAccount)
                              PluginRegistry().getIssueHooks
                                .foreach(_.closedByCommitComment(issue, repository, commit.fullMessage, loginAccount))
                            }
                        }
                      }
                      val issueContent = issue.title + " " + issue.content.getOrElse("")
                      closeIssuesFromMessage(
                        issueContent,
                        loginAccount.userName,
                        owner,
                        name
                      ).foreach { issueId =>
                        getIssue(owner, name, issueId.toString).map { issue =>
                          callIssuesWebHook("closed", repository, issue, baseUrl, loginAccount)
                          PluginRegistry().getIssueHooks
                            .foreach(_.closedByCommitComment(issue, repository, issueContent, loginAccount))
                        }
                      }
                      closeIssuesFromMessage(form.message, loginAccount.userName, owner, name).foreach { issueId =>
                        getIssue(owner, name, issueId.toString).map { issue =>
                          callIssuesWebHook("closed", repository, issue, baseUrl, loginAccount)
                          PluginRegistry().getIssueHooks
                            .foreach(_.closedByCommitComment(issue, repository, issueContent, loginAccount))
                        }
                      }
                    }

                    updatePullRequests(owner, name, pullreq.branch)

                    // call web hook
                    callPullRequestWebHook("closed", repository, issueId, context.baseUrl, context.loginAccount.get)

                    // call hooks
                    PluginRegistry().getPullRequestHooks.foreach { h =>
                      h.addedComment(commentId, form.message, issue, repository)
                      h.merged(issue, repository)
                    }

                    redirect(s"/${owner}/${name}/pull/${issueId}")
                }
            }
          }
        } else Some(BadRequest())
    } getOrElse NotFound()
  })

  get("/:owner/:repository/compare")(referrersOnly { forkedRepository =>
    val headBranch: Option[String] = params.get("head")
    (forkedRepository.repository.originUserName, forkedRepository.repository.originRepositoryName) match {
      case (Some(originUserName), Some(originRepositoryName)) => {
        getRepository(originUserName, originRepositoryName).map {
          originRepository =>
            using(
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
        using(Git.open(getRepositoryDir(forkedRepository.owner, forkedRepository.name))) { git =>
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
      using(
        Git.open(getRepositoryDir(originRepository.owner, originRepository.name)),
        Git.open(getRepositoryDir(forkedRepository.owner, forkedRepository.name))
      ) {
        case (oldGit, newGit) =>
          val (oldId, newId) =
            if (originRepository.branchList.contains(originId)) {
              val forkedId2 =
                forkedRepository.tags.collectFirst { case x if x.name == forkedId => x.id }.getOrElse(forkedId)

              val originId2 = JGitUtil.getForkedCommitId(
                oldGit,
                newGit,
                originRepository.owner,
                originRepository.name,
                originId,
                forkedRepository.owner,
                forkedRepository.name,
                forkedId2
              )

              (Option(oldGit.getRepository.resolve(originId2)), Option(newGit.getRepository.resolve(forkedId2)))

            } else {
              val originId2 =
                originRepository.tags.collectFirst { case x if x.name == originId => x.id }.getOrElse(originId)
              val forkedId2 =
                forkedRepository.tags.collectFirst { case x if x.name == forkedId => x.id }.getOrElse(forkedId)

              (Option(oldGit.getRepository.resolve(originId2)), Option(newGit.getRepository.resolve(forkedId2)))
            }

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
                  (repository.userName, repository.repositoryName)
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
      using(
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
          originUserName = repository.owner,
          originRepositoryName = repository.name,
          issueId = issueId,
          originBranch = form.targetBranch,
          requestUserName = form.requestUserName,
          requestRepositoryName = form.requestRepositoryName,
          requestBranch = form.requestBranch,
          commitIdFrom = form.commitIdFrom,
          commitIdTo = form.commitIdTo
        )

        // insert labels
        if (manageable) {
          form.labelNames.map { value =>
            val labels = getLabels(owner, name)
            value.split(",").foreach { labelName =>
              labels.find(_.labelName == labelName).map { label =>
                registerIssueLabel(repository.owner, repository.name, issueId, label.labelId)
              }
            }
          }
        }

        // fetch requested branch
        fetchAsPullRequest(owner, name, form.requestUserName, form.requestRepositoryName, form.requestBranch, issueId)

        // record activity
        recordPullRequestActivity(owner, name, loginUserName, issueId, form.title)

        // call web hook
        callPullRequestWebHook("opened", repository, issueId, context.baseUrl, context.loginAccount.get)

        getIssue(owner, name, issueId.toString) foreach { issue =>
          // extract references and create refer comment
          createReferComment(
            owner,
            name,
            issue,
            form.title + " " + form.content.getOrElse(""),
            context.loginAccount.get
          )

          // call hooks
          PluginRegistry().getPullRequestHooks.foreach(_.created(issue, repository))
        }

        redirect(s"/${owner}/${name}/pull/${issueId}")
    }
  })

  ajaxGet("/:owner/:repository/pulls/proposals")(readableUsersOnly { repository =>
    val thresholdTime = System.currentTimeMillis() - (1000 * 60 * 60)
    val mailAddresses =
      context.loginAccount.map(x => Seq(x.mailAddress) ++ getAccountExtraMailAddresses(x.userName)).getOrElse(Nil)

    val branches = JGitUtil
      .getBranches(
        owner = repository.owner,
        name = repository.name,
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

  /**
   * Parses branch identifier and extracts owner and branch name as tuple.
   *
   * - "owner:branch" to ("owner", "branch")
   * - "branch" to ("defaultOwner", "branch")
   */
  private def parseCompareIdentifier(value: String, defaultOwner: String): (String, String) =
    if (value.contains(':')) {
      val array = value.split(":")
      (array(0), array(1))
    } else {
      (defaultOwner, value)
    }

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
