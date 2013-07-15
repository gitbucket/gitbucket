package app

import util.{CollaboratorsAuthenticator, FileUtil, JGitUtil, ReferrerAuthenticator}
import util.Directory._
import service._
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.api.Git
import jp.sf.amateras.scalatra.forms._
import util.JGitUtil.{DiffInfo, CommitInfo}
import org.eclipse.jgit.transport.RefSpec
import org.apache.commons.io.FileUtils
import org.eclipse.jgit.lib.PersonIdent

class PullRequestsController extends PullRequestsControllerBase
  with RepositoryService with AccountService with IssuesService with PullRequestService with MilestonesService with ActivityService
  with ReferrerAuthenticator with CollaboratorsAuthenticator

trait PullRequestsControllerBase extends ControllerBase {
  self: RepositoryService with IssuesService with MilestonesService with ActivityService with PullRequestService
    with ReferrerAuthenticator with CollaboratorsAuthenticator =>

  val pullRequestForm = mapping(
    "title"           -> trim(label("Title"  , text(required, maxlength(100)))),
    "content"         -> trim(label("Content", optional(text()))),
    "branch"          -> trim(text(required, maxlength(100))),
    "requestUserName" -> trim(text(required, maxlength(100))),
    "requestBranch"   -> trim(text(required, maxlength(100)))
  )(PullRequestForm.apply)

  val mergeForm = mapping(
    "message" -> trim(label("Message", text(required)))
  )(MergeForm.apply)

  case class PullRequestForm(title: String, content: Option[String], branch: String,
                             requestUserName: String, requestBranch: String)

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

        val (commits, diffs) = if(pullreq.mergeStartId.isDefined){
          getCompareInfo(owner, name, pullreq.mergeStartId.get, owner, name, pullreq.mergeEndId.get)
        } else {
          getCompareInfo(owner, name, pullreq.branch, pullreq.requestUserName, pullreq.requestRepositoryName, pullreq.requestBranch)
        }

        pulls.html.pullreq(
          issue, pullreq,
          getComments(owner, name, issueId.toInt),
          (getCollaborators(owner, name) :+ owner).sorted,
          getMilestones(owner, name),
          commits,
          diffs,
          requestCommitId.getName,
          hasWritePermission(owner, name, context.loginAccount),
          repository,
          s"${baseUrl}${context.path}/git/${pullreq.requestUserName}/${pullreq.requestRepositoryName}.git")
      }

    } getOrElse NotFound
  })

  post("/:owner/:repository/pulls/:id/merge", mergeForm)(collaboratorsOnly { (form, repository) =>
    val issueId = params("id").toInt

    getPullRequest(repository.owner, repository.name, issueId).map { case (issue, pullreq) =>
      val remote = getRepositoryDir(repository.owner, repository.name)
      val tmpdir = new java.io.File(getTemporaryDir(repository.owner, repository.name), s"merge-${issueId}")
      val git = Git.cloneRepository.setDirectory(tmpdir).setURI(remote.toURI.toString).call

      try {
        val (commits, _) = getCompareInfo(repository.owner, repository.name, pullreq.branch,
                                  pullreq.requestUserName, pullreq.requestRepositoryName, pullreq.requestBranch)
        mergePullRequest(repository.owner, repository.name, issueId,
          git.getRepository.resolve("master").getName,
          commits.head.head.id)

        commits.flatten.foreach { commit =>
          insertCommitId(repository.owner, repository.name, commit.id)
        }

        // TODO mark issue as 'merged'
        val loginAccount = context.loginAccount.get
        createComment(repository.owner, repository.name, loginAccount.userName, issueId, "Closed", Some("close"))
        updateClosed(repository.owner, repository.name, issueId, true)
        recordMergeActivity(repository.owner, repository.name, loginAccount.userName, issueId, form.message)

        git.checkout.setName(pullreq.branch).call

        git.fetch
          .setRemote(getRepositoryDir(pullreq.requestUserName, pullreq.requestRepositoryName).toURI.toString)
          .setRefSpecs(new RefSpec(s"refs/heads/${pullreq.branch}:refs/heads/${pullreq.requestBranch}")).call

        git.merge.include(git.getRepository.resolve("FETCH_HEAD")).setCommit(false).call

        git.commit
          .setCommitter(new PersonIdent(loginAccount.userName, loginAccount.mailAddress))
          .setMessage(s"Merge pull request #${issueId} from ${pullreq.requestUserName}/${pullreq.requestRepositoryName}\n"
                     + form.message).call
        git.push.call

      } finally {
        git.getRepository.close
        FileUtils.deleteDirectory(tmpdir)
      }
    } getOrElse NotFound
  })

  get("/:owner/:repository/pulls/compare")(collaboratorsOnly { newRepo =>
    (newRepo.repository.originUserName, newRepo.repository.originRepositoryName) match {
      case (None,_)|(_, None) => NotFound // TODO BadRequest?
      case (Some(originUserName), Some(originRepositoryName)) => {
        getRepository(originUserName, originRepositoryName, baseUrl).map { oldRepo =>
          withGit(
            getRepositoryDir(originUserName, originRepositoryName),
            getRepositoryDir(params("owner"), params("repository"))
          ){ (oldGit, newGit) =>
            val oldBranch = JGitUtil.getDefaultBranch(oldGit, oldRepo).get._2
            val newBranch = JGitUtil.getDefaultBranch(newGit, newRepo).get._2

            redirect(s"${context.path}/${newRepo.owner}/${newRepo.name}/pulls/compare/${originUserName}:${oldBranch}...${newBranch}")
          }
        } getOrElse NotFound
      }
    }
  })

  get("/:owner/:repository/pulls/compare/*:*...*")(collaboratorsOnly { repository =>
    if(repository.repository.originUserName.isEmpty || repository.repository.originRepositoryName.isEmpty){
      NotFound // TODO BadRequest?
    } else {
      getRepository(
        repository.repository.originUserName.get,
        repository.repository.originRepositoryName.get, baseUrl
      ).map{ originRepository =>
        val Seq(origin, originId, forkedId) = multiParams("splat")
        val userName       = params("owner")
        val repositoryName = params("repository")

        JGitUtil.withGit(getRepositoryDir(userName, repositoryName)){ git =>
          val newId = git.getRepository.resolve(forkedId)

          val pullreq = getCompareInfo(
            origin, repository.repository.originRepositoryName.get, originId,
            params("owner"), params("repository"), forkedId)

          pulls.html.compare(pullreq._1, pullreq._2, origin, originId, forkedId, newId.getName, repository, originRepository)
        }
      } getOrElse NotFound
    }
  })

  post("/:owner/:repository/pulls/new", pullRequestForm)(referrersOnly { (form, repository) =>
    val loginUserName = context.loginAccount.get.userName

    val issueId = createIssue(
      repository.owner,
      repository.name,
      loginUserName,
      form.title,
      form.content,
      None, None)

    createPullRequest(
      repository.owner,
      repository.name,
      issueId,
      form.branch,
      form.requestUserName,
      repository.name,
      form.requestBranch)

    recordPullRequestActivity(repository.owner, repository.name, loginUserName, issueId, form.title)

    redirect(s"/${repository.owner}/${repository.name}/pulls/${issueId}")
  })

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
   * Returns the commits and diffs between specified repository and revision.
   */
  private def getCompareInfo(userName: String, repositoryName: String, branch: String,
      requestUserName: String, requestRepositoryName: String, requestBranch: String): (Seq[Seq[CommitInfo]], Seq[DiffInfo]) = {

    import scala.collection.JavaConverters._
    import util.Implicits._

    withGit(
      getRepositoryDir(userName, repositoryName),
      getRepositoryDir(requestUserName, requestRepositoryName)
    ){ (oldGit, newGit) =>
      val newId = newGit.getRepository.resolve(requestBranch)
      val oldId = newGit.getRepository.resolve(JGitUtil.getCommitLogFrom(newGit, newId.getName, true){ revCommit =>
        existsCommitId(userName, repositoryName, revCommit.getName)
      }.head.id)

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
