package gitbucket.core.api

import gitbucket.core.TestingGitBucketServer
import gitbucket.core.api.ApiError
import org.apache.commons.io.IOUtils
import org.eclipse.jgit.api.Git
import org.json4s.{DefaultFormats, jvalue2extractable}
import org.json4s.jackson.JsonMethods.parse
import org.scalatest.funsuite.AnyFunSuite

import scala.util.Using
import org.kohsuke.github.{GHCommitState, GHFileNotFoundException}

import java.io.File
import java.util.logging.{Level, Logger}

/**
 * Need to run `sbt package` before running this test.
 */
class ApiIntegrationTest extends AnyFunSuite {
  implicit val formats: org.json4s.Formats = DefaultFormats

  // Suppress warning logs caused by liquibase
  private val liquibaseResourceLogger = Logger.getLogger("liquibase.resource")
  liquibaseResourceLogger.setLevel(Level.SEVERE)
  private val liquibaseParserLogger = Logger.getLogger("liquibase.parser")
  liquibaseParserLogger.setLevel(Level.SEVERE)

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
        assert(repository.getDefaultBranch == "main")
        assert(repository.getWatchersCount == 0)
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
        assert(repository.getDefaultBranch == "main")
        assert(repository.getWatchersCount == 0)
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
      val sha1 = repo.getBranch("main").getSHA1

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
        val ref = repo.getRef("heads/main")
        assert(ref.getRef == "refs/heads/main")
        assert(
          ref.getUrl.toString == "http://localhost:19999/api/v3/repos/root/create_status_test/git/refs/heads/main"
        )
        assert(ref.getObject.getType == "commit")
      }

      // get tag v1.0
      {
        Using.resource(Git.open(new File(server.getDirectory(), "repositories/root/create_status_test"))) { git =>
          git.tag().setName("v1.0").call().getPeeledObjectId
        }
        val ref = repo.getRef("tags/v1.0")
        assert(ref.getRef == "refs/tags/v1.0")
        assert(ref.getUrl.toString == "http://localhost:19999/api/v3/repos/root/create_status_test/git/refs/tags/v1.0")

        val tags = repo.listTags().toList
        assert(tags.size() == 1)
        assert(tags.get(0).getName == "v1.0")
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
          .branch("main")
          .content("create")
          .message("Create content")
          .path("test.txt")
          .commit()

      assert(createResult.getContent.isFile)
      assert(IOUtils.toString(createResult.getContent.read(), "UTF-8") == "create")

      val content1 = repo.getFileContent("test.txt")
      assert(content1.isFile)
      assert(IOUtils.toString(content1.read(), "UTF-8") == "create")
      assert(content1.getSha == createResult.getContent.getSha)

      val updateResult =
        repo
          .createContent()
          .branch("main")
          .content("update")
          .message("Update content")
          .path("test.txt")
          .sha(content1.getSha)
          .commit()

      assert(updateResult.getContent.isFile)
      assert(IOUtils.toString(updateResult.getContent.read(), "UTF-8") == "update")

      val content2 = repo.getFileContent("test.txt")
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

  test("GET /repositories/:id returns the repository") {
    Using.resource(new TestingGitBucketServer(19999)) { server =>
      val github = server.client("root", "root")
      val repo = github.createRepository("id_lookup_test").autoInit(true).create()
      val id = repo.getId

      val found = github.getRepositoryById(id)
      assert(found.getFullName == repo.getFullName)
      assert(found.isFork == false)
    }
  }

  test("GET /repositories/:id with unknown ID returns 404") {
    Using.resource(new TestingGitBucketServer(19999)) { server =>
      val github = server.client("root", "root")
      assertThrows[GHFileNotFoundException] {
        github.getRepositoryById(999999999L)
      }
    }
  }

  test("GET /repositories/:id for a private repository without authentication returns 404") {
    Using.resource(new TestingGitBucketServer(19999)) { server =>
      val github = server.client("root", "root")
      val repo = github.createRepository("private_id_test").private_(true).autoInit(true).create()
      val id = repo.getId

      val status = server.getAnonymousApiStatus(s"/api/v3/repositories/$id")
      assert(status == 404, s"Expected 404 for unauthenticated access to private repo but got $status")
    }
  }

  test("GET /repositories/:id after repository rename resolves by original ID") {
    Using.resource(new TestingGitBucketServer(19999)) { server =>
      val github = server.client("root", "root")
      val repo = github.createRepository("pre-rename-id-test").autoInit(true).create()
      val id = repo.getId

      server.renameRepository("root", "pre-rename-id-test", "post-rename-id-test", "root", "root")

      val found = github.getRepositoryById(id)
      assert(found.getFullName == "root/post-rename-id-test")
    }
  }

  test("POST /repos/:owner/:repo/forks creates a fork via REST API") {
    Using.resource(new TestingGitBucketServer(19999)) { server =>
      val github = server.client("root", "root")
      val base = github.createRepository("fork_api_origin").autoInit(true).create()

      server.createUser("user3", "user3pass", "user3@example.com", "root", "root")
      val forkClient = server.client("user3", "user3pass")
      val response = server.forkRepositoryViaApi("root", "fork_api_origin", None, "user3", "user3pass")
      assert(response.status == 202, s"Expected 202 for new fork but got ${response.status}")
      val responseBody = parse(response.body).extract[Map[String, Any]]
      assert(responseBody("fork") == true)
      assert(responseBody("id").asInstanceOf[BigInt].toLong != 0)
      assert(responseBody("id").asInstanceOf[BigInt].toLong != base.getId)

      val fork = server.waitForRepository(forkClient, "user3/fork_api_origin")

      assert(fork.getId != 0)
      assert(fork.getId != base.getId)
      assert(fork.getFullName == "user3/fork_api_origin")
      assert(fork.isFork)
    }
  }

  test("POST /repos/:owner/:repo/forks with non-existent organization returns 422") {
    Using.resource(new TestingGitBucketServer(19999)) { server =>
      server.createUser("user4", "user4pass", "user4@example.com", "root", "root")
      val github = server.client("root", "root")
      github.createRepository("fork_bad_org_test").autoInit(true).create()

      val response =
        server.forkRepositoryViaApi("root", "fork_bad_org_test", Some("does_not_exist"), "user4", "user4pass")
      assert(response.status == 422, s"Expected 422 for non-existent organization but got ${response.status}")
      assert(
        parse(response.body).extract[ApiError] == ApiError(
          "The specified organization does not exist.",
          Some("https://docs.github.com/en/rest/repos/forks#create-a-fork")
        )
      )
    }
  }

  test("POST /repos/:owner/:repo/forks for an already-forked repository returns 202") {
    Using.resource(new TestingGitBucketServer(19999)) { server =>
      val github = server.client("root", "root")
      server.createUser("user5b", "user5bpass", "user5b@example.com", "root", "root")
      github.createRepository("double_fork_test").autoInit(true).create()

      val response1 = server.forkRepositoryViaApi("root", "double_fork_test", None, "user5b", "user5bpass")
      assert(response1.status == 202, s"Expected 202 for new fork but got ${response1.status}")
      val responseBody1 = parse(response1.body).extract[Map[String, Any]]
      assert(responseBody1("fork") == true)

      val response2 = server.forkRepositoryViaApi("root", "double_fork_test", None, "user5b", "user5bpass")
      assert(response2.status == 202, s"Expected 202 for existing fork but got ${response2.status}")
      val responseBody2 = parse(response2.body).extract[Map[String, Any]]
      assert(responseBody2("fork") == true)
      assert(responseBody1("id") == responseBody2("id"))

      server.waitForRepository(server.client("user5b", "user5bpass"), "user5b/double_fork_test")
    }
  }

  test("POST /repos/:owner/:repo/forks when target user has an unrelated repo with the same name returns 422") {
    Using.resource(new TestingGitBucketServer(19999)) { server =>
      val github = server.client("root", "root")
      server.createUser("user11", "user11pass", "user11@example.com", "root", "root")
      github.createRepository("name-collision-source").autoInit(true).create()
      server.client("user11", "user11pass").createRepository("name-collision-source").autoInit(true).create()

      val response = server.forkRepositoryViaApi("root", "name-collision-source", None, "user11", "user11pass")
      assert(
        response.status == 422,
        s"Expected 422 when target already has an unrelated repo with the same name but got ${response.status}"
      )
      assert(
        parse(response.body).extract[ApiError] == ApiError(
          "A repository with the same name already exists.",
          Some("https://docs.github.com/en/rest/repos/forks#create-a-fork")
        )
      )
    }
  }

  test("POST /repos/:owner/:repo/forks when the user tries to fork their own fork returns 422") {
    Using.resource(new TestingGitBucketServer(19999)) { server =>
      val github = server.client("root", "root")
      server.createUser("user12", "user12pass", "user12@example.com", "root", "root")
      github.createRepository("self-fork-test").autoInit(true).create()
      server.forkRepository("root", "self-fork-test", "user12", "user12pass")
      server.waitForRepository(server.client("user12", "user12pass"), "user12/self-fork-test")

      val response = server.forkRepositoryViaApi("user12", "self-fork-test", None, "user12", "user12pass")
      assert(response.status == 422, s"Expected 422 when user forks their own fork but got ${response.status}")
      assert(
        parse(response.body).extract[ApiError] == ApiError(
          "A user cannot fork their own repository.",
          Some("https://docs.github.com/en/rest/repos/forks#create-a-fork")
        )
      )
    }
  }

  test("POST /repos/:owner/:repo/forks with fork disabled returns 403") {
    Using.resource(new TestingGitBucketServer(19999)) { server =>
      val github = server.client("root", "root")
      github.createRepository("no-fork-test").autoInit(true).create()
      server.disableFork("root", "no-fork-test", "root", "root")

      server.createUser("forkuser", "forkuserpass", "forkuser@example.com", "root", "root")
      val response = server.forkRepositoryViaApi("root", "no-fork-test", None, "forkuser", "forkuserpass")
      assert(response.status == 403, s"Expected 403 when forking is disabled but got ${response.status}")
    }
  }

  test("POST /repos/:owner/:repo/forks into an organization the user is not a member of returns 403") {
    Using.resource(new TestingGitBucketServer(19999)) { server =>
      val github = server.client("root", "root")
      github.createRepository("fork-org-perm-test").autoInit(true).create()
      server.createOrganization("fork-target-org", "root", "root")
      server.createUser("nonmember", "nonmemberpass", "nonmember@example.com", "root", "root")

      val response =
        server.forkRepositoryViaApi("root", "fork-org-perm-test", Some("fork-target-org"), "nonmember", "nonmemberpass")
      assert(response.status == 403, s"Expected 403 for non-member forking into org but got ${response.status}")
    }
  }

  test("organization repository ID is non-zero") {
    Using.resource(new TestingGitBucketServer(19999)) { server =>
      val github = server.client("root", "root")
      server.createOrganization("testorg", "root", "root")
      val repo = github.getOrganization("testorg").createRepository("org_repo").autoInit(true).create()
      assert(repo.getId != 0)
    }
  }

  test("repository IDs are non-zero and distinct") {
    Using.resource(new TestingGitBucketServer(19999)) { server =>
      val github = server.client("root", "root")
      val repo1 = github.createRepository("id_test_1").autoInit(true).create()
      val repo2 = github.createRepository("id_test_2").autoInit(true).create()

      assert(repo1.getId != 0)
      assert(repo2.getId != 0)
      assert(repo1.getId != repo2.getId)
    }
  }

  test("Git refs APIs") {
    Using.resource(new TestingGitBucketServer(19999)) { server =>
      val github = server.client("root", "root")

      val repo = github.createRepository("git_refs_test").autoInit(true).create()
      val sha1 = repo.getBranch("main").getSHA1

      val refs1 = repo.listRefs().toList
      assert(refs1.size() == 1)
      assert(refs1.get(0).getRef == "refs/heads/main")
      assert(refs1.get(0).getObject.getSha == sha1)

      val ref = repo.createRef("refs/heads/testref", sha1)
      assert(ref.getRef == "refs/heads/testref")
      assert(ref.getObject.getSha == sha1)

      val refs2 = repo.listRefs().toList
      assert(refs2.size() == 2)
      assert(refs2.get(0).getRef == "refs/heads/main")
      assert(refs2.get(0).getObject.getSha == sha1)
      assert(refs2.get(1).getRef == "refs/heads/testref")
      assert(refs2.get(1).getObject.getSha == sha1)
    }
  }

  test("renaming an origin repository cascades to its forks' origin references") {
    Using.resource(new TestingGitBucketServer(19999)) { server =>
      val github = server.client("root", "root")
      server.createUser("user5", "user5pass", "user5@example.com", "root", "root")
      server.createUser("user7", "user7pass", "user7@example.com", "root", "root")

      github.createRepository("cascade-origin").autoInit(true).create()

      server.forkRepository("root", "cascade-origin", "user5", "user5pass")
      server.waitForRepository(server.client("user5", "user5pass"), "user5/cascade-origin")

      // Also fork user5's fork to create a two-level chain: root → user5 → user7
      server.forkRepository("user5", "cascade-origin", "user7", "user7pass")
      server.waitForRepository(server.client("user7", "user7pass"), "user7/cascade-origin")

      server.renameRepository("root", "cascade-origin", "cascade-renamed", "root", "root")

      val renamed = github.getRepository("root/cascade-renamed")
      assert(renamed.getFullName == "root/cascade-renamed")

      // Both direct and indirect forks still record cascade-renamed as their origin
      assert(renamed.getForksCount() == 2)

      // Renaming the intermediate fork must not break the sub-fork
      server.renameRepository("user5", "cascade-origin", "cascade-fork-renamed", "user5", "user5pass")

      val subFork = server.client("user7", "user7pass").getRepository("user7/cascade-origin")
      assert(subFork.getFullName == "user7/cascade-origin")
    }
  }

  test("deleting an origin repository does not delete its forks") {
    Using.resource(new TestingGitBucketServer(19999)) { server =>
      val github = server.client("root", "root")
      server.createUser("user6", "user6pass", "user6@example.com", "root", "root")
      server.createUser("user7b", "user7bpass", "user7b@example.com", "root", "root")

      github.createRepository("delete-origin").autoInit(true).create()

      server.forkRepository("root", "delete-origin", "user6", "user6pass")
      server.waitForRepository(server.client("user6", "user6pass"), "user6/delete-origin")

      // Also fork user6's fork to create a two-level chain: root → user6 → user7b
      server.forkRepository("user6", "delete-origin", "user7b", "user7bpass")
      server.waitForRepository(server.client("user7b", "user7bpass"), "user7b/delete-origin")

      // Deleting the root must not delete the direct fork
      server.deleteRepository("root", "delete-origin", "root", "root")

      val fork = server.client("user6", "user6pass").getRepository("user6/delete-origin")
      assert(fork.getFullName == "user6/delete-origin")
      assert(fork.getId != 0)

      // Deleting the intermediate fork must not delete the sub-fork
      server.deleteRepository("user6", "delete-origin", "user6", "user6pass")

      val subFork = server.client("user7b", "user7bpass").getRepository("user7b/delete-origin")
      assert(subFork.getFullName == "user7b/delete-origin")
      assert(subFork.getId != 0)
    }
  }

  test("fork repository has a different ID than its origin") {
    Using.resource(new TestingGitBucketServer(19999)) { server =>
      val github = server.client("root", "root")
      val base = github.createRepository("fork_origin").autoInit(true).create()

      server.createUser("user2", "user2pass", "user2@example.com", "root", "root")
      server.forkRepository("root", "fork_origin", "user2", "user2pass")

      val fork = server.waitForRepository(server.client("user2", "user2pass"), "user2/fork_origin")

      assert(base.getId != 0)
      assert(fork.getId != 0)
      assert(fork.getId != base.getId)
    }
  }
}
