package gitbucket.core.service

import gitbucket.core.util.Directory._
import gitbucket.core.util.GitSpecUtil._

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib._
import org.eclipse.jgit.revwalk._
import org.scalatest.FunSpec

import java.io.File
import scala.util.Using

class MergeServiceSpec extends FunSpec {
  val service = new MergeService with AccountService with ActivityService with IssuesService with LabelsService
  with MilestonesService with RepositoryService with PrioritiesService with PullRequestService with CommitsService
  with WebHookPullRequestService with WebHookPullRequestReviewCommentService {}
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
      val repo8Dir = initRepository("user1", "repo8")
      Using.resource(Git.open(repo8Dir)) { git =>
        createFile(git, s"refs/pull/${issueId}/head", "test.txt", "hoge2")
        val committer = new PersonIdent("dummy2", "dummy2@example.com")
        assert(getFile(git, branch, "test.txt").content.get == "hoge")
        val requestBranchId = git.getRepository.resolve(s"refs/pull/${issueId}/head")
        val masterId = git.getRepository.resolve(branch)
        service.mergePullRequest(git, branch, issueId, "merged", committer)
        val lastCommitId = git.getRepository.resolve(branch)
        val commit = Using.resource(new RevWalk(git.getRepository))(_.parseCommit(lastCommitId))
        assert(commit.getCommitterIdent() == committer)
        assert(commit.getAuthorIdent() == committer)
        assert(commit.getFullMessage() == "merged")
        assert(commit.getParents.toSet == Set(requestBranchId, masterId))
        assert(getFile(git, branch, "test.txt").content.get == "hoge2")
      }
    }
  }
}
