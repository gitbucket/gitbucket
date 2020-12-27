package gitbucket.core.service

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib._
import org.eclipse.jgit.revwalk._
import org.scalatest.funspec.AnyFunSpec
import org.mockito.Mockito._
import java.io.File
import java.util.Date
import javax.servlet.http.HttpServletRequest
import scala.util.Using

import gitbucket.core.controller.Context
import gitbucket.core.plugin.ReceiveHook
import gitbucket.core.util.Directory._
import gitbucket.core.util.GitSpecUtil._
import gitbucket.core.service.RepositoryService.RepositoryInfo
import gitbucket.core.service.SystemSettingsService.SystemSettings
import gitbucket.core.service.SystemSettingsService.RepositoryOperation
import gitbucket.core.service.SystemSettingsService.Ssh
import gitbucket.core.service.SystemSettingsService.WebHook
import gitbucket.core.service.SystemSettingsService.Upload
import gitbucket.core.service.SystemSettingsService.RepositoryViewerSettings
import gitbucket.core.model.Account
import gitbucket.core.model.RepositoryOptions

class MergeServiceSpec extends AnyFunSpec with ServiceSpecBase {
  val service = new MergeService with AccountService with ActivityService with IssuesService with LabelsService
  with MilestonesService with RepositoryService with PrioritiesService with PullRequestService with CommitsService
  with WebHookPullRequestService with WebHookPullRequestReviewCommentService with RequestCache {
    override protected def getReceiveHooks(): Seq[ReceiveHook] = Nil
  }
  val branch = "master"
  val issueId = 10
  def initRepository(owner: String, name: String): File = {
    val dir = createTestRepository(getRepositoryDir(owner, name))
    Using.resource(Git.open(dir)) { git =>
      createFile(git, "refs/heads/master", "test.txt", "hoge")
      git.branchCreate().setStartPoint(s"refs/heads/master").setName(s"refs/pull/${issueId}/head").call()
    }
    dir
  }
  def createConfrict(git: Git) = {
    createFile(git, s"refs/heads/${branch}", "test.txt", "hoge2")
    createFile(git, s"refs/pull/${issueId}/head", "test.txt", "hoge4")
  }
  describe("checkConflict, checkConflictCache") {
    it("checkConflict false if not conflicted, and create cache") {
      val repo1Dir = initRepository("user1", "repo1")
      assert(service.checkConflictCache("user1", "repo1", branch, issueId) == None)
      val conflicted = service.checkConflict("user1", "repo1", branch, issueId)
      assert(service.checkConflictCache("user1", "repo1", branch, issueId) == Some(None))
      assert(conflicted.isEmpty)
    }
    it("checkConflict true if not conflicted, and create cache") {
      val repo2Dir = initRepository("user1", "repo2")
      Using.resource(Git.open(repo2Dir)) { git =>
        createConfrict(git)
      }
      assert(service.checkConflictCache("user1", "repo2", branch, issueId) == None)
      val conflicted = service.checkConflict("user1", "repo2", branch, issueId)
      assert(conflicted.isDefined)
      assert(service.checkConflictCache("user1", "repo2", branch, issueId) match {
        case Some(Some(_: String)) => true
        case _                     => false
      })
    }
  }
  describe("checkConflictCache") {
    it("merged cache invalid if origin branch moved") {
      val repo3Dir = initRepository("user1", "repo3")
      assert(service.checkConflict("user1", "repo3", branch, issueId).isEmpty)
      assert(service.checkConflictCache("user1", "repo3", branch, issueId) == Some(None))
      Using.resource(Git.open(repo3Dir)) { git =>
        createFile(git, s"refs/heads/${branch}", "test.txt", "hoge2")
      }
      assert(service.checkConflictCache("user1", "repo3", branch, issueId) == None)
    }
    it("merged cache invalid if request branch moved") {
      val repo4Dir = initRepository("user1", "repo4")
      assert(service.checkConflict("user1", "repo4", branch, issueId).isEmpty)
      assert(service.checkConflictCache("user1", "repo4", branch, issueId) == Some(None))
      Using.resource(Git.open(repo4Dir)) { git =>
        createFile(git, s"refs/pull/${issueId}/head", "test.txt", "hoge4")
      }
      assert(service.checkConflictCache("user1", "repo4", branch, issueId) == None)
    }
    it("should merged cache invalid if origin branch moved") {
      val repo5Dir = initRepository("user1", "repo5")
      assert(service.checkConflict("user1", "repo5", branch, issueId).isEmpty)
      assert(service.checkConflictCache("user1", "repo5", branch, issueId) == Some(None))
      Using.resource(Git.open(repo5Dir)) { git =>
        createFile(git, s"refs/heads/${branch}", "test.txt", "hoge2")
      }
      assert(service.checkConflictCache("user1", "repo5", branch, issueId) == None)
    }
    it("conflicted cache invalid if request branch moved") {
      val repo6Dir = initRepository("user1", "repo6")
      Using.resource(Git.open(repo6Dir)) { git =>
        createConfrict(git)
      }
      assert(service.checkConflict("user1", "repo6", branch, issueId).isDefined)
      assert(service.checkConflictCache("user1", "repo6", branch, issueId) match {
        case Some(Some(_: String)) => true
        case _                     => false
      })
      Using.resource(Git.open(repo6Dir)) { git =>
        createFile(git, s"refs/pull/${issueId}/head", "test.txt", "hoge4")
      }
      assert(service.checkConflictCache("user1", "repo6", branch, issueId) == None)
    }
    it("conflicted cache invalid if origin branch moved") {
      val repo7Dir = initRepository("user1", "repo7")
      Using.resource(Git.open(repo7Dir)) { git =>
        createConfrict(git)
      }
      assert(service.checkConflict("user1", "repo7", branch, issueId).isDefined)
      assert(service.checkConflictCache("user1", "repo7", branch, issueId) match {
        case Some(Some(_)) => true
        case _             => false
      })
      Using.resource(Git.open(repo7Dir)) { git =>
        createFile(git, s"refs/heads/${branch}", "test.txt", "hoge4")
      }
      assert(service.checkConflictCache("user1", "repo7", branch, issueId) == None)
    }
  }
  describe("mergePullRequest") {
    it("can merge") {
      import gitbucket.core.util.Implicits._
      implicit val context =
        Context(createSystemSettings(), Some(createAccount("dummy2", "dummy2-fullname", "dummy2@example.com")), request)

      val repo8Dir = initRepository("user1", "repo8")
      Using.resource(Git.open(repo8Dir)) { git =>
        createFile(git, s"refs/pull/${issueId}/head", "test.txt", "hoge2")
        assert(getFile(git, branch, "test.txt").content.get == "hoge")

        val requestBranchId = git.getRepository.resolve(s"refs/pull/${issueId}/head")
        val masterId = git.getRepository.resolve(branch)
        val repository = createRepositoryInfo("user1", "repo8")

        withTestDB { implicit session =>
          service.mergeWithMergeCommit(
            git,
            repository,
            branch,
            issueId,
            "merged",
            context.loginAccount.get,
            context.settings
          )
        }

        val lastCommitId = git.getRepository.resolve(branch)
        val commit = Using.resource(new RevWalk(git.getRepository))(_.parseCommit(lastCommitId))
        assert(commit.getCommitterIdent().getName == "dummy2-fullname")
        assert(commit.getCommitterIdent().getEmailAddress == "dummy2@example.com")
        assert(commit.getAuthorIdent().getName == "dummy2-fullname")
        assert(commit.getAuthorIdent().getEmailAddress == "dummy2@example.com")
        assert(commit.getFullMessage() == "merged")
        assert(commit.getParents.toSet == Set(requestBranchId, masterId))
        assert(getFile(git, branch, "test.txt").content.get == "hoge2")
      }
    }
  }

  private def createAccount(userName: String, fullName: String, mailAddress: String): Account =
    Account(
      userName = userName,
      fullName = fullName,
      mailAddress = mailAddress,
      password = "password",
      isAdmin = false,
      url = None,
      registeredDate = new Date(),
      updatedDate = new Date(),
      lastLoginDate = None,
      image = None,
      isGroupAccount = false,
      isRemoved = false,
      description = None
    )

  private def createRepositoryInfo(userName: String, repositoryName: String): RepositoryInfo =
    RepositoryInfo(
      owner = userName,
      name = repositoryName,
      repository = gitbucket.core.model.Repository(
        userName = userName,
        repositoryName = repositoryName,
        isPrivate = false,
        description = None,
        defaultBranch = "master",
        registeredDate = new Date(),
        updatedDate = new Date(),
        lastActivityDate = new Date(),
        originUserName = None,
        originRepositoryName = None,
        parentUserName = None,
        parentRepositoryName = None,
        options = RepositoryOptions(
          issuesOption = "PUBLIC",
          externalIssuesUrl = None,
          wikiOption = "PUBLIC",
          externalWikiUrl = None,
          allowFork = true,
          mergeOptions = "merge-commit,squash,rebase",
          defaultMergeOption = "merge-commit"
        )
      ),
      issueCount = 0,
      pullCount = 0,
      forkedCount = 0,
      milestoneCount = 0,
      branchList = Nil,
      tags = Nil,
      managers = Nil
    )
}
