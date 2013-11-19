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
import service.RepositoryService.RepositoryTreeNode
import util.JGitUtil.CommitInfo
import org.slf4j.LoggerFactory
import org.eclipse.jgit.merge.MergeStrategy
import org.eclipse.jgit.errors.NoMergeBaseException

class PullRequestsController extends PullRequestsControllerBase
  with RepositoryService with AccountService with IssuesService with PullRequestService with MilestonesService with ActivityService
  with ReferrerAuthenticator with CollaboratorsAuthenticator

trait PullRequestsControllerBase extends ControllerBase {
  self: RepositoryService with AccountService with IssuesService with MilestonesService with ActivityService with PullRequestService
    with ReferrerAuthenticator with CollaboratorsAuthenticator =>

  private val logger = LoggerFactory.getLogger(classOf[PullRequestsControllerBase])

  val pullRequestForm = mapping(
    "title"           -> trim(label("Title"  , text(required, maxlength(100)))),
    "content"         -> trim(label("Content", optional(text()))),
    "targetUserName"  -> trim(text(required, maxlength(100))),
    "targetBranch"    -> trim(text(required, maxlength(100))),
    "requestUserName" -> trim(text(required, maxlength(100))),
    "requestBranch"   -> trim(text(required, maxlength(100))),
    "commitIdFrom"    -> trim(text(required, maxlength(40))),
    "commitIdTo"      -> trim(text(required, maxlength(40)))
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
    requestBranch: String,
    commitIdFrom: String,
    commitIdTo: String)

  case class MergeForm(message: String)

  get("/:owner/:repository/pulls")(referrersOnly { repository =>
    searchPullRequests(None, repository)
  })

  get("/:owner/:repository/pulls/:userName")(referrersOnly { repository =>
    searchPullRequests(Some(params("userName")), repository)
  })

  get("/:owner/:repository/pull/:id")(referrersOnly { repository =>
    params("id").toIntOpt.flatMap{ issueId =>
      val owner = repository.owner
      val name = repository.name
      getPullRequest(owner, name, issueId) map { case(issue, pullreq) =>
        using(Git.open(getRepositoryDir(owner, name))){ git =>
          // prepare head branch
          val commitIdTo = fetchPullRequest(git, issueId, pullreq.requestUserName, pullreq.requestRepositoryName, pullreq.requestBranch)
          updateCommitIdTo(owner, name, issueId, commitIdTo)

          val (commits, diffs) = getRequestCompareInfo(owner, name, pullreq.commitIdFrom, owner, name, commitIdTo)

          pulls.html.pullreq(
            issue, pullreq,
            getComments(owner, name, issueId),
            (getCollaborators(owner, name) ::: (if(getAccountByUserName(owner).get.isGroupAccount) Nil else List(owner))).sorted,
            getMilestonesWithIssueCount(owner, name),
            commits,
            diffs,
            useInlineDiff(context.loginAccount),
            hasWritePermission(owner, name, context.loginAccount),
            repository)
        }
      }
    } getOrElse NotFound
  })

  ajaxGet("/:owner/:repository/pull/:id/mergeguide")(collaboratorsOnly { repository =>
    params("id").toIntOpt.flatMap{ issueId =>
      val owner = repository.owner
      val name = repository.name
      getPullRequest(owner, name, issueId) map { case(issue, pullreq) =>
        pulls.html.mergeguide(
          checkConflictInPullRequest(owner, name, pullreq.branch, pullreq.requestUserName, name, pullreq.requestBranch, issueId),
          pullreq,
          s"${baseUrl}${context.path}/git/${pullreq.requestUserName}/${pullreq.requestRepositoryName}.git")
      }
    } getOrElse NotFound
  })

  post("/:owner/:repository/pull/:id/merge", mergeForm)(collaboratorsOnly { (form, repository) =>
    params("id").toIntOpt.flatMap { issueId =>
      val owner = repository.owner
      val name = repository.name
      LockUtil.lock(s"${owner}/${name}/merge"){
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
            mergeCommit.setMessage(s"Merge pull request #${issueId} from ${pullreq.requestUserName}/${pullreq.requestRepositoryName}\n\n" +
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

            commits.flatten.foreach { commit =>
              if(!existsCommitId(owner, name, commit.id)){
                insertCommitId(owner, name, commit.id)
              }
            }

            // notifications
            Notifier().toNotify(repository, issueId, "merge"){
              Notifier.msgStatus(s"${baseUrl}/${owner}/${name}/pull/${issueId}")
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
        getRepository(originUserName, originRepositoryName, baseUrl).map { originRepository =>
          using(
            Git.open(getRepositoryDir(originUserName, originRepositoryName)),
            Git.open(getRepositoryDir(forkedRepository.owner, forkedRepository.name))
          ){ (oldGit, newGit) =>
            val oldBranch = JGitUtil.getDefaultBranch(oldGit, originRepository).get._2
            val newBranch = JGitUtil.getDefaultBranch(newGit, forkedRepository).get._2

            redirect(s"${context.path}/${forkedRepository.owner}/${forkedRepository.name}/compare/${originUserName}:${oldBranch}...${newBranch}")
          }
        } getOrElse NotFound
      }
      case _ => {
        using(Git.open(getRepositoryDir(forkedRepository.owner, forkedRepository.name))){ git =>
          JGitUtil.getDefaultBranch(git, forkedRepository).map { case (_, defaultBranch) =>
            redirect(s"${context.path}/${forkedRepository.owner}/${forkedRepository.name}/compare/${defaultBranch}...${defaultBranch}")
          } getOrElse {
            redirect(s"${context.path}/${forkedRepository.owner}/${forkedRepository.name}")
          }
        }
      }
    }
  })

  get("/:owner/:repository/compare/*...*")(referrersOnly { repository =>
    val Seq(origin, forked) = multiParams("splat")
    val (originOwner, tmpOriginBranch) = parseCompareIdentifie(origin, repository.owner)
    val (forkedOwner, tmpForkedBranch) = parseCompareIdentifie(forked, repository.owner)

    (getRepository(originOwner, repository.name, baseUrl),
     getRepository(forkedOwner, repository.name, baseUrl)) match {
      case (Some(originRepository), Some(forkedRepository)) => {
        using(
          Git.open(getRepositoryDir(originOwner, repository.name)),
          Git.open(getRepositoryDir(forkedOwner, repository.name))
        ){ case (oldGit, newGit) =>
          val originBranch = JGitUtil.getDefaultBranch(oldGit, originRepository, tmpOriginBranch).get._2
          val forkedBranch = JGitUtil.getDefaultBranch(newGit, forkedRepository, tmpForkedBranch).get._2

          val forkedId = getForkedCommitId(oldGit, newGit,
            originOwner, repository.name, originBranch,
            forkedOwner, repository.name, forkedBranch)

          val oldId = oldGit.getRepository.resolve(forkedId)
          val newId = newGit.getRepository.resolve(forkedBranch)

          val (commits, diffs) = getRequestCompareInfo(
            originOwner, repository.name, oldId.getName,
            forkedOwner, repository.name, newId.getName)

          pulls.html.compare(
            commits,
            diffs,
            repository.repository.originUserName.map { userName =>
              userName :: getForkedRepositories(userName, repository.name)
            } getOrElse List(repository.owner),
            originBranch,
            forkedBranch,
            oldId.getName,
            newId.getName,
            repository,
            originRepository,
            forkedRepository,
            useInlineDiff(context.loginAccount),
            hasWritePermission(repository.owner, repository.name, context.loginAccount))
        }
      }
      case _ => NotFound
    }
  })

  ajaxGet("/:owner/:repository/compare/*...*/mergecheck")(collaboratorsOnly { repository =>
    val Seq(origin, forked) = multiParams("splat")
    val (originOwner, tmpOriginBranch) = parseCompareIdentifie(origin, repository.owner)
    val (forkedOwner, tmpForkedBranch) = parseCompareIdentifie(forked, repository.owner)

    (getRepository(originOwner, repository.name, baseUrl),
      getRepository(forkedOwner, repository.name, baseUrl)) match {
      case (Some(originRepository), Some(forkedRepository)) => {
        using(
          Git.open(getRepositoryDir(originOwner, repository.name)),
          Git.open(getRepositoryDir(forkedOwner, repository.name))
        ){ case (oldGit, newGit) =>
          val originBranch = JGitUtil.getDefaultBranch(oldGit, originRepository, tmpOriginBranch).get._2
          val forkedBranch = JGitUtil.getDefaultBranch(newGit, forkedRepository, tmpForkedBranch).get._2

          pulls.html.mergecheck(
            checkConflict(originOwner, repository.name, originBranch, forkedOwner, repository.name, forkedBranch))
        }
      }
      case _ => NotFound()
    }
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
      requestRepositoryName = repository.name,
      requestBranch         = form.requestBranch,
      commitIdFrom          = form.commitIdFrom,
      commitIdTo            = form.commitIdTo)

    // fetch requested branch
    using(Git.open(getRepositoryDir(repository.owner, repository.name))){ git =>
      git.fetch
        .setRemote(getRepositoryDir(form.requestUserName, repository.name).toURI.toString)
        .setRefSpecs(new RefSpec(s"refs/heads/${form.requestBranch}:refs/pull/${issueId}/head"))
        .call
    }

    // record activity
    recordPullRequestActivity(repository.owner, repository.name, loginUserName, issueId, form.title)

    // notifications
    Notifier().toNotify(repository, issueId, form.content.getOrElse("")){
      Notifier.msgPullRequest(s"${baseUrl}/${repository.owner}/${repository.name}/pull/${issueId}")
    }

    redirect(s"/${repository.owner}/${repository.name}/pull/${issueId}")
  })

  /**
   * Checks whether conflict will be caused in merging. Returns true if conflict will be caused.
   */
  private def checkConflict(userName: String, repositoryName: String, branch: String,
                            requestUserName: String, requestRepositoryName: String, requestBranch: String): Boolean = {
    LockUtil.lock(s"${userName}/${repositoryName}/merge-check"){
      using(Git.open(getRepositoryDir(requestUserName, requestRepositoryName))) { git =>
        val remoteRefName = s"refs/heads/${branch}"
        val tmpRefName = s"refs/merge-check/${userName}/${branch}"

        withTmpRefSpec(new RefSpec(s"${remoteRefName}:${tmpRefName}").setForceUpdate(true), git) { ref =>
          // fetch objects from origin repository branch
          git.fetch
             .setRemote(getRepositoryDir(userName, repositoryName).toURI.toString)
             .setRefSpecs(ref)
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
    LockUtil.lock(s"${userName}/${repositoryName}/merge") {
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

  /**
   * Extracts all repository names from [[service.RepositoryService.RepositoryTreeNode]] as flat list.
   */
  private def getRepositoryNames(node: RepositoryTreeNode): List[String] =
    node.owner :: node.children.map { child => getRepositoryNames(child) }.flatten

  /**
   * Returns the identifier of the root commit (or latest merge commit) of the specified branch.
   */
  private def getForkedCommitId(oldGit: Git, newGit: Git, userName: String, repositoryName: String, branch: String,
      requestUserName: String, requestRepositoryName: String, requestBranch: String): String =
    JGitUtil.getCommitLogs(newGit, requestBranch, true){ commit =>
      existsCommitId(userName, repositoryName, commit.getName) &&
        JGitUtil.getBranchesOfCommit(oldGit, commit.getName).contains(branch)
    }.head.id

  private def getRequestCompareInfo(userName: String, repositoryName: String, branch: String,
      requestUserName: String, requestRepositoryName: String, requestCommitId: String): (Seq[Seq[CommitInfo]], Seq[DiffInfo]) = {

    using(
      Git.open(getRepositoryDir(userName, repositoryName)),
      Git.open(getRepositoryDir(requestUserName, requestRepositoryName))
    ){ (oldGit, newGit) =>
      val oldId = oldGit.getRepository.resolve(branch)
      val newId = newGit.getRepository.resolve(requestCommitId)

      val commits = newGit.log.addRange(oldId, newId).call.iterator.asScala.map { revCommit =>
        new CommitInfo(revCommit)
      }.toList.splitWith{ (commit1, commit2) =>
        view.helpers.date(commit1.time) == view.helpers.date(commit2.time)
      }

      val diffs = JGitUtil.getDiffs(newGit, oldId.getName, newId.getName, true)

      (commits, diffs)
    }
  }

  private def searchPullRequests(userName: Option[String], repository: RepositoryService.RepositoryInfo) =
    defining(repository.owner, repository.name){ case (owner, repoName) =>
      val filterUser = userName.map { x => Map("created_by" -> x) } getOrElse Map("all" -> "")
      val page       = IssueSearchCondition.page(request)
      val sessionKey = Keys.Session.Pulls(owner, repoName)

      // retrieve search condition
      val condition = session.putAndGet(sessionKey,
        if(request.hasQueryString) IssueSearchCondition(request)
        else session.getAs[IssueSearchCondition](sessionKey).getOrElse(IssueSearchCondition())
      )

      pulls.html.list(
        searchIssue(condition, filterUser, true, (page - 1) * PullRequestLimit, PullRequestLimit, owner -> repoName),
        getPullRequestCountGroupByUser(condition.state == "closed", owner, Some(repoName)),
        userName,
        page,
        countIssue(condition.copy(state = "open"  ), filterUser, true, owner -> repoName),
        countIssue(condition.copy(state = "closed"), filterUser, true, owner -> repoName),
        countIssue(condition, Map.empty, true, owner -> repoName),
        condition,
        repository,
        hasWritePermission(owner, repoName, context.loginAccount))
    }

  /**
   * Fetch pull request contents into refs/pull/${issueId}/head and return the head commit id of the pull request.
   */
  private def fetchPullRequest(git: Git, issueId: Int, requestUserName: String, requestRepositoryName: String, requestBranch: String): String = {
    git.fetch
      .setRemote(getRepositoryDir(requestUserName, requestRepositoryName).toURI.toString)
      .setRefSpecs(new RefSpec(s"refs/heads/${requestBranch}:refs/pull/${issueId}/head").setForceUpdate(true))
      .call

    git.getRepository.resolve(s"refs/pull/${issueId}/head").getName
  }

}
