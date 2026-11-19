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

  test("pull requests can't take a branch from a repository the user can't read") {
    server.createUser("alice", "alice", "alice@example.invalid", "root", "root")
    val secret = server.client("alice", "alice").createRepository("same_name").private_(true).autoInit(true).create()
    secret.createRef("refs/heads/secret", secret.getBranch("main").getSHA1)
    secret.createContent().content("secret").path("secret.txt").message("Add secret").branch("secret").commit()
    val secretId = secret.getBranch("secret").getSHA1
    server.createUser("bob", "bob", "bob@example.invalid", "root", "root")
    val own = server.client("bob", "bob").createRepository("same_name").autoInit(true).create()

    val api = server.postApi(
      "/api/v3/repos/bob/same_name/pulls",
      """{"title":"Copy","head":"alice:secret","base":"main"}""",
      "bob",
      "bob"
    )
    assertStatus(api, 404, "API")
    val web = server.postForm(
      "/bob/same_name/pulls/new",
      Seq(
        "title" -> "Copy",
        "targetUserName" -> "bob",
        "targetBranch" -> "main",
        "requestUserName" -> "alice",
        "requestRepositoryName" -> "same_name",
        "requestBranch" -> "secret",
        "commitIdFrom" -> own.getBranch("main").getSHA1,
        "commitIdTo" -> secretId,
        "isDraft" -> "false"
      ),
      "bob",
      "bob"
    )
    assertStatus(web, 404, s"web form (${web.body})")
  }

  test("Update branch can't push to a protected branch the user may not push to") {
    val repo = server.client("root", "root").createRepository("update_repo").autoInit(true).create()
    assertStatus(
      server.putApi(
        "/api/v3/repos/root/update_repo/collaborators/developer",
        """{"permission":"push"}""",
        "root",
        "root"
      ),
      200,
      "developer added"
    )
    val protect = server.patchApi(
      "/api/v3/repos/root/update_repo/branches/main",
      """{"protection":{"enabled":true,"restrictions":{"users":["root"]}}}""",
      "root",
      "root"
    )
    assertStatus(protect, 200, "owner protects")
    val mainId = repo.getBranch("main").getSHA1
    val asDeveloper = server.client("developer", "developer").getRepository("root/update_repo")
    asDeveloper.createRef("refs/heads/feature", mainId)
    asDeveloper.createContent().content("change").path("change.txt").message("Change").branch("feature").commit()
    val pull = server.postApi(
      "/api/v3/repos/root/update_repo/pulls",
      """{"title":"Into feature","head":"main","base":"feature"}""",
      "developer",
      "developer"
    )
    assertStatus(pull, 200, "developer opens a pull request from main into feature")

    server.postForm("/root/update_repo/pull/1/update_branch", Nil, "developer", "developer")
    assert(repo.getBranch("main").getSHA1 == mainId, "main was changed")
  }
}
