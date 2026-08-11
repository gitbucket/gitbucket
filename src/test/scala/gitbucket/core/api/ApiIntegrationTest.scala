package gitbucket.core.api

import gitbucket.core.TestingGitBucketServer
import gitbucket.core.api.ApiError
import org.apache.commons.io.IOUtils
import org.eclipse.jgit.api.Git
import org.json4s.{DefaultFormats, JArray, JObject, JNull, JString, jvalue2extractable}
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

  test("commit APIs return null account fields for an unmapped Git author") {
    Using.resource(new TestingGitBucketServer(19999)) { server =>
      val github = server.client("root", "root")
      github.createRepository("unmapped_commit_author").autoInit(true).create()

      val bareRepository = new File(server.getDirectory(), "repositories/root/unmapped_commit_author")
      val workTree = new File(server.getDirectory(), "unmapped_commit_author-worktree")
      val commitId =
        Using.resource(Git.cloneRepository().setURI(bareRepository.toURI.toString).setDirectory(workTree).call()) {
          git =>
            val commit = git
              .commit()
              .setAllowEmpty(true)
              .setMessage("Commit from an unmapped Git identity")
              .setAuthor("Unmapped Author", "unmapped-author@example.invalid")
              .setCommitter("Unmapped Committer", "unmapped-committer@example.invalid")
              .call()
            git.push().call()
            commit.getName
        }

      val listResponse = server.getApi("/api/v3/repos/root/unmapped_commit_author/commits", "root", "root")
      assert(listResponse.status == 200)
      val listCommit = parse(listResponse.body).asInstanceOf[JArray].arr.head
      def field(json: JObject, name: String) = json.obj.collectFirst { case (`name`, value) => value }.get
      assert(field(listCommit.asInstanceOf[JObject], "author") == JNull)
      assert(field(listCommit.asInstanceOf[JObject], "committer") == JNull)

      val singleResponse =
        server.getApi(s"/api/v3/repos/root/unmapped_commit_author/commits/${commitId}", "root", "root")
      assert(singleResponse.status == 200)
      val singleCommit = parse(singleResponse.body).asInstanceOf[JObject]
      assert(field(singleCommit, "author") == JNull)
      assert(field(singleCommit, "committer") == JNull)

      val singleCommitInfo = field(singleCommit, "commit").asInstanceOf[JObject]
      val singleCommitAuthor = field(singleCommitInfo, "author").asInstanceOf[JObject]
      val singleCommitCommitter = field(singleCommitInfo, "committer").asInstanceOf[JObject]
      assert(
        field(singleCommitAuthor, "name") == JString("Unmapped Author") &&
          field(singleCommitAuthor, "email") == JString("unmapped-author@example.invalid")
      )
      assert(
        field(singleCommitCommitter, "name") == JString("Unmapped Committer") &&
          field(singleCommitCommitter, "email") == JString("unmapped-committer@example.invalid")
      )
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

  test("GET /repos/:owner/:repo/forks lists repository forks") {
    Using.resource(new TestingGitBucketServer(19999)) { server =>
      val github = server.client("root", "root")
      github.createRepository("list_forks_test").autoInit(true).create()
      server.createOrganization("forkorg", "root", "root")
      server.createUser("user13", "user13pass", "user13@example.com", "root", "root")

      val orgForkResponse = server.forkRepositoryViaApi("root", "list_forks_test", Some("forkorg"), "root", "root")
      assert(orgForkResponse.status == 202, s"Expected 202 for org fork but got ${orgForkResponse.status}")

      // REGISTERED_DATE has only second-level precision on some supported DBs (e.g. MySQL's
      // DATETIME), so the sleep must clear a full second to guarantee distinct timestamps.
      Thread.sleep(1100)

      val userForkResponse = server.forkRepositoryViaApi("root", "list_forks_test", None, "user13", "user13pass")
      assert(userForkResponse.status == 202, s"Expected 202 for user fork but got ${userForkResponse.status}")

      val newestResponse = server.getApi("/api/v3/repos/root/list_forks_test/forks?sort=newest", "root", "root")
      assert(newestResponse.status == 200, s"Expected 200 for newest fork listing but got ${newestResponse.status}")

      val newestForks = parse(newestResponse.body).extract[List[Map[String, Any]]]
      assert(newestForks.size == 2)
      assert(newestForks.forall(_("fork") == true))
      assert(
        newestForks.map(_("full_name").toString) == List("user13/list_forks_test", "forkorg/list_forks_test")
      )

      val oldestResponse = server.getApi("/api/v3/repos/root/list_forks_test/forks?sort=oldest", "root", "root")
      assert(oldestResponse.status == 200, s"Expected 200 for oldest fork listing but got ${oldestResponse.status}")

      val oldestForks = parse(oldestResponse.body).extract[List[Map[String, Any]]]
      assert(oldestForks.size == 2)
      assert(oldestForks.forall(_("fork") == true))
      assert(
        oldestForks.map(_("full_name").toString) == List("forkorg/list_forks_test", "user13/list_forks_test")
      )
    }
  }

  test("GET /repos/:owner/:repo/forks rejects unsupported GitHub sort values") {
    Using.resource(new TestingGitBucketServer(19999)) { server =>
      val github = server.client("root", "root")
      github.createRepository("list_forks_unsupported_sort_test").autoInit(true).create()

      List("stargazers", "watchers").foreach { sort =>
        val response =
          server.getApi(s"/api/v3/repos/root/list_forks_unsupported_sort_test/forks?sort=$sort", "root", "root")
        assert(response.status == 501, s"Expected 501 for unsupported sort '$sort' but got ${response.status}")
        assert(
          parse(response.body).extract[ApiError] == ApiError(
            s"Sort value '$sort' is not supported by GitBucket.",
            Some("https://docs.github.com/en/rest/repos/forks#list-forks")
          )
        )
      }
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

  test("user and organization IDs are non-zero and distinct") {
    Using.resource(new TestingGitBucketServer(19999)) { server =>
      val github = server.client("root", "root")
      server.createOrganization("testorg", "root", "root")

      val user = github.getUser("root")
      val org = github.getOrganization("testorg")

      assert(user.getId != 0)
      assert(org.getId != 0)
      assert(user.getId != org.getId)
    }
  }

  test("GET /user/:id returns the user") {
    Using.resource(new TestingGitBucketServer(19999)) { server =>
      val github = server.client("root", "root")
      val id = github.getUser("root").getId

      val response = server.getApi(s"/api/v3/user/$id", "root", "root")
      assert(response.status == 200, s"Expected 200 but got ${response.status}")

      val body = parse(response.body).extract[Map[String, Any]]
      assert(body("id").asInstanceOf[BigInt].toLong == id)
      assert(body("login") == "root")
      assert(body("type") == "User")
    }
  }

  test("GET /user/:id returns organization info for an organization ID") {
    Using.resource(new TestingGitBucketServer(19999)) { server =>
      val github = server.client("root", "root")
      server.createOrganization("id_lookup_org", "root", "root")
      val id = github.getOrganization("id_lookup_org").getId

      val response = server.getApi(s"/api/v3/user/$id", "root", "root")
      assert(response.status == 200, s"Expected 200 but got ${response.status}")

      val body = parse(response.body).extract[Map[String, Any]]
      assert(body("id").asInstanceOf[BigInt].toLong == id)
      assert(body("login") == "id_lookup_org")
      assert(body("type") == "Organization")
    }
  }

  test("GET /user/:id with an unknown ID returns 404") {
    Using.resource(new TestingGitBucketServer(19999)) { server =>
      val response = server.getApi("/api/v3/user/999999999", "root", "root")
      assert(response.status == 404, s"Expected 404 for an unknown ID but got ${response.status}")
    }
  }

  test("GET /user/:id with a non-numeric ID returns 404") {
    Using.resource(new TestingGitBucketServer(19999)) { server =>
      val response = server.getApi("/api/v3/user/not-a-number", "root", "root")
      assert(response.status == 404, s"Expected 404 for a non-numeric ID but got ${response.status}")
    }
  }

  test("GET /user/:id for a suspended user returns 404") {
    Using.resource(new TestingGitBucketServer(19999)) { server =>
      server.createUser("suspend_id_test", "suspend_id_test_pw", "suspend_id_test@example.com", "root", "root")
      val id = server.client("root", "root").getUser("suspend_id_test").getId

      server.suspendUser("suspend_id_test", "root", "root")

      val response = server.getApi(s"/api/v3/user/$id", "root", "root")
      assert(response.status == 404, s"Expected 404 for a suspended user but got ${response.status}")
    }
  }

  test("GET /user/:id works without authentication and returns the same fields as an authenticated request") {
    Using.resource(new TestingGitBucketServer(19999)) { server =>
      val github = server.client("root", "root")
      val id = github.getUser("root").getId

      val anonymous = server.getAnonymousApi(s"/api/v3/user/$id")
      assert(anonymous.status == 200, s"Expected 200 for anonymous access but got ${anonymous.status}")

      val authenticated = server.getApi(s"/api/v3/user/$id", "root", "root")
      assert(authenticated.status == 200, s"Expected 200 for authenticated access but got ${authenticated.status}")

      assert(anonymous.body == authenticated.body)
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

  test("POST /admin/organizations creates an organization with the given profile") {
    Using.resource(new TestingGitBucketServer(19999)) { server =>
      val response = server.postApi(
        "/api/v3/admin/organizations",
        """{"login":"json-response-org","admin":"root","profile_name":"JSON Response Org"}""",
        "root",
        "root"
      )
      assert(response.status == 200, s"Expected 200 but got ${response.status}")

      val organization = parse(response.body).extract[Map[String, Any]]
      assert(organization("login") == "json-response-org")
      assert(organization("description") == "JSON Response Org")
    }
  }

  test("POST /admin/organizations with an unparsable request body returns 400") {
    Using.resource(new TestingGitBucketServer(19999)) { server =>
      val response = server.postApi("/api/v3/admin/organizations", "{", "root", "root")
      assert(response.status == 400, s"Expected 400 but got ${response.status}")
    }
  }

  test("POST /admin/users creates a user with the given login and email") {
    Using.resource(new TestingGitBucketServer(19999)) { server =>
      val response = server.postApi(
        "/api/v3/admin/users",
        """{"login":"json-response-user","password":"json-response-pass","email":"json-response-user@example.invalid"}""",
        "root",
        "root"
      )
      assert(response.status == 200, s"Expected 200 but got ${response.status}")

      val user = parse(response.body).extract[Map[String, Any]]
      assert(user("login") == "json-response-user")
      assert(user("email") == "json-response-user@example.invalid")
      assert(user("type") == "User")
      assert(user("site_admin") == false)
    }
  }

  test("POST /admin/users with an unparsable request body returns 400") {
    Using.resource(new TestingGitBucketServer(19999)) { server =>
      val response = server.postApi("/api/v3/admin/users", "{", "root", "root")
      assert(response.status == 400, s"Expected 400 but got ${response.status}")
    }
  }

  test("PATCH /user updates the authenticated user's profile") {
    Using.resource(new TestingGitBucketServer(19999)) { server =>
      server.createUser("patch-user-test", "patch-user-pass", "patch-user-test@example.invalid", "root", "root")

      val response = server.patchApi(
        "/api/v3/user",
        """{"email":"patch-user-test-updated@example.invalid"}""",
        "patch-user-test",
        "patch-user-pass"
      )
      assert(response.status == 200, s"Expected 200 but got ${response.status}")

      val user = parse(response.body).extract[Map[String, Any]]
      assert(user("login") == "patch-user-test")
      assert(user("email") == "patch-user-test-updated@example.invalid")
    }
  }

  test("PATCH /user with an unparsable request body returns 400") {
    Using.resource(new TestingGitBucketServer(19999)) { server =>
      server.createUser("patch-user-bad-body", "patch-user-bad-pass", "patch-user-bad@example.invalid", "root", "root")

      val response = server.patchApi("/api/v3/user", "{", "patch-user-bad-body", "patch-user-bad-pass")
      assert(response.status == 400, s"Expected 400 but got ${response.status}")
    }
  }

  test("POST /repos/:owner/:repo/pulls creates a pull request with the given title and body") {
    Using.resource(new TestingGitBucketServer(19999)) { server =>
      val github = server.client("root", "root")
      val repo = github.createRepository("pulls_response_test").autoInit(true).create()
      val sha1 = repo.getBranch("main").getSHA1
      repo.createRef("refs/heads/feature", sha1)
      repo
        .createContent()
        .content("feature content")
        .path("feature.txt")
        .message("Add feature file")
        .branch("feature")
        .commit()

      val response = server.postApi(
        "/api/v3/repos/root/pulls_response_test/pulls",
        """{"title":"Add feature","head":"feature","base":"main","body":"feature description"}""",
        "root",
        "root"
      )
      assert(response.status == 200, s"Expected 200 but got ${response.status}")

      val pullRequest = parse(response.body).extract[Map[String, Any]]
      assert(pullRequest("title") == "Add feature")
      assert(pullRequest("body") == "feature description")
      assert(pullRequest("state") == "open")
      assert(pullRequest("head").asInstanceOf[Map[String, Any]]("ref") == "feature")
      assert(pullRequest("base").asInstanceOf[Map[String, Any]]("ref") == "main")
    }
  }

  test("POST /repos/:owner/:repo/pulls with an unparsable request body returns 400") {
    Using.resource(new TestingGitBucketServer(19999)) { server =>
      val github = server.client("root", "root")
      github.createRepository("pulls_bad_body_test").autoInit(true).create()

      val response = server.postApi("/api/v3/repos/root/pulls_bad_body_test/pulls", "{", "root", "root")
      assert(response.status == 400, s"Expected 400 but got ${response.status}")
    }
  }

  test("POST /repos/:owner/:repo/releases creates a release with the given tag and body") {
    Using.resource(new TestingGitBucketServer(19999)) { server =>
      val github = server.client("root", "root")
      github.createRepository("releases_response_test").autoInit(true).create()

      val response = server.postApi(
        "/api/v3/repos/root/releases_response_test/releases",
        """{"tag_name":"v1.0.0","name":"Version 1.0.0","body":"initial release"}""",
        "root",
        "root"
      )
      assert(response.status == 200, s"Expected 200 but got ${response.status}")

      val release = parse(response.body).extract[Map[String, Any]]
      assert(release("tag_name") == "v1.0.0")
      assert(release("name") == "Version 1.0.0")
      assert(release("body") == "initial release")
    }
  }

  test("POST /repos/:owner/:repo/releases with an unparsable request body returns 400") {
    Using.resource(new TestingGitBucketServer(19999)) { server =>
      val github = server.client("root", "root")
      github.createRepository("releases_bad_body_test").autoInit(true).create()

      val response = server.postApi("/api/v3/repos/root/releases_bad_body_test/releases", "{", "root", "root")
      assert(response.status == 400, s"Expected 400 but got ${response.status}")
    }
  }

  test("PATCH /repos/:owner/:repo/releases/:tag updates the release name and body") {
    Using.resource(new TestingGitBucketServer(19999)) { server =>
      val github = server.client("root", "root")
      github.createRepository("release_patch_test").autoInit(true).create()

      val createResponse = server.postApi(
        "/api/v3/repos/root/release_patch_test/releases",
        """{"tag_name":"v1.0.0","name":"Version 1.0.0","body":"initial release"}""",
        "root",
        "root"
      )
      assert(createResponse.status == 200, s"Expected 200 but got ${createResponse.status}")

      val response = server.patchApi(
        "/api/v3/repos/root/release_patch_test/releases/v1.0.0",
        """{"tag_name":"v1.0.0","name":"Version 1.0.0 - updated","body":"updated release notes"}""",
        "root",
        "root"
      )
      assert(response.status == 200, s"Expected 200 but got ${response.status}")

      val release = parse(response.body).extract[Map[String, Any]]
      assert(release("tag_name") == "v1.0.0")
      assert(release("name") == "Version 1.0.0 - updated")
      assert(release("body") == "updated release notes")
    }
  }

  test("PATCH /repos/:owner/:repo/releases/:tag with an unparsable request body returns 400") {
    Using.resource(new TestingGitBucketServer(19999)) { server =>
      val github = server.client("root", "root")
      github.createRepository("release_patch_bad_body_test").autoInit(true).create()

      server.postApi(
        "/api/v3/repos/root/release_patch_bad_body_test/releases",
        """{"tag_name":"v1.0.0","name":"Version 1.0.0","body":"initial release"}""",
        "root",
        "root"
      )

      val response =
        server.patchApi("/api/v3/repos/root/release_patch_bad_body_test/releases/v1.0.0", "{", "root", "root")
      assert(response.status == 400, s"Expected 400 but got ${response.status}")
    }
  }
}
