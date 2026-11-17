package gitbucket.core.controller

import gitbucket.core.TestingGitBucketServer
import org.scalatest.funsuite.AnyFunSuite

import scala.util.Using

/**
 * Need to run `sbt package` before running this test.
 */
class AccountNewRepositoryControllerSpec extends AnyFunSuite {

  test("POST /new does not clone from a private address by default") {
    Using.resource(new TestingGitBucketServer(19998)) { server =>
      server.client("root", "root").createRepository("clone_source").autoInit(true).create()

      val sourceUrl = s"http://127.0.0.1:${server.port}/git/root/clone_source.git"
      val response = server.createRepositoryByClone("root", "clone_blocked", sourceUrl, "root", "root")
      assert(response.status == 400, s"Expected 400 but got ${response.status}")
      assert(response.body.contains("blocked by system settings"))
      assert(server.getApi("/api/v3/repos/root/clone_blocked", "root", "root").status == 404)
    }
  }

  test("POST /new clones from a private address in the whitelist") {
    Using.resource(new TestingGitBucketServer(19998)) { server =>
      val github = server.client("root", "root")
      github.createRepository("clone_source").autoInit(true).create()
      server.updateSettings("webhook.whitelist" -> "127.0.0.0/8")

      val sourceUrl = s"http://127.0.0.1:${server.port}/git/root/clone_source.git"
      val response = server.createRepositoryByClone("root", "clone_allowed", sourceUrl, "root", "root")
      assert(response.status == 302, s"Expected 302 but got ${response.status}")
      assert(server.waitForRepository(github, "root/clone_allowed").getName == "clone_allowed")
    }
  }
}
