package gitbucket.core.api

import gitbucket.core.TestingGitBucketServer
import org.apache.commons.io.IOUtils
import org.eclipse.jgit.api.Git
import org.scalatest.funsuite.AnyFunSuite

import scala.util.Using
import org.kohsuke.github.GHCommitState

import java.io.File

/**
 * Need to run `sbt package` before running this test.
 */
class ApiIntegrationTest extends AnyFunSuite {

  test("create repository") {
    Using.resource(new TestingGitBucketServer(19999)) { server =>
      val github = server.client("root", "root")

      {
        val repository = github
          .createRepository("test")
          .description("test repository")
          .private_(false)
          .autoInit(true)
          .create()

        assert(repository.getName == "test")
        assert(repository.getDescription == "test repository")
        assert(repository.getDefaultBranch == "master")
        assert(repository.getWatchers == 0)
        assert(repository.getWatchersCount == 0)
        assert(repository.getForks == 0)
        assert(repository.getForksCount == 0)
        assert(repository.isPrivate == false)
        assert(repository.getOwner.getLogin == "root")
        assert(repository.hasIssues == true)
        assert(repository.getUrl.toString == s"http://localhost:${server.port}/api/v3/repos/root/test")
        assert(repository.getHttpTransportUrl == s"http://localhost:${server.port}/git/root/test.git")
        assert(repository.getHtmlUrl.toString == s"http://localhost:${server.port}/root/test")
      }
      {
        val repositories = github.getUser("root").listRepositories().toList
        assert(repositories.size() == 1)

        val repository = repositories.get(0)
        assert(repository.getName == "test")
        assert(repository.getDescription == "test repository")
        assert(repository.getDefaultBranch == "master")
        assert(repository.getWatchers == 0)
        assert(repository.getWatchersCount == 0)
        assert(repository.getForks == 0)
        assert(repository.getForksCount == 0)
        assert(repository.isPrivate == false)
        assert(repository.getOwner.getLogin == "root")
        assert(repository.hasIssues == true)
        assert(repository.getUrl.toString == s"http://localhost:${server.port}/api/v3/repos/root/test")
        assert(repository.getHttpTransportUrl == s"http://localhost:${server.port}/git/root/test.git")
        assert(repository.getHtmlUrl.toString == s"http://localhost:${server.port}/root/test")
      }
    }
  }

  test("commit status") {
    Using.resource(new TestingGitBucketServer(19999)) { server =>
      val github = server.client("root", "root")

      val repo = github.createRepository("create_status_test").autoInit(true).create()
      val sha1 = repo.getBranch("master").getSHA1

      {
        val status = repo.getLastCommitStatus(sha1)
        assert(status == null)
      }
      {
        val statusList = repo.listCommitStatuses(sha1).toList
        assert(statusList.size() == 0)
      }
      {
        val status =
          repo.createCommitStatus(sha1, GHCommitState.SUCCESS, "http://localhost/target", "description", "context")
        assert(status.getState == GHCommitState.SUCCESS)
        assert(status.getTargetUrl == "http://localhost/target")
        assert(status.getDescription == "description")
        assert(status.getContext == "context")
        assert(
          status.getUrl.toString == s"http://localhost:19999/api/v3/repos/root/create_status_test/commits/${sha1}/statuses"
        )
      }
      {
        val status = repo.getLastCommitStatus(sha1)
        assert(status.getState == GHCommitState.SUCCESS)
        assert(status.getTargetUrl == "http://localhost/target")
        assert(status.getDescription == "description")
        assert(status.getContext == "context")
        assert(
          status.getUrl.toString == s"http://localhost:19999/api/v3/repos/root/create_status_test/commits/${sha1}/statuses"
        )
      }
      {
        val statusList = repo.listCommitStatuses(sha1).toList
        assert(statusList.size() == 1)

        val status = repo.getLastCommitStatus(sha1)
        assert(status.getState == GHCommitState.SUCCESS)
        assert(status.getTargetUrl == "http://localhost/target")
        assert(status.getDescription == "description")
        assert(status.getContext == "context")
        assert(
          status.getUrl.toString == s"http://localhost:19999/api/v3/repos/root/create_status_test/commits/${sha1}/statuses"
        )
      }
      {
        // Update the status
        repo.createCommitStatus(sha1, GHCommitState.FAILURE, "http://localhost/target", "description", "context")

        val status = repo.getLastCommitStatus(sha1)
        assert(status.getState == GHCommitState.FAILURE)

        val statusList = repo.listCommitStatuses(sha1).toList
        assert(statusList.size() == 1)
        assert(statusList.get(0).getState == GHCommitState.FAILURE)
      }
      {
        // Add status in a different context
        repo.createCommitStatus(sha1, GHCommitState.ERROR, "http://localhost/target", "description", "context2")

        val status = repo.getLastCommitStatus(sha1)
        assert(status.getState == GHCommitState.ERROR)

        val statusList = repo.listCommitStatuses(sha1).toList
        assert(statusList.size() == 2)
        assert(statusList.get(0).getState == GHCommitState.ERROR)
        assert(statusList.get(0).getContext == "context2")
        assert(statusList.get(1).getState == GHCommitState.FAILURE)
        assert(statusList.get(1).getContext == "context")
      }

      // get master ref
      {
        val ref = repo.getRef("heads/master")
        assert(ref.getRef == "refs/heads/master")
        assert(
          ref.getUrl.toString == "http://localhost:19999/api/v3/repos/root/create_status_test/git/refs/heads/master"
        )
        assert(ref.getObject.getType == "commit")
      }

      // get tag v1.0
      {
        Using.resource(Git.open(new File(server.getDirectory(), "repositories/root/create_status_test"))) { git =>
          git.tag().setName("v1.0").call()
        }
        val ref = repo.getRef("tags/v1.0")
        assert(ref.getRef == "refs/tags/v1.0")
        assert(ref.getUrl.toString == "http://localhost:19999/api/v3/repos/root/create_status_test/git/refs/tags/v1.0")
      }
    }
  }

  test("create and update contents") {
    Using.resource(new TestingGitBucketServer(19999)) { server =>
      val github = server.client("root", "root")

      val repo = github.createRepository("create_contents_test").autoInit(true).create()

      val createResult =
        repo
          .createContent()
          .branch("master")
          .content("create")
          .message("Create content")
          .path("README.md")
          .commit()

      assert(createResult.getContent.isFile == true)
      assert(IOUtils.toString(createResult.getContent.read(), "UTF-8") == "create")

      val content1 = repo.getFileContent("README.md")
      assert(content1.isFile == true)
      assert(IOUtils.toString(content1.read(), "UTF-8") == "create")
      assert(content1.getSha == createResult.getContent.getSha)

      val updateResult =
        repo
          .createContent()
          .branch("master")
          .content("update")
          .message("Update content")
          .path("README.md")
          .sha(content1.getSha)
          .commit()

      assert(updateResult.getContent.isFile == true)
      assert(IOUtils.toString(updateResult.getContent.read(), "UTF-8") == "update")

      val content2 = repo.getFileContent("README.md")
      assert(content2.isFile == true)
      assert(IOUtils.toString(content2.read(), "UTF-8") == "update")
      assert(content2.getSha == updateResult.getContent.getSha)
      assert(content1.getSha != content2.getSha)
    }
  }

  test("issue labels") {
    Using.resource(new TestingGitBucketServer(19999)) { server =>
      val github = server.client("root", "root")

      val repo = github.createRepository("issue_label_test").autoInit(true).create()
      val issue = repo.createIssue("test").create()

      // Initial label state
      {
        val labels = repo.getIssue(issue.getNumber).getLabels
        assert(labels.size() == 0)
      }

      // Add labels
      {
        issue.addLabels("bug", "duplicate")

        val labels = repo.getIssue(issue.getNumber).getLabels
        assert(labels.size() == 2)

        val i = labels.iterator()
        val label1 = i.next()
        assert(label1.getName == "bug")
        assert(label1.getColor == "fc2929")
        assert(label1.getUrl == "http://localhost:19999/api/v3/repos/root/issue_label_test/labels/bug")

        val label2 = i.next()
        assert(label2.getName == "duplicate")
        assert(label2.getColor == "cccccc")
        assert(label2.getUrl == "http://localhost:19999/api/v3/repos/root/issue_label_test/labels/duplicate")
      }

      // Remove a label
      {
        issue.removeLabel("duplicate")

        val labels = repo.getIssue(issue.getNumber).getLabels
        assert(labels.size() == 1)

        val i = labels.iterator()
        val label1 = i.next()
        assert(label1.getName == "bug")
        assert(label1.getColor == "fc2929")
        assert(label1.getUrl == "http://localhost:19999/api/v3/repos/root/issue_label_test/labels/bug")
      }

    // Replace labels (Cannot test because GHLabel.setLabels() doesn't use the replace endpoint)
//      {
//        issue.setLabels("enhancement", "invalid", "question")
//
//        val labels = repo.getIssue(issue.getNumber).getLabels
//        assert(labels.size() == 3)
//
//        val i = labels.iterator()
//        val label1 = i.next()
//        assert(label1.getName == "enhancement")
//        assert(label1.getColor == "84b6eb")
//        assert(label1.getUrl == "http://localhost:19999/api/v3/repos/root/issue_label_test/labels/enhancement")
//
//        val label2 = i.next()
//        assert(label2.getName == "invalid")
//        assert(label2.getColor == "e6e6e6")
//        assert(label2.getUrl == "http://localhost:19999/api/v3/repos/root/issue_label_test/labels/invalid")
//
//        val label3 = i.next()
//        assert(label3.getName == "question")
//        assert(label3.getColor == "cc317c")
//        assert(label3.getUrl == "http://localhost:19999/api/v3/repos/root/issue_label_test/labels/question")
//      }
    }
  }

  test("Git refs APIs") {
    Using.resource(new TestingGitBucketServer(19999)) { server =>
      val github = server.client("root", "root")

      val repo = github.createRepository("git_refs_test").autoInit(true).create()
      val sha1 = repo.getBranch("master").getSHA1

      val refs1 = repo.listRefs().toList
      assert(refs1.size() == 1)
      assert(refs1.get(0).getRef == "refs/heads/master")
      assert(refs1.get(0).getObject.getSha == sha1)

      val ref = repo.createRef("refs/heads/testref", sha1)
      assert(ref.getRef == "refs/heads/testref")
      assert(ref.getObject.getSha == sha1)

      val refs2 = repo.listRefs().toList
      assert(refs2.size() == 2)
      assert(refs2.get(0).getRef == "refs/heads/master")
      assert(refs2.get(0).getObject.getSha == sha1)
      assert(refs2.get(1).getRef == "refs/heads/testref")
      assert(refs2.get(1).getObject.getSha == sha1)
    }
  }
}
