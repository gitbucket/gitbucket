package gitbucket.core.controller

import gitbucket.core.model.WebHook
import gitbucket.core.pulls.html
import gitbucket.core.service.CommitStatusService
import gitbucket.core.service.MergeService
import gitbucket.core.service.IssuesService._
import gitbucket.core.service.PullRequestService._
import gitbucket.core.service._
import gitbucket.core.util.ControlUtil._
import gitbucket.core.util.Directory._
import gitbucket.core.util.Implicits._
import gitbucket.core.util.JGitUtil._
import gitbucket.core.util._
import gitbucket.core.view
import gitbucket.core.view.helpers

import io.github.gitbucket.scalatra.forms._
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.PersonIdent
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._


class PullRequestsController extends PullRequestsControllerBase
  with RepositoryService with AccountService with IssuesService with PullRequestService with MilestonesService with LabelsService
  with CommitsService with ActivityService with WebHookPullRequestService with ReferrerAuthenticator with CollaboratorsAuthenticator
  with CommitStatusService with MergeService with ProtectedBranchService


trait PullRequestsControllerBase extends ControllerBase {
  self: RepositoryService with AccountService with IssuesService with MilestonesService with LabelsService
    with CommitsService with ActivityService with PullRequestService with WebHookPullRequestService with ReferrerAuthenticator with CollaboratorsAuthenticator
    with CommitStatusService with MergeService with ProtectedBranchService =>

  private val logger = LoggerFactory.getLogger(classOf[PullRequestsControllerBase])

  val pullRequestForm = mapping(
    "title"                 -> trim(label("Title"  , text(required, maxlength(100)))),
    "content"               -> trim(label("Content", optional(text()))),
    "targetUserName"        -> trim(text(required, maxlength(100))),
    "targetBranch"          -> trim(text(required, maxlength(100))),
    "requestUserName"       -> trim(text(required, maxlength(100))),
    "requestRepositoryName" -> trim(text(required, maxlength(100))),
    "requestBranch"         -> trim(text(required, maxlength(100))),
    "commitIdFrom"          -> trim(text(required, maxlength(40))),
    "commitIdTo"            -> trim(text(required, maxlength(40))),
    "assignedUserName"      -> trim(optional(text())),
    "milestoneId"           -> trim(optional(number())),
    "labelNames"            -> trim(optional(text()))
  )(PullRequestForm.apply)

  val mergeForm = mapping(
    "message" -> trim(label("Message", text(required)))
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
    labelNames: Option[String]
  )

  case class MergeForm(message: String)

  get("/:owner/:repository/pulls")(referrersOnly { repository =>
    val q = request.getParameter("q")
    if(Option(q).exists(_.contains("is:issue"))){
      redirect(s"/${repository.owner}/${repository.name}/issues?q=" + StringUtil.urlEncode(q))
    } else {
      searchPullRequests(None, repository)
    }
  })

  get("/:owner/:repository/pull/:id")(referrersOnly { repository =>
    params("id").toIntOpt.flatMap{ issueId =>
      val owner = repository.owner
      val name = repository.name
      getPullRequest(owner, name, issueId) map { case(issue, pullreq) =>
        using(Git.open(getRepositoryDir(owner, name))){ git =>
          val (commits, diffs) =
            getRequestCompareInfo(owner, name, pullreq.commitIdFrom, owner, name, pullreq.commitIdTo)
          html.pullreq(
            issue, pullreq,
            (commits.flatten.map(commit => getCommitComments(owner, name, commit.id, true)).flatten.toList ::: getComments(owner, name, issueId))
              .sortWith((a, b) => a.registeredDate before b.registeredDate),
            getIssueLabels(owner, name, issueId),
            (getCollaborators(owner, name) ::: (if(getAccountByUserName(owner).get.isGroupAccount) Nil else List(owner))).sorted,
            getMilestonesWithIssueCount(owner, name),
            getLabels(owner, name),
            commits,
            diffs,
            hasWritePermission(owner, name, context.loginAccount),
            repository,
            flash.toMap.map(f => f._1 -> f._2.toString))
        }
      }
    } getOrElse NotFound
  })

  ajaxGet("/:owner/:repository/pull/:id/mergeguide")(referrersOnly { repository =>
    params("id").toIntOpt.flatMap{ issueId =>
      val owner = repository.owner
      val name  = repository.name
      getPullRequest(owner, name, issueId) map { case(issue, pullreq) =>
        val hasConflict = LockUtil.lock(s"${owner}/${name}"){
          checkConflict(owner, name, pullreq.branch, issueId)
        }
        val hasMergePermission = hasWritePermission(owner, name, context.loginAccount)
        val branchProtection = getProtectedBranchInfo(owner, name, pullreq.branch)
        val mergeStatus = PullRequestService.MergeStatus(
           hasConflict         = hasConflict,
           commitStatues       = getCommitStatues(owner, name, pullreq.commitIdTo),
           branchProtection    = branchProtection,
           branchIsOutOfDate   = JGitUtil.getShaByRef(owner, name, pullreq.branch) != Some(pullreq.commitIdFrom),
           needStatusCheck     = context.loginAccount.map{ u =>
                                   branchProtection.needStatusCheck(u.userName)
                                 }.getOrElse(true),
           hasUpdatePermission = hasWritePermission(pullreq.requestUserName, pullreq.requestRepositoryName, context.loginAccount) &&
                                   context.loginAccount.map{ u =>
                                     !getProtectedBranchInfo(pullreq.requestUserName, pullreq.requestRepositoryName, pullreq.requestBranch).needStatusCheck(u.userName)
                                   }.getOrElse(false),
           hasMergePermission  = hasMergePermission,
           commitIdTo          = pullreq.commitIdTo)
        html.mergeguide(
          mergeStatus,
          issue,
          pullreq,
          repository,
          getRepository(pullreq.requestUserName, pullreq.requestRepositoryName).get)
      }
    } getOrElse NotFound
  })

  get("/:owner/:repository/pull/:id/delete/*")(collaboratorsOnly { repository =>
    params("id").toIntOpt.map { issueId =>
      val branchName = multiParams("splat").head
      val userName   = context.loginAccount.get.userName
      if(repository.repository.defaultBranch != branchName){
        using(Git.open(getRepositoryDir(repository.owner, repository.name))){ git =>
          git.branchDelete().setForce(true).setBranchNames(branchName).call()
          recordDeleteBranchActivity(repository.owner, repository.name, userName, branchName)
        }
      }
      createComment(repository.owner, repository.name, userName, issueId, branchName, "delete_branch")
      redirect(s"/${repository.owner}/${repository.name}/pull/${issueId}")
    } getOrElse NotFound
  })

  post("/:owner/:repository/pull/:id/update_branch")(referrersOnly { baseRepository =>
    (for {
      issueId <- params("id").toIntOpt
      loginAccount <- context.loginAccount
      (issue, pullreq) <- getPullRequest(baseRepository.owner, baseRepository.name, issueId)
      owner = pullreq.requestUserName
      name  = pullreq.requestRepositoryName
      if hasWritePermission(owner, name, context.loginAccount)
    } yield {
      val branchProtection = getProtectedBranchInfo(owner, name, pullreq.requestBranch)
      if(branchProtection.needStatusCheck(loginAccount.userName)){
        flash += "error" -> s"branch ${pullreq.requestBranch} is protected need status check."
      } else {
        val repository = getRepository(owner, name).get
        LockUtil.lock(s"${owner}/${name}"){
          val alias = if(pullreq.repositoryName == pullreq.requestRepositoryName && pullreq.userName == pullreq.requestUserName){
            pullreq.branch
          }else{
            s"${pullreq.userName}:${pullreq.branch}"
          }
          val existIds = using(Git.open(Directory.getRepositoryDir(owner, name))) { git => JGitUtil.getAllCommitIds(git) }.toSet
          pullRemote(owner, name, pullreq.requestBranch, pullreq.userName, pullreq.repositoryName, pullreq.branch, loginAccount,
                     s"Merge branch '${alias}' into ${pullreq.requestBranch}") match {
            case None => // conflict
              flash += "error" -> s"Can't automatic merging branch '${alias}' into ${pullreq.requestBranch}."
            case Some(oldId) =>
              // update pull request
              updatePullRequests(owner, name, pullreq.requestBranch)

              using(Git.open(Directory.getRepositoryDir(owner, name))) { git =>
                //  after update branch

                val newCommitId = git.getRepository.resolve(s"refs/heads/${pullreq.requestBranch}")
                val commits = git.log.addRange(oldId, newCommitId).call.iterator.asScala.map(c => new JGitUtil.CommitInfo(c)).toList

                commits.foreach{ commit =>
                  if(!existIds.contains(commit.id)){
                    createIssueComment(owner, name, commit)
                  }
                }

                // record activity
                recordPushActivity(owner, name, loginAccount.userName, pullreq.branch, commits)

                // close issue by commit message
                if(pullreq.requestBranch == repository.repository.defaultBranch){
                  commits.map { commit =>
                    closeIssuesFromMessage(commit.fullMessage, loginAccount.userName, owner, name)
                  }
                }

                // call web hook
                callPullRequestWebHookByRequestBranch("synchronize", repository, pullreq.requestBranch, baseUrl, loginAccount)
                callWebHookOf(owner, name, WebHook.Push) {
                  for {
                    ownerAccount <- getAccountByUserName(owner)
                  } yield {
                    WebHookService.WebHookPushPayload(git, loginAccount, pullreq.requestBranch, repository, commits, ownerAccount, oldId = oldId, newId = newCommitId)
                  }
                }
              }
              flash += "info" -> s"Merge branch '${alias}' into ${pullreq.requestBranch}"
          }
        }
        redirect(s"/${repository.owner}/${repository.name}/pull/${issueId}")
      }
    }) getOrElse NotFound
  })

  post("/:owner/:repository/pull/:id/merge", mergeForm)(collaboratorsOnly { (form, repository) =>
    params("id").toIntOpt.flatMap { issueId =>
      val owner = repository.owner
      val name  = repository.name
      LockUtil.lock(s"${owner}/${name}"){
        getPullRequest(owner, name, issueId).map { case (issue, pullreq) =>
          using(Git.open(getRepositoryDir(owner, name))) { git =>
            // mark issue as merged and close.
            val loginAccount = context.loginAccount.get
            createComment(owner, name, loginAccount.userName, issueId, form.message, "merge")
            createComment(owner, name, loginAccount.userName, issueId, "Close", "close")
            updateClosed(owner, name, issueId, true)

            // record activity
            recordMergeActivity(owner, name, loginAccount.userName, issueId, form.message)

            // merge git repository
            mergePullRequest(git, pullreq.branch, issueId,
              s"Merge pull request #${issueId} from ${pullreq.requestUserName}/${pullreq.requestBranch}\n\n" + form.message,
               new PersonIdent(loginAccount.fullName, loginAccount.mailAddress))

            val (commits, _) = getRequestCompareInfo(owner, name, pullreq.commitIdFrom,
              pullreq.requestUserName, pullreq.requestRepositoryName, pullreq.commitIdTo)

            // close issue by content of pull request
            val defaultBranch = getRepository(owner, name).get.repository.defaultBranch
            if(pullreq.branch == defaultBranch){
              commits.flatten.foreach { commit =>
                closeIssuesFromMessage(commit.fullMessage, loginAccount.userName, owner, name)
              }
              closeIssuesFromMessage(issue.title + " " + issue.content.getOrElse(""), loginAccount.userName, owner, name)
              closeIssuesFromMessage(form.message, loginAccount.userName, owner, name)
            }

            updatePullRequests(owner, name, pullreq.branch)

            // call web hook
            callPullRequestWebHook("closed", repository, issueId, context.baseUrl, context.loginAccount.get)

            // notifications
            Notifier().toNotify(repository, issue, "merge"){
              Notifier.msgStatus(s"${context.baseUrl}/${owner}/${name}/pull/${issueId}")
            }

            redirect(s"/${owner}/${name}/pull/${issueId}")
          }
        }
      }
    } getOrElse NotFound
  })

  get("/:owner/:repository/compare")(referrersOnly { forkedRepository =>
    val headBranch:Option[String] = params.get("head")
    (forkedRepository.repository.originUserName, forkedRepository.repository.originRepositoryName) match {
      case (Some(originUserName), Some(originRepositoryName)) => {
        getRepository(originUserName, originRepositoryName).map { originRepository =>
          using(
            Git.open(getRepositoryDir(originUserName, originRepositoryName)),
            Git.open(getRepositoryDir(forkedRepository.owner, forkedRepository.name))
          ){ (oldGit, newGit) =>
            val newBranch = headBranch.getOrElse(JGitUtil.getDefaultBranch(newGit, forkedRepository).get._2)
            val oldBranch = originRepository.branchList.find( _ == newBranch).getOrElse(JGitUtil.getDefaultBranch(oldGit, originRepository).get._2)

            redirect(s"/${forkedRepository.owner}/${forkedRepository.name}/compare/${originUserName}:${oldBranch}...${newBranch}")
          }
        } getOrElse NotFound
      }
      case _ => {
        using(Git.open(getRepositoryDir(forkedRepository.owner, forkedRepository.name))){ git =>
          JGitUtil.getDefaultBranch(git, forkedRepository).map { case (_, defaultBranch) =>
            redirect(s"/${forkedRepository.owner}/${forkedRepository.name}/compare/${defaultBranch}...${headBranch.getOrElse(defaultBranch)}")
          } getOrElse {
            redirect(s"/${forkedRepository.owner}/${forkedRepository.name}")
          }
        }
      }
    }
  })

  get("/:owner/:repository/compare/*...*")(referrersOnly { forkedRepository =>
    val Seq(origin, forked) = multiParams("splat")
    val (originOwner, originId) = parseCompareIdentifie(origin, forkedRepository.owner)
    val (forkedOwner, forkedId) = parseCompareIdentifie(forked, forkedRepository.owner)

    (for(
      originRepositoryName <- if(originOwner == forkedOwner) {
        // Self repository
        Some(forkedRepository.name)
      } else if(forkedRepository.repository.originUserName.isEmpty){
        // when ForkedRepository is the original repository
        getForkedRepositories(forkedRepository.owner, forkedRepository.name).find(_._1 == originOwner).map(_._2)
      } else if(Some(originOwner) == forkedRepository.repository.originUserName){
        // Original repository
        forkedRepository.repository.originRepositoryName
      } else {
        // Sibling repository
        getUserRepositories(originOwner).find { x =>
          x.repository.originUserName == forkedRepository.repository.originUserName &&
            x.repository.originRepositoryName == forkedRepository.repository.originRepositoryName
        }.map(_.repository.repositoryName)
      };
      originRepository <- getRepository(originOwner, originRepositoryName)
    ) yield {
      using(
        Git.open(getRepositoryDir(originRepository.owner, originRepository.name)),
        Git.open(getRepositoryDir(forkedRepository.owner, forkedRepository.name))
      ){ case (oldGit, newGit) =>
        val (oldId, newId) =
          if(originRepository.branchList.contains(originId) && forkedRepository.branchList.contains(forkedId)){
            // Branch name
            val rootId = JGitUtil.getForkedCommitId(oldGit, newGit,
              originRepository.owner, originRepository.name, originId,
              forkedRepository.owner, forkedRepository.name, forkedId)

            (Option(oldGit.getRepository.resolve(rootId)),  Option(newGit.getRepository.resolve(forkedId)))
          } else {
            // Commit id
            (Option(oldGit.getRepository.resolve(originId)), Option(newGit.getRepository.resolve(forkedId)))
          }

        (oldId, newId) match {
          case (Some(oldId), Some(newId)) => {
            val (commits, diffs) = getRequestCompareInfo(
              originRepository.owner, originRepository.name, oldId.getName,
              forkedRepository.owner, forkedRepository.name, newId.getName)

            val title = if(commits.flatten.length == 1){
              commits.flatten.head.shortMessage
            } else {
              val text = forkedId.replaceAll("[\\-_]", " ")
              text.substring(0, 1).toUpperCase + text.substring(1)
            }

            html.compare(
              title,
              commits,
              diffs,
              (forkedRepository.repository.originUserName, forkedRepository.repository.originRepositoryName) match {
                case (Some(userName), Some(repositoryName)) => (userName, repositoryName) :: getForkedRepositories(userName, repositoryName)
                case _ => (forkedRepository.owner, forkedRepository.name) :: getForkedRepositories(forkedRepository.owner, forkedRepository.name)
              },
              commits.flatten.map(commit => getCommitComments(forkedRepository.owner, forkedRepository.name, commit.id, false)).flatten.toList,
              originId,
              forkedId,
              oldId.getName,
              newId.getName,
              forkedRepository,
              originRepository,
              forkedRepository,
              hasWritePermission(originRepository.owner, originRepository.name, context.loginAccount),
              (getCollaborators(originRepository.owner, originRepository.name) ::: (if(getAccountByUserName(originRepository.owner).get.isGroupAccount) Nil else List(originRepository.owner))).sorted,
              getMilestones(originRepository.owner, originRepository.name),
              getLabels(originRepository.owner, originRepository.name)
            )
          }
          case (oldId, newId) =>
            redirect(s"/${forkedRepository.owner}/${forkedRepository.name}/compare/" +
                     s"${originOwner}:${oldId.map(_ => originId).getOrElse(originRepository.repository.defaultBranch)}..." +
                     s"${forkedOwner}:${newId.map(_ => forkedId).getOrElse(forkedRepository.repository.defaultBranch)}")
        }
      }
    }) getOrElse NotFound
  })

  ajaxGet("/:owner/:repository/compare/*...*/mergecheck")(collaboratorsOnly { forkedRepository =>
    val Seq(origin, forked) = multiParams("splat")
    val (originOwner, tmpOriginBranch) = parseCompareIdentifie(origin, forkedRepository.owner)
    val (forkedOwner, tmpForkedBranch) = parseCompareIdentifie(forked, forkedRepository.owner)

    (for(
      originRepositoryName <- if(originOwner == forkedOwner){
        Some(forkedRepository.name)
      } else {
        forkedRepository.repository.originRepositoryName.orElse {
          getForkedRepositories(forkedRepository.owner, forkedRepository.name).find(_._1 == originOwner).map(_._2)
        }
      };
      originRepository <- getRepository(originOwner, originRepositoryName)
    ) yield {
      using(
        Git.open(getRepositoryDir(originRepository.owner, originRepository.name)),
        Git.open(getRepositoryDir(forkedRepository.owner, forkedRepository.name))
      ){ case (oldGit, newGit) =>
        val originBranch = JGitUtil.getDefaultBranch(oldGit, originRepository, tmpOriginBranch).get._2
        val forkedBranch = JGitUtil.getDefaultBranch(newGit, forkedRepository, tmpForkedBranch).get._2
        val conflict = LockUtil.lock(s"${originRepository.owner}/${originRepository.name}"){
          checkConflict(originRepository.owner, originRepository.name, originBranch,
                        forkedRepository.owner, forkedRepository.name, forkedBranch)
        }
        html.mergecheck(conflict)
      }
    }) getOrElse NotFound
  })

  post("/:owner/:repository/pulls/new", pullRequestForm)(referrersOnly { (form, repository) =>
    defining(repository.owner, repository.name){ case (owner, name) =>
      val writable = hasWritePermission(owner, name, context.loginAccount)
      val loginUserName = context.loginAccount.get.userName

      val issueId = createIssue(
        owner            = repository.owner,
        repository       = repository.name,
        loginUser        = loginUserName,
        title            = form.title,
        content          = form.content,
        assignedUserName = if(writable) form.assignedUserName else None,
        milestoneId      = if(writable) form.milestoneId else None,
        isPullRequest    = true)

      createPullRequest(
        originUserName        = repository.owner,
        originRepositoryName  = repository.name,
        issueId               = issueId,
        originBranch          = form.targetBranch,
        requestUserName       = form.requestUserName,
        requestRepositoryName = form.requestRepositoryName,
        requestBranch         = form.requestBranch,
        commitIdFrom          = form.commitIdFrom,
        commitIdTo            = form.commitIdTo)

      // insert labels
      if(writable){
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
        createReferComment(owner, name, issue, form.title + " " + form.content.getOrElse(""), context.loginAccount.get)

        // notifications
        Notifier().toNotify(repository, issue, form.content.getOrElse("")){
          Notifier.msgPullRequest(s"${context.baseUrl}/${owner}/${name}/pull/${issueId}")
        }
      }

      redirect(s"/${owner}/${name}/pull/${issueId}")
    }
  })

  /**
   * Parses branch identifier and extracts owner and branch name as tuple.
   *
   * - "owner:branch" to ("owner", "branch")
   * - "branch" to ("defaultOwner", "branch")
   */
  private def parseCompareIdentifie(value: String, defaultOwner: String): (String, String) =
    if(value.contains(':')){
      val array = value.split(":")
      (array(0), array(1))
    } else {
      (defaultOwner, value)
    }

  private def getRequestCompareInfo(userName: String, repositoryName: String, branch: String,
      requestUserName: String, requestRepositoryName: String, requestCommitId: String): (Seq[Seq[CommitInfo]], Seq[DiffInfo]) =
    using(
      Git.open(getRepositoryDir(userName, repositoryName)),
      Git.open(getRepositoryDir(requestUserName, requestRepositoryName))
    ){ (oldGit, newGit) =>
      val oldId = oldGit.getRepository.resolve(branch)
      val newId = newGit.getRepository.resolve(requestCommitId)

      val commits = newGit.log.addRange(oldId, newId).call.iterator.asScala.map { revCommit =>
        new CommitInfo(revCommit)
      }.toList.splitWith { (commit1, commit2) =>
        helpers.date(commit1.commitTime) == view.helpers.date(commit2.commitTime)
      }

      val diffs = JGitUtil.getDiffs(newGit, oldId.getName, newId.getName, true)

      (commits, diffs)
    }

  private def searchPullRequests(userName: Option[String], repository: RepositoryService.RepositoryInfo) =
    defining(repository.owner, repository.name){ case (owner, repoName) =>
      val page       = IssueSearchCondition.page(request)
      val sessionKey = Keys.Session.Pulls(owner, repoName)

      // retrieve search condition
      val condition = session.putAndGet(sessionKey,
        if(request.hasQueryString) IssueSearchCondition(request)
        else session.getAs[IssueSearchCondition](sessionKey).getOrElse(IssueSearchCondition())
      )

      gitbucket.core.issues.html.list(
        "pulls",
        searchIssue(condition, true, (page - 1) * PullRequestLimit, PullRequestLimit, owner -> repoName),
        page,
        if(!getAccountByUserName(owner).exists(_.isGroupAccount)){
          (getCollaborators(owner, repoName) :+ owner).sorted
        } else {
          getCollaborators(owner, repoName)
        },
        getMilestones(owner, repoName),
        getLabels(owner, repoName),
        countIssue(condition.copy(state = "open"  ), true, owner -> repoName),
        countIssue(condition.copy(state = "closed"), true, owner -> repoName),
        condition,
        repository,
        hasWritePermission(owner, repoName, context.loginAccount))
    }

}
