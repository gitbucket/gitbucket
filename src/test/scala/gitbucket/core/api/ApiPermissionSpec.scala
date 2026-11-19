package gitbucket.core.api

import gitbucket.core.TestingGitBucketServer
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite

/**
 * Write collaborators may push, but not change the repository settings, as in the web UI.
 *
 * Need to run `sbt package` before running this test.
 */
class ApiPermissionSpec extends AnyFunSuite with BeforeAndAfterAll {

  private lazy val server = new TestingGitBucketServer(19992)

  override def beforeAll(): Unit = {
    server.client("root", "root").createRepository("settings_repo").autoInit(true).create()
    server.createUser("developer", "developer", "developer@example.invalid", "root", "root")
    val added = server.putApi(
      "/api/v3/repos/root/settings_repo/collaborators/developer",
      """{"permission":"push"}""",
      "root",
      "root"
    )
    assert(added.status == 200, s"Expected 200 but got ${added.status}")
  }

  override def afterAll(): Unit = server.close()

  private def assertStatus(response: server.ApiResponse, expected: Int, label: String): Unit =
    assert(response.status == expected, s"$label: expected $expected but got ${response.status}")

  test("webhook APIs require the owner role") {
    val hook = """{"config":{"url":"http://example.invalid/hook","content_type":"json"},"events":["push"]}"""
    val path = "/api/v3/repos/root/settings_repo/hooks"
    assertStatus(server.postApi(path, hook, "root", "root"), 200, "owner creates")
    assertStatus(server.getApi(path, "root", "root"), 200, "owner lists")

    assertStatus(server.getApi(path, "developer", "developer"), 401, "developer lists")
    assertStatus(server.getApi(s"$path/1", "developer", "developer"), 401, "developer gets")
    assertStatus(server.postApi(path, hook, "developer", "developer"), 401, "developer creates")
    assertStatus(server.patchApi(s"$path/1", hook, "developer", "developer"), 401, "developer updates")
    assertStatus(server.deleteApi(s"$path/1", "developer", "developer"), 401, "developer deletes")
    assertStatus(server.getApi(s"$path/1", "root", "root"), 200, "hook still there")
  }

  test("deleting a branch protection requires the owner role") {
    val protect = server.patchApi(
      "/api/v3/repos/root/settings_repo/branches/main",
      """{"protection":{"enabled":true}}""",
      "root",
      "root"
    )
    assertStatus(protect, 200, "owner protects")
    val path = "/api/v3/repos/root/settings_repo/branches/main/protection"
    assertStatus(server.deleteApi(path, "developer", "developer"), 401, "developer deletes")
    assertStatus(server.getApi(path, "root", "root"), 200, "protection still there")
  }
}
