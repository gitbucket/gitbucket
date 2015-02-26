package service
import org.specs2.mutable.Specification
import java.util.Date
import model._
import util.JGitUtil
import util.Directory._
import java.nio.file._
import util.Implicits._
import util.ControlUtil._
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.dircache.DirCache
import org.eclipse.jgit.lib._
import org.eclipse.jgit.treewalk._
import org.eclipse.jgit.revwalk._
import org.apache.commons.io.FileUtils

class MergeServiceSpec extends Specification {
  sequential
  val service = new MergeService{}
  val branch = "master"
  val issueId = 10
  def initRepository(owner:String, name:String) = {
      val repo1Dir = getRepositoryDir(owner, name)
      RepositoryCache.clear()
      FileUtils.deleteQuietly(repo1Dir)
      Files.createDirectories(repo1Dir.toPath())
      JGitUtil.initRepository(repo1Dir)
      using(Git.open(repo1Dir)){ git =>
        createFile(git, s"refs/heads/master", "test.txt", "hoge" )
        git.branchCreate().setStartPoint(s"refs/heads/master").setName(s"refs/pull/${issueId}/head").call()
      }
      repo1Dir
  }
  def createFile(git:Git, branch:String, name:String, content:String){
    val builder  = DirCache.newInCore.builder()
    val inserter = git.getRepository.newObjectInserter()
    val headId   = git.getRepository.resolve(branch + "^{commit}")
    builder.add(JGitUtil.createDirCacheEntry(name, FileMode.REGULAR_FILE,
      inserter.insert(Constants.OBJ_BLOB, content.getBytes("UTF-8"))))
    builder.finish()
    JGitUtil.createNewCommit(git, inserter, headId, builder.getDirCache.writeTree(inserter),
      branch, "dummy", "dummy@example.com", "Initial commit")
  }
  def getFile(git:Git, branch:String, path:String) = {
      val revCommit = JGitUtil.getRevCommitFromId(git, git.getRepository.resolve(branch))
        val objectId = using(new TreeWalk(git.getRepository)){ walk =>
          walk.addTree(revCommit.getTree)
          walk.setRecursive(true)
          @scala.annotation.tailrec
          def _getPathObjectId: ObjectId = walk.next match {
            case true if(walk.getPathString == path) => walk.getObjectId(0)
            case true  => _getPathObjectId
            case false => throw new Exception(s"not found ${branch} / ${path}")
          }
          _getPathObjectId
        }
        JGitUtil.getContentInfo(git, path, objectId)
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