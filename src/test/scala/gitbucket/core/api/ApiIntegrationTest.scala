package gitbucket.core.api

import gitbucket.core.TestingGitBucketServer
import org.scalatest.funsuite.AnyFunSuite

import scala.util.Using
import org.kohsuke.github.{GHCommitState, GitHub}

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
    }
  }

}
