package gitbucket.core.service

import gitbucket.core.model.*
import gitbucket.core.util.GitSpecUtil.*
import gitbucket.core.util.JGitUtil
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.treewalk.TreeWalk
import org.scalatest.funspec.AnyFunSpec

import scala.collection.mutable
import scala.util.Using

class PullRequestServiceSpec
    extends AnyFunSpec
    with ServiceSpecBase
    with MergeService
    with PullRequestService
    with IssuesService
    with AccountService
    with ActivityService
    with RepositoryService
    with CommitsService
    with LabelsService
    with MilestonesService
    with PrioritiesService
    with WebHookService
    with WebHookPullRequestService
    with WebHookPullRequestReviewCommentService
    with RequestCache {

  private def swap(r: (Issue, PullRequest)) = (r._2 -> r._1)

  /**
   * Returns a map of (path -> content) for all files in the tree of the given branch.
   */
  private def getFiles(git: Git, branch: String): Map[String, String] = {
    val repository = git.getRepository
    val headId = repository.resolve(s"refs/heads/$branch")
    if (headId == null) return Map.empty
    Using.resource(new org.eclipse.jgit.revwalk.RevWalk(repository)) { revWalk =>
      val commit = revWalk.parseCommit(headId)
      val tree = commit.getTree
      val files = mutable.Map[String, String]()
      Using.resource(new TreeWalk(repository)) { walk =>
        walk.addTree(tree)
        walk.setRecursive(true)
        while (walk.next()) {
          val path = walk.getPathString
          val objectId = walk.getObjectId(0)
          val loader = repository.open(objectId)
          val content = new String(loader.getBytes, "UTF-8")
          files(path) = content
        }
      }
      files.toMap
    }
  }

  describe("PullRequestService.getPullRequestFromBranch") {
    it("""should
    |return pull request if exists pull request from `branch` to `defaultBranch` and not closed.
    |return pull request if exists pull request from `branch` to other branch and not closed.
    |return None if all pull request is closed""".stripMargin.trim) {
      withTestDB { implicit se =>
        generateNewUserWithDBRepository("user1", "repo1")
        generateNewUserWithDBRepository("user1", "repo2")
        generateNewUserWithDBRepository("user2", "repo1")
        generateNewPullRequest("user1/repo1/master", "user1/repo1/head2", loginUser = "root") // not target branch
        generateNewPullRequest(
          "user1/repo1/head1",
          "user1/repo1/master",
          loginUser = "root"
        ) // not target branch ( swap from, to )
        generateNewPullRequest("user1/repo1/master", "user2/repo1/head1", loginUser = "root") // other user
        generateNewPullRequest("user1/repo1/master", "user1/repo2/head1", loginUser = "root") // other repository
        val r1 = swap(generateNewPullRequest("user1/repo1/master2", "user1/repo1/head1", loginUser = "root"))
        val r2 = swap(generateNewPullRequest("user1/repo1/master", "user1/repo1/head1", loginUser = "root"))
        val r3 = swap(generateNewPullRequest("user1/repo1/master4", "user1/repo1/head1", loginUser = "root"))
        assert(getPullRequestFromBranch("user1", "repo1", "head1", "master") == Some(r2))
        updateClosed("user1", "repo1", r2._1.issueId, true)
        assert(Seq(r1, r2).contains(getPullRequestFromBranch("user1", "repo1", "head1", "master").get))
        updateClosed("user1", "repo1", r1._1.issueId, true)
        updateClosed("user1", "repo1", r3._1.issueId, true)
        assert(getPullRequestFromBranch("user1", "repo1", "head1", "master") == None)
      }
    }
  }

  describe("PullRequestService.createRevertCommit") {
    it("should revert a single commit that modified a file") {
      withTestRepository { git =>
        createFile(git, Constants.HEAD, "README.md", "original content", message = "initial commit")
        val commitToRevert = createFile(git, Constants.HEAD, "README.md", "modified content", message = "modify readme")

        assert(getFiles(git, "main")("README.md") == "modified content")

        val result = createRevertCommit(
          git,
          "main",
          Seq(commitToRevert.getName),
          "test-user",
          "test@example.com",
          "Revert modify readme"
        )

        assert(result.isRight)
        assert(getFiles(git, "main")("README.md") == "original content")
      }
    }

    it("should revert a single commit that added a new file") {
      withTestRepository { git =>
        createFile(git, Constants.HEAD, "README.md", "readme content", message = "initial commit")
        val commitToRevert =
          createFile(git, Constants.HEAD, "NEW_FILE.txt", "new file content", message = "add new file")

        assert(getFiles(git, "main").contains("NEW_FILE.txt"))

        val result = createRevertCommit(
          git,
          "main",
          Seq(commitToRevert.getName),
          "test-user",
          "test@example.com",
          "Revert add new file"
        )

        assert(result.isRight)
        val files = getFiles(git, "main")
        assert(files.contains("README.md"))
        assert(!files.contains("NEW_FILE.txt"))
      }
    }

    it("should revert a commit that deleted a file") {
      withTestRepository { git =>
        createFile(git, Constants.HEAD, "README.md", "readme content", message = "initial commit")
        createFile(git, Constants.HEAD, "TO_DELETE.txt", "delete me", message = "add file to delete")

        // Delete file by creating a commit without it
        val repository = git.getRepository
        val inserter = repository.newObjectInserter()
        val headId = repository.resolve("main^{commit}")
        val builder = org.eclipse.jgit.dircache.DirCache.newInCore.builder()
        JGitUtil.processTree(git, headId) { (path, tree) =>
          if (path != "TO_DELETE.txt") {
            builder.add(JGitUtil.createDirCacheEntry(path, tree.getEntryFileMode, tree.getEntryObjectId))
          }
        }
        builder.finish()
        val deleteCommitId = JGitUtil.createNewCommit(
          git,
          inserter,
          headId,
          builder.getDirCache.writeTree(inserter),
          Constants.HEAD,
          "dummy",
          "dummy@example.com",
          "delete file"
        )
        inserter.flush()
        inserter.close()

        assert(!getFiles(git, "main").contains("TO_DELETE.txt"))

        val result = createRevertCommit(
          git,
          "main",
          Seq(deleteCommitId.getName),
          "test-user",
          "test@example.com",
          "Revert delete file"
        )

        assert(result.isRight)
        val files = getFiles(git, "main")
        assert(files.contains("TO_DELETE.txt"))
        assert(files("TO_DELETE.txt") == "delete me")
        assert(files.contains("README.md"))
      }
    }

    it("should revert multiple commits in sequence") {
      withTestRepository { git =>
        createFile(git, Constants.HEAD, "README.md", "v1", message = "initial commit")
        val commit1 = createFile(git, Constants.HEAD, "README.md", "v2", message = "update to v2")
        val commit2 = createFile(git, Constants.HEAD, "README.md", "v3", message = "update to v3")

        assert(getFiles(git, "main")("README.md") == "v3")

        val result = createRevertCommit(
          git,
          "main",
          Seq(commit1.getName, commit2.getName),
          "test-user",
          "test@example.com",
          "Revert two commits"
        )

        assert(result.isRight)
        assert(getFiles(git, "main")("README.md") == "v1")
      }
    }

    it("should preserve unrelated files") {
      withTestRepository { git =>
        createFile(git, Constants.HEAD, "README.md", "readme content", message = "initial commit")
        createFile(git, Constants.HEAD, "OTHER.txt", "other content", message = "add other file")
        val commitToRevert =
          createFile(git, Constants.HEAD, "README.md", "changed readme", message = "modify readme only")

        val result = createRevertCommit(
          git,
          "main",
          Seq(commitToRevert.getName),
          "test-user",
          "test@example.com",
          "Revert readme change"
        )

        assert(result.isRight)
        val files = getFiles(git, "main")
        assert(files("README.md") == "readme content")
        assert(files("OTHER.txt") == "other content")
      }
    }

    it("should return Left for non-existent branch") {
      withTestRepository { git =>
        createFile(git, Constants.HEAD, "README.md", "content", message = "initial commit")
        val commitId = git.getRepository.resolve("main").getName

        val result = createRevertCommit(
          git,
          "nonexistent-branch",
          Seq(commitId),
          "test-user",
          "test@example.com",
          "Revert"
        )

        assert(result.isLeft)
      }
    }

    it("should revert commit with files in subdirectories") {
      withTestRepository { git =>
        createFile(git, Constants.HEAD, "README.md", "readme", message = "initial commit")
        createFile(git, Constants.HEAD, "src/main/Hello.scala", "hello v1", message = "add hello")
        val commitToRevert =
          createFile(git, Constants.HEAD, "src/main/Hello.scala", "hello v2", message = "modify hello")

        assert(getFiles(git, "main")("src/main/Hello.scala") == "hello v2")

        val result = createRevertCommit(
          git,
          "main",
          Seq(commitToRevert.getName),
          "test-user",
          "test@example.com",
          "Revert hello change"
        )

        assert(result.isRight)
        val files = getFiles(git, "main")
        assert(files("src/main/Hello.scala") == "hello v1")
        assert(files("README.md") == "readme")
      }
    }

    it("should create proper commit metadata") {
      withTestRepository { git =>
        createFile(git, Constants.HEAD, "README.md", "v1", message = "initial commit")
        val commitToRevert = createFile(git, Constants.HEAD, "README.md", "v2", message = "modify")

        val result = createRevertCommit(
          git,
          "main",
          Seq(commitToRevert.getName),
          "revert-author",
          "revert@example.com",
          "Revert: modify"
        )

        assert(result.isRight)

        val headId = git.getRepository.resolve("refs/heads/main")
        Using.resource(new org.eclipse.jgit.revwalk.RevWalk(git.getRepository)) { revWalk =>
          val revertCommit = revWalk.parseCommit(headId)

          assert(revertCommit.getFullMessage == "Revert: modify")
          assert(revertCommit.getAuthorIdent.getName == "revert-author")
          assert(revertCommit.getAuthorIdent.getEmailAddress == "revert@example.com")
          assert(revertCommit.getParentCount == 1)
          assert(revertCommit.getParent(0).getId.getName == commitToRevert.getName)
        }
      }
    }

    it("should revert a commit that adds multiple files at once") {
      withTestRepository { git =>
        createFile(git, Constants.HEAD, "README.md", "readme", message = "initial commit")

        // Create a commit that adds two files using the low-level API
        val repository = git.getRepository
        val inserter = repository.newObjectInserter()
        val headId = repository.resolve("main^{commit}")
        val builder = org.eclipse.jgit.dircache.DirCache.newInCore.builder()
        JGitUtil.processTree(git, headId) { (path, tree) =>
          builder.add(JGitUtil.createDirCacheEntry(path, tree.getEntryFileMode, tree.getEntryObjectId))
        }
        builder.add(
          JGitUtil.createDirCacheEntry(
            "file1.txt",
            org.eclipse.jgit.lib.FileMode.REGULAR_FILE,
            inserter.insert(Constants.OBJ_BLOB, "file1 content".getBytes("UTF-8"))
          )
        )
        builder.add(
          JGitUtil.createDirCacheEntry(
            "file2.txt",
            org.eclipse.jgit.lib.FileMode.REGULAR_FILE,
            inserter.insert(Constants.OBJ_BLOB, "file2 content".getBytes("UTF-8"))
          )
        )
        builder.finish()
        val multiAddCommitId = JGitUtil.createNewCommit(
          git,
          inserter,
          headId,
          builder.getDirCache.writeTree(inserter),
          Constants.HEAD,
          "dummy",
          "dummy@example.com",
          "add two files"
        )
        inserter.flush()
        inserter.close()

        val filesBefore = getFiles(git, "main")
        assert(filesBefore.contains("file1.txt"))
        assert(filesBefore.contains("file2.txt"))

        val result = createRevertCommit(
          git,
          "main",
          Seq(multiAddCommitId.getName),
          "test-user",
          "test@example.com",
          "Revert multi add"
        )

        assert(result.isRight)
        val filesAfter = getFiles(git, "main")
        assert(filesAfter.contains("README.md"))
        assert(!filesAfter.contains("file1.txt"))
        assert(!filesAfter.contains("file2.txt"))
      }
    }
  }
}
