package app

import util.{LockUtil, CollaboratorsAuthenticator, JGitUtil, ReferrerAuthenticator, Notifier, Keys}
import util.Directory._
import util.Implicits._
import util.ControlUtil._
import service._
import org.eclipse.jgit.api.Git
import jp.sf.amateras.scalatra.forms._
import org.eclipse.jgit.transport.RefSpec
import scala.collection.JavaConverters._
import org.eclipse.jgit.lib.{ObjectId, CommitBuilder, PersonIdent}
import service.IssuesService._
import service.PullRequestService._
import util.JGitUtil.DiffInfo
import util.JGitUtil.CommitInfo
import org.slf4j.LoggerFactory
import org.eclipse.jgit.merge.MergeStrategy
import org.eclipse.jgit.errors.NoMergeBaseException
import service.WebHookService.WebHookPayload

class PullRequestsController extends PullRequestsControllerBase
  with RepositoryService with AccountService with IssuesService with PullRequestService with MilestonesService with LabelsService
  with ActivityService with WebHookService with ReferrerAuthenticator with CollaboratorsAuthenticator

trait PullRequestsControllerBase extends ControllerBase {
  self: RepositoryService with AccountService with IssuesService with MilestonesService with LabelsService
    with ActivityService with PullRequestService with WebHookService with ReferrerAuthenticator with CollaboratorsAuthenticator =>

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
    searchPullRequests(None, repository)
  })

  get("/:owner/:repository/pull/:id")(referrersOnly { repository =>
    params("id").toIntOpt.flatMap{ issueId =>
      val owner = repository.owner
      val name = repository.name
      getPullRequest(owner, name, issueId) map { case(issue, pullreq) =>
        using(Git.open(getRepositoryDir(owner, name))){ git =>
          val (commits, diffs) =
            getRequestCompareInfo(owner, name, pullreq.commitIdFrom, owner, name, pullreq.commitIdTo)

          pulls.html.pullreq(
            issue, pullreq,
            getComments(owner, name, issueId),
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

  ajaxGet("/:owner/:repository/pull/:id/mergeguide")(collaboratorsOnly { repository =>
    params("id").toIntOpt.flatMap{ issueId =>
      val owner = repository.owner
      val name  = repository.name
      getPullRequest(owner, name, issueId) map { case(issue, pullreq) =>
        pulls.html.mergeguide(
          checkConflictInPullRequest(owner, name, pullreq.branch, pullreq.requestUserName, name, pullreq.requestBranch, issueId),
          pullreq,
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

            // merge
            val mergeBaseRefName = s"refs/heads/${pullreq.branch}"
            val merger = MergeStrategy.RECURSIVE.newMerger(git.getRepository, true)
            val mergeBaseTip = git.getRepository.resolve(mergeBaseRefName)
            val mergeTip = git.getRepository.resolve(s"refs/pull/${issueId}/head")
            val conflicted = try {
              !merger.merge(mergeBaseTip, mergeTip)
            } catch {
              case e: NoMergeBaseException => true
            }
            if (conflicted) {
              throw new RuntimeException("This pull request can't merge automatically.")
            }

            // creates merge commit
            val mergeCommit = new CommitBuilder()
            mergeCommit.setTreeId(merger.getResultTreeId)
            mergeCommit.setParentIds(Array[ObjectId](mergeBaseTip, mergeTip): _*)
            val personIdent = new PersonIdent(loginAccount.fullName, loginAccount.mailAddress)
            mergeCommit.setAuthor(personIdent)
            mergeCommit.setCommitter(personIdent)
            mergeCommit.setMessage(s"Merge pull request #${issueId} from ${pullreq.requestUserName}/${pullreq.requestBranch}\n\n" +
                                   form.message)

            // insertObject and got mergeCommit Object Id
            val inserter = git.getRepository.newObjectInserter
            val mergeCommitId = inserter.insert(mergeCommit)
            inserter.flush()
            inserter.release()

            // update refs
            val refUpdate = git.getRepository.updateRef(mergeBaseRefName)
            refUpdate.setNewObjectId(mergeCommitId)
            refUpdate.setForceUpdate(false)
            refUpdate.setRefLogIdent(personIdent)
            refUpdate.setRefLogMessage("merged", true)
            refUpdate.update()

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
            getWebHookURLs(owner, name) match {
              case webHookURLs if(webHookURLs.nonEmpty) =>
                for(ownerAccount <- getAccountByUserName(owner)){
                  callWebHook(owner, name, webHookURLs,
                    WebHookPayload(git, loginAccount, mergeBaseRefName, repository, commits.flatten.toList, ownerAccount))
                }
              case _ =>
            }

            // notifications
            Notifier().toNotify(repository, issueId, "merge"){
              Notifier.msgStatus(s"${context.baseUrl}/${owner}/${name}/pull/${issueId}")
            }

            redirect(s"/${owner}/${name}/pull/${issueId}")
          }
        }
      }
    } getOrElse NotFound
  })

  get("/:owner/:repository/compare")(referrersOnly { forkedRepository =>
    (forkedRepository.repository.originUserName, forkedRepository.repository.originRepositoryName) match {
      case (Some(originUserName), Some(originRepositoryName)) => {
        getRepository(originUserName, originRepositoryName, context.baseUrl).map { originRepository =>
          using(
            Git.open(getRepositoryDir(originUserName, originRepositoryName)),
            Git.open(getRepositoryDir(forkedRepository.owner, forkedRepository.name))
          ){ (oldGit, newGit) =>
            val oldBranch = JGitUtil.getDefaultBranch(oldGit, originRepository).get._2
            val newBranch = JGitUtil.getDefaultBranch(newGit, forkedRepository).get._2

            redirect(s"/${forkedRepository.owner}/${forkedRepository.name}/compare/${originUserName}:${oldBranch}...${newBranch}")
          }
        } getOrElse NotFound
      }
      case _ => {
        using(Git.open(getRepositoryDir(forkedRepository.owner, forkedRepository.name))){ git =>
          JGitUtil.getDefaultBranch(git, forkedRepository).map { case (_, defaultBranch) =>
            redirect(s"/${forkedRepository.owner}/${forkedRepository.name}/compare/${defaultBranch}...${defaultBranch}")
          } getOrElse {
            redirect(s"/${forkedRepository.owner}/${forkedRepository.name}")
          }
        }
      }
    }
  })

  get("/:owner/:repository/compare/*...*")(referrersOnly { forkedRepository =>
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

        val forkedId = JGitUtil.getForkedCommitId(oldGit, newGit,
          originRepository.owner, originRepository.name, originBranch,
          forkedRepository.owner, forkedRepository.name, forkedBranch)

        val oldId = oldGit.getRepository.resolve(forkedId)
        val newId = newGit.getRepository.resolve(forkedBranch)

        val (commits, diffs) = getRequestCompareInfo(
          originRepository.owner, originRepository.name, oldId.getName,
          forkedRepository.owner, forkedRepository.name, newId.getName)

        pulls.html.compare(
          commits,
          diffs,
          (forkedRepository.repository.originUserName, forkedRepository.repository.originRepositoryName) match {
            case (Some(userName), Some(repositoryName)) => (userName, repositoryName) :: getForkedRepositories(userName, repositoryName)
            case _ => (forkedRepository.owner, forkedRepository.name) :: getForkedRepositories(forkedRepository.owner, forkedRepository.name)
          },
          originBranch,
          forkedBranch,
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

        pulls.html.mergecheck(
          checkConflict(originRepository.owner, originRepository.name, originBranch,
                        forkedRepository.owner, forkedRepository.name, forkedBranch))
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
    using(Git.open(getRepositoryDir(repository.owner, repository.name))){ git =>
      git.fetch
        .setRemote(getRepositoryDir(form.requestUserName, form.requestRepositoryName).toURI.toString)
        .setRefSpecs(new RefSpec(s"refs/heads/${form.requestBranch}:refs/pull/${issueId}/head"))
        .call
    }

    // record activity
    recordPullRequestActivity(repository.owner, repository.name, loginUserName, issueId, form.title)

    // notifications
    Notifier().toNotify(repository, issueId, form.content.getOrElse("")){
      Notifier.msgPullRequest(s"${context.baseUrl}/${repository.owner}/${repository.name}/pull/${issueId}")
    }

    redirect(s"/${repository.owner}/${repository.name}/pull/${issueId}")
  })

  /**
   * Checks whether conflict will be caused in merging. Returns true if conflict will be caused.
   */
  private def checkConflict(userName: String, repositoryName: String, branch: String,
                            requestUserName: String, requestRepositoryName: String, requestBranch: String): Boolean = {
    LockUtil.lock(s"${userName}/${repositoryName}"){
      using(Git.open(getRepositoryDir(requestUserName, requestRepositoryName))) { git =>
        val remoteRefName = s"refs/heads/${branch}"
        val tmpRefName = s"refs/merge-check/${userName}/${branch}"
        val refSpec = new RefSpec(s"${remoteRefName}:${tmpRefName}").setForceUpdate(true)
        try {
          // fetch objects from origin repository branch
          git.fetch
             .setRemote(getRepositoryDir(userName, repositoryName).toURI.toString)
             .setRefSpecs(refSpec)
             .call

          // merge conflict check
          val merger = MergeStrategy.RECURSIVE.newMerger(git.getRepository, true)
          val mergeBaseTip = git.getRepository.resolve(s"refs/heads/${requestBranch}")
          val mergeTip = git.getRepository.resolve(tmpRefName)
          try {
            !merger.merge(mergeBaseTip, mergeTip)
          } catch {
            case e: NoMergeBaseException =>  true
          }
        } finally {
          val refUpdate = git.getRepository.updateRef(refSpec.getDestination)
          refUpdate.setForceUpdate(true)
          refUpdate.delete()
        }
      }
    }
  }

  /**
   * Checks whether conflict will be caused in merging within pull request. Returns true if conflict will be caused.
   */
  private def checkConflictInPullRequest(userName: String, repositoryName: String, branch: String,
                                         requestUserName: String, requestRepositoryName: String, requestBranch: String,
                                         issueId: Int): Boolean = {
    LockUtil.lock(s"${userName}/${repositoryName}") {
      using(Git.open(getRepositoryDir(userName, repositoryName))) { git =>
        // merge
        val merger = MergeStrategy.RECURSIVE.newMerger(git.getRepository, true)
        val mergeBaseTip = git.getRepository.resolve(s"refs/heads/${branch}")
        val mergeTip = git.getRepository.resolve(s"refs/pull/${issueId}/head")
        try {
          !merger.merge(mergeBaseTip, mergeTip)
        } catch {
          case e: NoMergeBaseException => true
        }
      }
    }
  }

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
        view.helpers.date(commit1.commitTime) == view.helpers.date(commit2.commitTime)
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

      issues.html.list(
        "pulls",
        searchIssue(condition, Map.empty, true, (page - 1) * PullRequestLimit, PullRequestLimit, owner -> repoName),
        page,
        (getCollaborators(owner, repoName) :+ owner).sorted,
        getMilestones(owner, repoName),
        getLabels(owner, repoName),
        countIssue(condition.copy(state = "open"  ), Map.empty, true, owner -> repoName),
        countIssue(condition.copy(state = "closed"), Map.empty, true, owner -> repoName),
        condition,
        repository,
        hasWritePermission(owner, repoName, context.loginAccount))
    }

}
