package app

import util.{LockUtil, CollaboratorsAuthenticator, JGitUtil, ReferrerAuthenticator}
import util.Directory._
import util.Implicits._
import util.JGitUtil.{DiffInfo, CommitInfo}
import service._
import org.eclipse.jgit.api.Git
import jp.sf.amateras.scalatra.forms._
import org.eclipse.jgit.transport.RefSpec
import org.apache.commons.io.FileUtils
import scala.collection.JavaConverters._
import service.RepositoryService.RepositoryTreeNode
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.api.MergeCommand.FastForwardMode

class PullRequestsController extends PullRequestsControllerBase
  with RepositoryService with AccountService with IssuesService with PullRequestService with MilestonesService with ActivityService
  with ReferrerAuthenticator with CollaboratorsAuthenticator

trait PullRequestsControllerBase extends ControllerBase {
  self: RepositoryService with IssuesService with MilestonesService with ActivityService with PullRequestService
    with ReferrerAuthenticator with CollaboratorsAuthenticator =>

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
    pulls.html.list(repository)
  })

  get("/:owner/:repository/pulls/:id")(referrersOnly { repository =>
    val owner   = repository.owner
    val name    = repository.name
    val issueId = params("id").toInt

    getPullRequest(owner, name, issueId) map { case(issue, pullreq) =>
      JGitUtil.withGit(getRepositoryDir(owner, name)){ git =>
        val requestCommitId = git.getRepository.resolve(pullreq.requestBranch)

        val (commits, diffs) =
          getRequestCompareInfo(owner, name, pullreq.commitIdFrom, owner, name, pullreq.commitIdTo)

        pulls.html.pullreq(
          issue, pullreq,
          getComments(owner, name, issueId.toInt),
          (getCollaborators(owner, name) :+ owner).sorted,
          getMilestones(owner, name),
          commits,
          diffs,
          requestCommitId.getName,
          if(issue.closed){
            false
          } else {
            checkConflict(owner, name, pullreq.branch, owner, name, pullreq.requestBranch)
          },
          hasWritePermission(owner, name, context.loginAccount),
          repository,
          s"${baseUrl}${context.path}/git/${pullreq.requestUserName}/${pullreq.requestRepositoryName}.git")
      }

    } getOrElse NotFound
  })

  post("/:owner/:repository/pulls/:id/merge", mergeForm)(collaboratorsOnly { (form, repository) =>
    LockUtil.lock(s"${repository.owner}/${repository.name}/merge"){
      val issueId = params("id").toInt

      getPullRequest(repository.owner, repository.name, issueId).map { case (issue, pullreq) =>
        val remote = getRepositoryDir(repository.owner, repository.name)
        val tmpdir = new java.io.File(getTemporaryDir(repository.owner, repository.name), s"merge-${issueId}")
        val git    = Git.cloneRepository.setDirectory(tmpdir).setURI(remote.toURI.toString).call

        try {
          // TODO mark issue as 'merged'
          val loginAccount = context.loginAccount.get
          createComment(repository.owner, repository.name, loginAccount.userName, issueId, "Closed", "close")
          updateClosed(repository.owner, repository.name, issueId, true)
          recordMergeActivity(repository.owner, repository.name, loginAccount.userName, issueId, form.message)

          // fetch pull request to working repository
          val pullRequestBranchName = s"gitbucket-pullrequest-${issueId}"

          git.fetch
            .setRemote(getRepositoryDir(repository.owner, repository.name).toURI.toString)
            .setRefSpecs(new RefSpec(s"refs/pull/${issueId}/head:refs/heads/${pullRequestBranchName}")).call

          // merge pull request
          git.checkout.setName(pullreq.branch).call

          val result = git.merge
            .include(git.getRepository.resolve(pullRequestBranchName))
            .setFastForward(FastForwardMode.NO_FF)
            .setCommit(false)
            .call

          if(result.getConflicts != null){
            throw new RuntimeException("This pull request can't merge automatically.")
          }

          // merge commit
          git.getRepository.writeMergeCommitMsg(
            s"Merge pull request #${issueId} from ${pullreq.requestUserName}/${pullreq.requestRepositoryName}\n"
            + form.message)

          git.commit
            .setCommitter(new PersonIdent(loginAccount.userName, loginAccount.mailAddress))
            .call

          // push
          git.push.call

          val (commits, _) = getRequestCompareInfo(repository.owner, repository.name, pullreq.commitIdFrom,
            pullreq.requestUserName, pullreq.requestRepositoryName, pullreq.commitIdTo)

          commits.flatten.foreach { commit =>
            if(!existsCommitId(repository.owner, repository.name, commit.id)){
              insertCommitId(repository.owner, repository.name, commit.id)
            }
          }

          redirect(s"/${repository.owner}/${repository.name}/pulls/${issueId}")

        } finally {
          git.getRepository.close
          FileUtils.deleteDirectory(tmpdir)
        }
      } getOrElse NotFound
    }
  })

  /**
   * Checks whether conflict will be caused in merging.
   * Returns true if conflict will be caused.
   */
  private def checkConflict(userName: String, repositoryName: String, branch: String,
                            requestUserName: String, requestRepositoryName: String, requestBranch: String): Boolean = {
    // TODO Are there more quick way?
    LockUtil.lock(s"${userName}/${repositoryName}/merge-check"){
      val remote = getRepositoryDir(userName, repositoryName)
      val tmpdir = new java.io.File(getTemporaryDir(userName, repositoryName), "merge-check")
      if(tmpdir.exists()){
        FileUtils.deleteDirectory(tmpdir)
      }

      val git = Git.cloneRepository.setDirectory(tmpdir).setURI(remote.toURI.toString).call
      try {
        git.checkout.setName(branch).call

        git.fetch
          .setRemote(getRepositoryDir(requestUserName, requestRepositoryName).toURI.toString)
          .setRefSpecs(new RefSpec(s"refs/heads/${branch}:refs/heads/${requestBranch}")).call

        val result = git.merge
          .include(git.getRepository.resolve("FETCH_HEAD"))
          .setCommit(false).call

        result.getConflicts != null

      } finally {
        git.getRepository.close
        FileUtils.deleteDirectory(tmpdir)
      }
    }
  }

  get("/:owner/:repository/pulls/compare")(collaboratorsOnly { forkedRepository =>
    (forkedRepository.repository.originUserName, forkedRepository.repository.originRepositoryName) match {
      case (Some(originUserName), Some(originRepositoryName)) => {
        getRepository(originUserName, originRepositoryName, baseUrl).map { originRepository =>
          withGit(
            getRepositoryDir(originUserName, originRepositoryName),
            getRepositoryDir(forkedRepository.owner, forkedRepository.name)
          ){ (oldGit, newGit) =>
            val oldBranch = JGitUtil.getDefaultBranch(oldGit, originRepository).get._2
            val newBranch = JGitUtil.getDefaultBranch(newGit, forkedRepository).get._2

            redirect(s"${context.path}/${forkedRepository.owner}/${forkedRepository.name}/pulls/compare/${originUserName}:${oldBranch}...${newBranch}")
          }
        } getOrElse NotFound
      }
      case _ => {
        JGitUtil.withGit(getRepositoryDir(forkedRepository.owner, forkedRepository.name)){ git =>
          val defaultBranch = JGitUtil.getDefaultBranch(git, forkedRepository).get._2
          redirect(s"${context.path}/${forkedRepository.owner}/${forkedRepository.name}/pulls/compare/${defaultBranch}...${defaultBranch}")
        }
      }
    }
  })

  get("/:owner/:repository/pulls/compare/*...*")(collaboratorsOnly { repository =>
    val Seq(origin, forked) = multiParams("splat")
    val (originOwner, tmpOriginBranch) = parseCompareIdentifie(origin, repository.owner)
    val (forkedOwner, tmpForkedBranch) = parseCompareIdentifie(forked, repository.owner)

    (getRepository(originOwner, repository.name, baseUrl),
     getRepository(forkedOwner,   repository.name, baseUrl)) match {
      case (Some(originRepository), Some(forkedRepository)) => {
        withGit(
          getRepositoryDir(originOwner, repository.name),
          getRepositoryDir(forkedOwner,   repository.name)
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
              getRepositoryNames(getForkedRepositoryTree(userName, repository.name))
            } getOrElse Nil,
            originBranch,
            forkedBranch,
            oldId.getName,
            newId.getName,
            checkConflict(originOwner, repository.name, originBranch, forkedOwner, repository.name, forkedBranch),
            repository,
            originRepository,
            forkedRepository)
        }
      }
      case _ => NotFound
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
    JGitUtil.withGit(getRepositoryDir(repository.owner, repository.name)){ git =>
      git.fetch
        .setRemote(getRepositoryDir(form.requestUserName, repository.name).toURI.toString)
        .setRefSpecs(new RefSpec(s"refs/heads/${form.requestBranch}:refs/pull/${issueId}/head"))
        .call
    }

    recordPullRequestActivity(repository.owner, repository.name, loginUserName, issueId, form.title)

    redirect(s"/${repository.owner}/${repository.name}/pulls/${issueId}")
  })

  /**
   * Handles w Git object simultaneously.
   */
  private def withGit[T](oldDir: java.io.File, newDir: java.io.File)(action: (Git, Git) => T): T = {
    val oldGit = Git.open(oldDir)
    val newGit = Git.open(newDir)
    try {
      action(oldGit, newGit)
    } finally {
      oldGit.getRepository.close
      newGit.getRepository.close
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

    withGit(
      getRepositoryDir(userName, repositoryName),
      getRepositoryDir(requestUserName, requestRepositoryName)
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

}
