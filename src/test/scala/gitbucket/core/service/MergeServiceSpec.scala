package gitbucket.core.service

import gitbucket.core.util.Directory._
import gitbucket.core.util.ControlUtil._
import gitbucket.core.util.GitSpecUtil._

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib._
import org.eclipse.jgit.revwalk._
import org.scalatest.FunSpec

import java.io.File

class MergeServiceSpec extends FunSpec {
  val service = new MergeService{}
  val branch = "master"
  val issueId = 10
  def initRepository(owner:String, name:String): File = {
    val dir = createTestRepository(getRepositoryDir(owner, name))
    using(Git.open(dir)){ git =>
      createFile(git, s"refs/heads/master", "test.txt", "hoge" )
      git.branchCreate().setStartPoint(s"refs/heads/master").setName(s"refs/pull/${issueId}/head").call()
    }
    dir
  }
  def createConfrict(git:Git) = {
    createFile(git, s"refs/heads/${branch}", "test.txt", "hoge2" )
    createFile(git, s"refs/pull/${issueId}/head", "test.txt", "hoge4" )
  }
  describe("checkConflict, checkConflictCache") {
    it("checkConflict false if not conflicted, and create cache") {
      val repo1Dir = initRepository("user1","repo1")
      assert(service.checkConflictCache("user1", "repo1", branch, issueId) == None)
      val conflicted = service.checkConflict("user1", "repo1", branch, issueId)
      assert(service.checkConflictCache("user1", "repo1", branch, issueId) == Some(false))
      assert(conflicted  == false)
    }
    it("checkConflict true if not conflicted, and create cache") {
      val repo2Dir = initRepository("user1","repo2")
      using(Git.open(repo2Dir)){ git =>
        createConfrict(git)
      }
      assert(service.checkConflictCache("user1", "repo2", branch, issueId) == None)
      val conflicted = service.checkConflict("user1", "repo2", branch, issueId)
      assert(conflicted  == true)
      assert(service.checkConflictCache("user1", "repo2", branch, issueId) == Some(true))
    }
  }
  describe("checkConflictCache") {
    it("merged cache invalid if origin branch moved") {
      val repo3Dir = initRepository("user1","repo3")
      assert(service.checkConflict("user1", "repo3", branch, issueId) == false)
      assert(service.checkConflictCache("user1", "repo3", branch, issueId) == Some(false))
      using(Git.open(repo3Dir)){ git =>
        createFile(git, s"refs/heads/${branch}", "test.txt", "hoge2" )
      }
      assert(service.checkConflictCache("user1", "repo3", branch, issueId) == None)
    }
    it("merged cache invalid if request branch moved") {
      val repo4Dir = initRepository("user1","repo4")
      assert(service.checkConflict("user1", "repo4", branch, issueId) == false)
      assert(service.checkConflictCache("user1", "repo4", branch, issueId) == Some(false))
      using(Git.open(repo4Dir)){ git =>
        createFile(git, s"refs/pull/${issueId}/head", "test.txt", "hoge4" )
      }
      assert(service.checkConflictCache("user1", "repo4", branch, issueId) == None)
    }
    it("should merged cache invalid if origin branch moved") {
      val repo5Dir = initRepository("user1","repo5")
      assert(service.checkConflict("user1", "repo5", branch, issueId) == false)
      assert(service.checkConflictCache("user1", "repo5", branch, issueId) == Some(false))
      using(Git.open(repo5Dir)){ git =>
        createFile(git, s"refs/heads/${branch}", "test.txt", "hoge2" )
      }
      assert(service.checkConflictCache("user1", "repo5", branch, issueId) == None)
    }
    it("conflicted cache invalid if request branch moved") {
      val repo6Dir = initRepository("user1","repo6")
      using(Git.open(repo6Dir)){ git =>
        createConfrict(git)
      }
      assert(service.checkConflict("user1", "repo6", branch, issueId) == true)
      assert(service.checkConflictCache("user1", "repo6", branch, issueId) == Some(true))
      using(Git.open(repo6Dir)){ git =>
        createFile(git, s"refs/pull/${issueId}/head", "test.txt", "hoge4" )
      }
      assert(service.checkConflictCache("user1", "repo6", branch, issueId) == None)
    }
    it("conflicted cache invalid if origin branch moved") {
      val repo7Dir = initRepository("user1","repo7")
      using(Git.open(repo7Dir)){ git =>
        createConfrict(git)
      }
      assert(service.checkConflict("user1", "repo7", branch, issueId) == true)
      assert(service.checkConflictCache("user1", "repo7", branch, issueId) == Some(true))
      using(Git.open(repo7Dir)){ git =>
        createFile(git, s"refs/heads/${branch}", "test.txt", "hoge4" )
      }
      assert(service.checkConflictCache("user1", "repo7", branch, issueId) == None)
    }
  }
  describe("mergePullRequest") {
    it("can merge") {
      val repo8Dir = initRepository("user1","repo8")
      using(Git.open(repo8Dir)){ git =>
        createFile(git, s"refs/pull/${issueId}/head", "test.txt", "hoge2" )
        val committer = new PersonIdent("dummy2", "dummy2@example.com")
        assert(getFile(git, branch, "test.txt").content.get == "hoge")
        val requestBranchId = git.getRepository.resolve(s"refs/pull/${issueId}/head")
        val masterId = git.getRepository.resolve(branch)
        service.mergePullRequest(git, branch, issueId, "merged", committer)
        val lastCommitId = git.getRepository.resolve(branch)
        val commit = using(new RevWalk(git.getRepository))(_.parseCommit(lastCommitId))
        assert(commit.getCommitterIdent() == committer)
        assert(commit.getAuthorIdent() == committer)
        assert(commit.getFullMessage() == "merged")
        assert(commit.getParents.toSet == Set( requestBranchId, masterId ))
        assert(getFile(git, branch, "test.txt").content.get == "hoge2")
      }
    }
  }
}