package gitbucket.core.service

import gitbucket.core.model._
import gitbucket.core.util.JGitUtil
import gitbucket.core.util.Directory._
import gitbucket.core.util.Implicits._
import gitbucket.core.util.ControlUtil._
import gitbucket.core.util.GitSpecUtil._

import org.apache.commons.io.FileUtils
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.dircache.DirCache
import org.eclipse.jgit.lib._
import org.eclipse.jgit.revwalk._
import org.eclipse.jgit.treewalk._
import org.specs2.mutable.Specification

import java.io.File
import java.nio.file._
import java.util.Date

class MergeServiceSpec extends Specification {
  sequential
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
  "checkConflict, checkConflictCache" should {
    "checkConflict false if not conflicted, and create cache" in {
      val repo1Dir = initRepository("user1","repo1")
      service.checkConflictCache("user1", "repo1", branch, issueId) mustEqual None
      val conflicted = service.checkConflict("user1", "repo1", branch, issueId)
      service.checkConflictCache("user1", "repo1", branch, issueId) mustEqual Some(false)
      conflicted  mustEqual false
    }
    "checkConflict true if not conflicted, and create cache" in {
      val repo1Dir = initRepository("user1","repo1")
      using(Git.open(repo1Dir)){ git =>
        createConfrict(git)
      }
      service.checkConflictCache("user1", "repo1", branch, issueId) mustEqual None
      val conflicted = service.checkConflict("user1", "repo1", branch, issueId)
      conflicted  mustEqual true
      service.checkConflictCache("user1", "repo1", branch, issueId) mustEqual Some(true)
    }
  }
  "checkConflictCache" should {
    "merged cache invalid if origin branch moved" in {
      val repo1Dir = initRepository("user1","repo1")
      service.checkConflict("user1", "repo1", branch, issueId) mustEqual false
      service.checkConflictCache("user1", "repo1", branch, issueId) mustEqual Some(false)
      using(Git.open(repo1Dir)){ git =>
        createFile(git, s"refs/heads/${branch}", "test.txt", "hoge2" )
      }
      service.checkConflictCache("user1", "repo1", branch, issueId) mustEqual None
    }
    "merged cache invalid if request branch moved" in {
      val repo1Dir = initRepository("user1","repo1")
      service.checkConflict("user1", "repo1", branch, issueId) mustEqual false
      service.checkConflictCache("user1", "repo1", branch, issueId) mustEqual Some(false)
      using(Git.open(repo1Dir)){ git =>
        createFile(git, s"refs/pull/${issueId}/head", "test.txt", "hoge4" )
      }
      service.checkConflictCache("user1", "repo1", branch, issueId) mustEqual None
    }
    "merged cache invalid if origin branch moved" in {
      val repo1Dir = initRepository("user1","repo1")
      service.checkConflict("user1", "repo1", branch, issueId) mustEqual false
      service.checkConflictCache("user1", "repo1", branch, issueId) mustEqual Some(false)
      using(Git.open(repo1Dir)){ git =>
        createFile(git, s"refs/heads/${branch}", "test.txt", "hoge2" )
      }
      service.checkConflictCache("user1", "repo1", branch, issueId) mustEqual None
    }
    "conflicted cache invalid if request branch moved" in {
      val repo1Dir = initRepository("user1","repo1")
      using(Git.open(repo1Dir)){ git =>
        createConfrict(git)
      }
      service.checkConflict("user1", "repo1", branch, issueId) mustEqual true
      service.checkConflictCache("user1", "repo1", branch, issueId) mustEqual Some(true)
      using(Git.open(repo1Dir)){ git =>
        createFile(git, s"refs/pull/${issueId}/head", "test.txt", "hoge4" )
      }
      service.checkConflictCache("user1", "repo1", branch, issueId) mustEqual None
    }
    "conflicted cache invalid if origin branch moved" in {
      val repo1Dir = initRepository("user1","repo1")
      using(Git.open(repo1Dir)){ git =>
        createConfrict(git)
      }
      service.checkConflict("user1", "repo1", branch, issueId) mustEqual true
      service.checkConflictCache("user1", "repo1", branch, issueId) mustEqual Some(true)
      using(Git.open(repo1Dir)){ git =>
        createFile(git, s"refs/heads/${branch}", "test.txt", "hoge4" )
      }
      service.checkConflictCache("user1", "repo1", branch, issueId) mustEqual None
    }
  }
  "mergePullRequest" should {
    "can merge" in {
      val repo1Dir = initRepository("user1","repo1")
      using(Git.open(repo1Dir)){ git =>
        createFile(git, s"refs/pull/${issueId}/head", "test.txt", "hoge2" )
        val committer = new PersonIdent("dummy2", "dummy2@example.com")
        getFile(git, branch, "test.txt").content.get mustEqual "hoge"
        val requestBranchId = git.getRepository.resolve(s"refs/pull/${issueId}/head")
        val masterId = git.getRepository.resolve(branch)
        service.mergePullRequest(git, branch, issueId, "merged", committer)
        val lastCommitId = git.getRepository.resolve(branch);
        val commit = using(new RevWalk(git.getRepository))(_.parseCommit(lastCommitId))
        commit.getCommitterIdent() mustEqual committer
        commit.getAuthorIdent() mustEqual committer
        commit.getFullMessage() mustEqual "merged"
        commit.getParents.toSet mustEqual Set( requestBranchId, masterId )
        getFile(git, branch, "test.txt").content.get mustEqual "hoge2"
      }
    }
  }
}