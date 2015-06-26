package gitbucket.core.controller

import gitbucket.core.api._
import gitbucket.core.model.{Account, CommitState, Repository, PullRequest, Issue}
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

import jp.sf.amateras.scalatra.forms._
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.PersonIdent
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._


class PullRequestsController extends PullRequestsControllerBase
  with RepositoryService with AccountService with IssuesService with PullRequestService with MilestonesService with LabelsService
  with CommitsService with ActivityService with WebHookPullRequestService with ReferrerAuthenticator with CollaboratorsAuthenticator
  with CommitStatusService with MergeService


trait PullRequestsControllerBase extends ControllerBase {
  self: RepositoryService with AccountService with IssuesService with MilestonesService with LabelsService
    with CommitsService with ActivityService with PullRequestService with WebHookPullRequestService with ReferrerAuthenticator with CollaboratorsAuthenticator
    with CommitStatusService with MergeService =>

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
    "commitIdTo"            -> trim(text(required, maxlength(40)))
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
    commitIdTo: String)

  case class MergeForm(message: String)

  get("/:owner/:repository/pulls")(referrersOnly { repository =>
    val q = request.getParameter("q")
    if(Option(q).exists(_.contains("is:issue"))){
      redirect(s"/${repository.owner}/${repository.name}/issues?q=" + StringUtil.urlEncode(q))
    } else {
      searchPullRequests(None, repository)
    }
  })

  /**
   * https://developer.github.com/v3/pulls/#list-pull-requests
   */
  get("/api/v3/repos/:owner/:repository/pulls")(referrersOnly { repository =>
    val page       = IssueSearchCondition.page(request)
    // TODO: more api spec condition
    val condition = IssueSearchCondition(request)
    val baseOwner = getAccountByUserName(repository.owner).get
    val issues:List[(Issue, Account, Int, PullRequest, Repository, Account)] = searchPullRequestByApi(condition, (page - 1) * PullRequestLimit, PullRequestLimit, repository.owner -> repository.name)
    JsonFormat(issues.map{case (issue, issueUser, commentCount, pullRequest, headRepo, headOwner) =>
      ApiPullRequest(
        issue,
        pullRequest,
        ApiRepository(headRepo, ApiUser(headOwner)),
        ApiRepository(repository, ApiUser(baseOwner)),
        ApiUser(issueUser)) })
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
            repository)
        }
      }
    } getOrElse NotFound
  })

  /**
   * https://developer.github.com/v3/pulls/#get-a-single-pull-request
   */
  get("/api/v3/repos/:owner/:repository/pulls/:id")(referrersOnly { repository =>
    (for{
      issueId <- params("id").toIntOpt
      (issue, pullRequest) <- getPullRequest(repository.owner, repository.name, issueId)
      users = getAccountsByUserNames(Set(repository.owner, pullRequest.requestUserName, issue.openedUserName), Set())
      baseOwner <- users.get(repository.owner)
      headOwner <- users.get(pullRequest.requestUserName)
      issueUser <- users.get(issue.openedUserName)
      headRepo  <- getRepository(pullRequest.requestUserName, pullRequest.requestRepositoryName, baseUrl)
    } yield {
      JsonFormat(ApiPullRequest(
        issue,
        pullRequest,
        ApiRepository(headRepo, ApiUser(headOwner)),
        ApiRepository(repository, ApiUser(baseOwner)),
        ApiUser(issueUser)))
    }).getOrElse(NotFound)
  })

  /**
   * https://developer.github.com/v3/pulls/#list-commits-on-a-pull-request
   */
  get("/api/v3/repos/:owner/:repository/pulls/:id/commits")(referrersOnly { repository =>
    val owner = repository.owner
    val name = repository.name
    params("id").toIntOpt.flatMap{ issueId =>
      getPullRequest(owner, name, issueId) map { case(issue, pullreq) =>
        using(Git.open(getRepositoryDir(owner, name))){ git =>
          val oldId = git.getRepository.resolve(pullreq.commitIdFrom)
          val newId = git.getRepository.resolve(pullreq.commitIdTo)
          val repoFullName = RepositoryName(repository)
          val commits = git.log.addRange(oldId, newId).call.iterator.asScala.map(c => ApiCommitListItem(new CommitInfo(c), repoFullName)).toList
          JsonFormat(commits)
        }
      }
    } getOrElse NotFound
  })

  ajaxGet("/:owner/:repository/pull/:id/mergeguide")(collaboratorsOnly { repository =>
    params("id").toIntOpt.flatMap{ issueId =>
      val owner = repository.owner
      val name  = repository.name
      getPullRequest(owner, name, issueId) map { case(issue, pullreq) =>
        val statuses = getCommitStatues(owner, name, pullreq.commitIdTo)
        val hasConfrict = LockUtil.lock(s"${owner}/${name}"){
          checkConflict(owner, name, pullreq.branch, issueId)
        }
        val hasProblem = hasConfrict || (!statuses.isEmpty && CommitState.combine(statuses.map(_.state).toSet) != CommitState.SUCCESS)
        html.mergeguide(
          hasConfrict,
          hasProblem,
          issue,
          pullreq,
          statuses,
          repository,
          s"${context.baseUrl}/git/${pullreq.requestUserName}/${pullreq.requestRepositoryName}.git")
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
            val defaultBranch = getRepository(owner, name, context.baseUrl).get.repository.defaultBranch
            if(pullreq.branch == defaultBranch){
              commits.flatten.foreach { commit =>
                closeIssuesFromMessage(commit.fullMessage, loginAccount.userName, owner, name)
              }
              issue.content match {
                case Some(content) => closeIssuesFromMessage(content, loginAccount.userName, owner, name)
                case _ =>
              }
              closeIssuesFromMessage(form.message, loginAccount.userName, owner, name)
            }
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
        getRepository(originUserName, originRepositoryName, context.baseUrl).map { originRepository =>
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
      } else if(Some(originOwner) == forkedRepository.repository.originUserName){
        // Original repository
        forkedRepository.repository.originRepositoryName
      } else {
        // Sibling repository
        getUserRepositories(originOwner, context.baseUrl).find { x =>
          x.repository.originUserName == forkedRepository.repository.originUserName &&
            x.repository.originRepositoryName == forkedRepository.repository.originRepositoryName
        }.map(_.repository.repositoryName)
      };
      originRepository <- getRepository(originOwner, originRepositoryName, context.baseUrl)
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

            (oldGit.getRepository.resolve(rootId),  newGit.getRepository.resolve(forkedId))
          } else {
            // Commit id
            (oldGit.getRepository.resolve(originId), newGit.getRepository.resolve(forkedId))
          }

        val (commits, diffs) = getRequestCompareInfo(
          originRepository.owner, originRepository.name, oldId.getName,
          forkedRepository.owner, forkedRepository.name, newId.getName)

        html.compare(
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
          hasWritePermission(forkedRepository.owner, forkedRepository.name, context.loginAccount))
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
      originRepository <- getRepository(originOwner, originRepositoryName, context.baseUrl)
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
    val loginUserName = context.loginAccount.get.userName

    val issueId = createIssue(
      owner            = repository.owner,
      repository       = repository.name,
      loginUser        = loginUserName,
      title            = form.title,
      content          = form.content,
      assignedUserName = None,
      milestoneId      = None,
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

    // fetch requested branch
    fetchAsPullRequest(repository.owner, repository.name, form.requestUserName, form.requestRepositoryName, form.requestBranch, issueId)

    // record activity
    recordPullRequestActivity(repository.owner, repository.name, loginUserName, issueId, form.title)

    // call web hook
    callPullRequestWebHook("opened", repository, issueId, context.baseUrl, context.loginAccount.get)

    // notifications
    getIssue(repository.owner, repository.name, issueId.toString) foreach { issue =>
      Notifier().toNotify(repository, issue, form.content.getOrElse("")){
        Notifier.msgPullRequest(s"${context.baseUrl}/${repository.owner}/${repository.name}/pull/${issueId}")
      }
    }

    redirect(s"/${repository.owner}/${repository.name}/pull/${issueId}")
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
        (getCollaborators(owner, repoName) :+ owner).sorted,
        getMilestones(owner, repoName),
        getLabels(owner, repoName),
        countIssue(condition.copy(state = "open"  ), true, owner -> repoName),
        countIssue(condition.copy(state = "closed"), true, owner -> repoName),
        condition,
        repository,
        hasWritePermission(owner, repoName, context.loginAccount))
    }
}
