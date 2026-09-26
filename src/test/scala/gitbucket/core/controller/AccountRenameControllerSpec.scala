package gitbucket.core.controller

import gitbucket.core.TestingGitBucketServer
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.HttpPost
import org.apache.http.impl.client.{BasicCookieStore, HttpClients}
import org.apache.http.message.BasicNameValuePair
import org.apache.http.util.EntityUtils
import org.scalatest.funsuite.AnyFunSuite

import java.util.{Arrays => JArrays}
import scala.util.Using

/**
 * Need to run `sbt package` before running this test.
 */
class AccountRenameControllerSpec extends AnyFunSuite {

  /** Sign in as `login` and POST to `path` with the given form fields, without following redirects. */
  private def postWeb(
    server: TestingGitBucketServer,
    path: String,
    login: String,
    password: String,
    fields: Map[String, String]
  ): Int = {
    Using.resource(HttpClients.custom().setDefaultCookieStore(new BasicCookieStore()).build()) { httpClient =>
      val signin = new HttpPost(s"http://localhost:${server.port}/signin")
      signin.setEntity(
        new UrlEncodedFormEntity(
          JArrays.asList(
            new BasicNameValuePair("userName", login),
            new BasicNameValuePair("password", password)
          )
        )
      )
      val signinResponse = httpClient.execute(signin)
      EntityUtils.consume(signinResponse.getEntity)
      assert(signinResponse.getStatusLine.getStatusCode < 400, "signin failed")

      val post = new HttpPost(s"http://localhost:${server.port}$path")
      post.setConfig(RequestConfig.custom().setRedirectsEnabled(false).build())
      post.setEntity(
        new UrlEncodedFormEntity(
          JArrays.asList(fields.map { case (k, v) => new BasicNameValuePair(k, v) }.toSeq*)
        )
      )
      val response = httpClient.execute(post)
      EntityUtils.consume(response.getEntity)
      response.getStatusLine.getStatusCode
    }
  }

  test("POST /:userName/_rename renames the account and signin succeeds with new username") {
    Using.resource(new TestingGitBucketServer(19993)) { server =>
      server.createUser("alice", "alicepw", "alice@example.com", "root", "root")

      server.renameAccount("alice", "bob", "alice", "alicepw")

      val renamed = server.getAnonymousApi("/api/v3/users/bob")
      assert(renamed.status == 200, s"expected renamed account to be reachable, got ${renamed.status}")
      assert(renamed.body.contains("\"login\":\"bob\""))

      val old = server.getAnonymousApi("/api/v3/users/alice")
      assert(old.status == 404, s"expected the old user name to no longer exist, got ${old.status}")

      // The renamed user can still sign in and use their account under the new name
      val editStatus = postWeb(server, "/bob/_rename", "bob", "alicepw", Map("newUserName" -> "bob"))
      assert(editStatus == 302)
    }
  }

  test("POST /:userName/_rename preserves ownership of the user's repositories") {
    Using.resource(new TestingGitBucketServer(19992)) { server =>
      server.createUser("carol", "carolpw", "carol@example.com", "root", "root")
      server.client("carol", "carolpw").createRepository("myrepo").autoInit(true).create()

      server.renameAccount("carol", "carol2", "carol", "carolpw")

      val moved = server.getAnonymousApi("/api/v3/repos/carol2/myrepo")
      assert(moved.status == 200, s"expected the repository to follow the rename, got ${moved.status}")

      val stale = server.getAnonymousApi("/api/v3/repos/carol/myrepo")
      assert(stale.status == 404, s"expected the old owner path to be gone, got ${stale.status}")
    }
  }

  test("POST /:userName/_rename is rejected for a user other than oneself") {
    Using.resource(new TestingGitBucketServer(19991)) { server =>
      server.createUser("dave", "davepw", "dave@example.com", "root", "root")
      server.createUser("erin", "erinpw", "erin@example.com", "root", "root")

      val status = postWeb(server, "/erin/_rename", "dave", "davepw", Map("newUserName" -> "erin2"))
      assert(status == 401, "a non-owner must not be able to rename another user's account")

      val unchanged = server.getAnonymousApi("/api/v3/users/erin")
      assert(unchanged.status == 200, "erin's account must be unaffected by the rejected rename")
    }
  }

  test("POST /:userName/_rename is rejected when the new user name is already taken") {
    Using.resource(new TestingGitBucketServer(19990)) { server =>
      server.createUser("frank", "frankpw", "frank@example.com", "root", "root")
      server.createUser("grace", "gracepw", "grace@example.com", "root", "root")

      val status = postWeb(server, "/frank/_rename", "frank", "frankpw", Map("newUserName" -> "grace"))
      assert(status == 400, "renaming to an existing user name must be rejected")

      val frankStillExists = server.getAnonymousApi("/api/v3/users/frank")
      assert(frankStillExists.status == 200, "frank's account must not have been renamed away")
    }
  }

  test("POST /:userName/_rename is rejected when the new user name is reserved") {
    Using.resource(new TestingGitBucketServer(19989)) { server =>
      server.createUser("henry", "henrypw", "henry@example.com", "root", "root")

      // "admin" is one of the reserved names in ControllerBase.allReservedNames, which would
      // collide with a fixed application route (e.g. /admin/...) if a user could claim it.
      val status = postWeb(server, "/henry/_rename", "henry", "henrypw", Map("newUserName" -> "admin"))
      assert(status == 400, "renaming to a reserved user name must be rejected")

      val henryStillExists = server.getAnonymousApi("/api/v3/users/henry")
      assert(henryStillExists.status == 200, "henry's account must not have been renamed away")

      val noReservedAccount = server.getAnonymousApi("/api/v3/users/admin")
      assert(noReservedAccount.status == 404, "no account should have been created under the reserved name")
    }
  }
}
