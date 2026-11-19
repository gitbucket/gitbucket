package gitbucket.core.api

import gitbucket.core.TestingGitBucketServer
import gitbucket.core.util.FileUtil
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.{RefSpec, RemoteRefUpdate, UsernamePasswordCredentialsProvider}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite

import scala.jdk.CollectionConverters.*
import scala.util.Using

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
    assertStatus(web, 401, s"web form (${web.body})")
  }

  test("the compare page doesn't show an origin repository the user can't read") {
    server.createUser("carol", "carol", "carol@example.invalid", "root", "root")
    server.client("carol", "carol").createRepository("compare_repo").autoInit(true).create()
    server.createUser("dave", "dave", "dave@example.invalid", "root", "root")
    val fork = server.forkRepositoryViaApi("carol", "compare_repo", None, "dave", "dave")
    assert(fork.status == 202, s"fork: ${fork.status} ${fork.body}")
    server.waitForRepository(server.client("dave", "dave"), "dave/compare_repo")

    val compare = "/dave/compare_repo/compare/carol:main...dave:main"
    assertStatus(server.getPage(compare, "dave", "dave"), 200, "origin readable")
    val privateOrigin = server.postForm(
      "/carol/compare_repo/settings/options",
      Seq(
        "isPrivate" -> "true",
        "issuesOption" -> "ALL",
        "wikiOption" -> "ALL",
        "allowFork" -> "true",
        "mergeOptions" -> "merge-commit",
        "defaultMergeOption" -> "merge-commit",
        "safeMode" -> "true"
      ),
      "carol",
      "carol"
    )
    assertStatus(privateOrigin, 302, s"make the origin private (${privateOrigin.body})")
    assertStatus(server.getPage("/dave/compare_repo", "dave", "dave"), 200, "own fork")
    assertStatus(server.getPage(compare, "dave", "dave"), 404, "origin private")
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

  test("the refs API and git push keep branch protection and refs/pull") {
    val repo = server.client("root", "root").createRepository("refs_repo").autoInit(true).create()
    assertStatus(
      server
        .putApi("/api/v3/repos/root/refs_repo/collaborators/developer", """{"permission":"push"}""", "root", "root"),
      200,
      "developer added"
    )
    val mainId = repo.getBranch("main").getSHA1
    val asDeveloper = server.client("developer", "developer").getRepository("root/refs_repo")
    asDeveloper.createRef("refs/heads/other", mainId)
    asDeveloper.createContent().content("other").path("other.txt").message("Other").branch("other").commit()
    val otherId = asDeveloper.getBranch("other").getSHA1
    asDeveloper.createRef("refs/heads/unrelated", mainId)
    assertStatus(
      server.patchApi(
        "/api/v3/repos/root/refs_repo/branches/main",
        """{"protection":{"enabled":true,"restrictions":{"users":["root"]}}}""",
        "root",
        "root"
      ),
      200,
      "owner protects"
    )

    val workTree = java.nio.file.Files.createTempDirectory("refs_repo").toFile
    val pushed =
      try {
        val credentials = new UsernamePasswordCredentialsProvider("developer", "developer")
        Using.resource(
          Git
            .cloneRepository()
            .setURI(s"http://localhost:${server.port}/git/root/refs_repo.git")
            .setCredentialsProvider(credentials)
            .setDirectory(workTree)
            .call()
        ) { git =>
          git
            .push()
            .setCredentialsProvider(credentials)
            .setRefSpecs(new RefSpec("HEAD:refs/pull/99/merge"))
            .call()
            .asScala
            .flatMap(_.getRemoteUpdates.asScala)
            .exists(_.getStatus == RemoteRefUpdate.Status.OK)
        }
      } finally FileUtil.deleteIfExists(workTree)

    val path = "/api/v3/repos/root/refs_repo/git/refs"
    val accepted = (Seq(
      "update protected" -> server
        .patchApi(s"$path/heads/main", s"""{"sha":"$otherId","force":false}""", "developer", "developer")
        .status,
      "delete protected" -> server.deleteApi(s"$path/heads/main", "developer", "developer").status,
      "create refs/pull" -> server
        .postApi(path, s"""{"ref":"refs/pull/99/head","sha":"$otherId"}""", "developer", "developer")
        .status
    ) ++ Seq("git push to refs/pull" -> (if (pushed) 200 else 400))).filter(_._2 < 400)
    assert(accepted.isEmpty, accepted.map { case (label, status) => s"$label: $status" }.mkString(", "))
    assert(repo.getBranch("main").getSHA1 == mainId, "main was changed")
    assertStatus(server.deleteApi(s"$path/heads/unrelated", "developer", "developer"), 204, "delete unprotected")
  }

  test("merging respects the push restrictions of the base branch") {
    val repo = server.client("root", "root").createRepository("merge_repo").autoInit(true).create()
    assertStatus(
      server
        .putApi("/api/v3/repos/root/merge_repo/collaborators/developer", """{"permission":"push"}""", "root", "root"),
      200,
      "developer added"
    )
    val mainId = repo.getBranch("main").getSHA1
    val asDeveloper = server.client("developer", "developer").getRepository("root/merge_repo")
    asDeveloper.createRef("refs/heads/feature", mainId)
    asDeveloper.createContent().content("feature").path("feature.txt").message("Feature").branch("feature").commit()
    assertStatus(
      server.patchApi(
        "/api/v3/repos/root/merge_repo/branches/main",
        """{"protection":{"enabled":true,"restrictions":{"users":["root"]}}}""",
        "root",
        "root"
      ),
      200,
      "owner protects"
    )
    val pull = server.postApi(
      "/api/v3/repos/root/merge_repo/pulls",
      """{"title":"Feature","head":"feature","base":"main"}""",
      "developer",
      "developer"
    )
    assertStatus(pull, 200, "developer opens a pull request")

    server.putApi(
      "/api/v3/repos/root/merge_repo/pulls/1/merge",
      """{"merge_method":"merge"}""",
      "developer",
      "developer"
    )
    server.postForm(
      "/root/merge_repo/pull/1/merge",
      Seq("message" -> "Merge", "strategy" -> "merge-commit", "isDraft" -> "false"),
      "developer",
      "developer"
    )
    assert(repo.getBranch("main").getSHA1 == mainId, "main was changed")
  }
}
