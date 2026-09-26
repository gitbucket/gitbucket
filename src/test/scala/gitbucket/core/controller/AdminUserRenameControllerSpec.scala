package gitbucket.core.controller

import gitbucket.core.TestingGitBucketServer
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.{HttpGet, HttpPost}
import org.apache.http.impl.client.{BasicCookieStore, HttpClients}
import org.apache.http.message.BasicNameValuePair
import org.apache.http.util.EntityUtils
import org.scalatest.funsuite.AnyFunSuite

import java.util.{Arrays => JArrays}
import scala.util.Using

/**
 * Need to run `sbt package` before running this test.
 */
class AdminUserRenameControllerSpec extends AnyFunSuite {

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

  test("POST /admin/users/:userName/_rename lets an administrator rename another user's account") {
    Using.resource(new TestingGitBucketServer(19988)) { server =>
      server.createUser("ivy", "ivypw", "ivy@example.com", "root", "root")

      server.renameAccountAsAdmin("ivy", "ivy2", "root", "root")

      val renamed = server.getAnonymousApi("/api/v3/users/ivy2")
      assert(renamed.status == 200, s"expected renamed account to be reachable, got ${renamed.status}")
      assert(renamed.body.contains("\"login\":\"ivy2\""))

      val old = server.getAnonymousApi("/api/v3/users/ivy")
      assert(old.status == 404, s"expected the old user name to no longer exist, got ${old.status}")
    }
  }

  test("POST /admin/users/:userName/_rename keeps the admin's own session working after a self-rename") {
    Using.resource(new TestingGitBucketServer(19987)) { server =>
      Using.resource(HttpClients.custom().setDefaultCookieStore(new BasicCookieStore()).build()) { httpClient =>
        val signin = new HttpPost(s"http://localhost:${server.port}/signin")
        signin.setEntity(
          new UrlEncodedFormEntity(
            JArrays.asList(
              new BasicNameValuePair("userName", "root"),
              new BasicNameValuePair("password", "root")
            )
          )
        )
        val signinResponse = httpClient.execute(signin)
        EntityUtils.consume(signinResponse.getEntity)
        assert(signinResponse.getStatusLine.getStatusCode < 400, "signin failed")

        // The admin renames themselves through the admin UI, using the same session as above
        val rename = new HttpPost(s"http://localhost:${server.port}/admin/users/root/_rename")
        rename.setConfig(RequestConfig.custom().setRedirectsEnabled(false).build())
        rename.setEntity(new UrlEncodedFormEntity(JArrays.asList(new BasicNameValuePair("newUserName", "root2"))))
        val renameResponse = httpClient.execute(rename)
        EntityUtils.consume(renameResponse.getEntity)
        assert(renameResponse.getStatusLine.getStatusCode == 302, "admin self-rename request failed")

        // Without signing in again, the very same session must now be recognized as "root2" -
        // exactly as the self-service danger zone rename keeps the session in sync.
        val whoami = new HttpGet(s"http://localhost:${server.port}/api/v3/user")
        val whoamiResponse = httpClient.execute(whoami)
        val whoamiBody = EntityUtils.toString(whoamiResponse.getEntity, "UTF-8")
        assert(whoamiResponse.getStatusLine.getStatusCode == 200, "session should still be authenticated")
        assert(
          whoamiBody.contains("\"login\":\"root2\""),
          s"expected the session to reflect the new name, got $whoamiBody"
        )

        // The still-authenticated session must also continue to be treated as an administrator
        val adminPage = new HttpGet(s"http://localhost:${server.port}/admin/users")
        val adminPageResponse = httpClient.execute(adminPage)
        EntityUtils.consume(adminPageResponse.getEntity)
        assert(adminPageResponse.getStatusLine.getStatusCode == 200, "session should still have admin access")
      }
    }
  }

  test("POST /admin/users/:userName/_rename is rejected for a non-administrator") {
    Using.resource(new TestingGitBucketServer(19986)) { server =>
      server.createUser("jack", "jackpw", "jack@example.com", "root", "root")
      server.createUser("kate", "katepw", "kate@example.com", "root", "root")

      val status = postWeb(server, "/admin/users/kate/_rename", "jack", "jackpw", Map("newUserName" -> "kate2"))
      assert(status == 401, "a non-administrator must not be able to use the admin rename route")

      val unchanged = server.getAnonymousApi("/api/v3/users/kate")
      assert(unchanged.status == 200, "kate's account must be unaffected by the rejected rename")
    }
  }

  test("POST /admin/users/:userName/_rename is rejected when the new user name is already taken") {
    Using.resource(new TestingGitBucketServer(19985)) { server =>
      server.createUser("liam", "liampw", "liam@example.com", "root", "root")
      server.createUser("mona", "monapw", "mona@example.com", "root", "root")

      val status = postWeb(server, "/admin/users/liam/_rename", "root", "root", Map("newUserName" -> "mona"))
      assert(status == 400, "renaming to an existing user name must be rejected")

      val liamStillExists = server.getAnonymousApi("/api/v3/users/liam")
      assert(liamStillExists.status == 200, "liam's account must not have been renamed away")
    }
  }

  test("POST /admin/users/:userName/_rename is rejected when the new user name is reserved") {
    Using.resource(new TestingGitBucketServer(19984)) { server =>
      server.createUser("noah", "noahpw", "noah@example.com", "root", "root")

      val status = postWeb(server, "/admin/users/noah/_rename", "root", "root", Map("newUserName" -> "admin"))
      assert(status == 400, "renaming to a reserved user name must be rejected")

      val noahStillExists = server.getAnonymousApi("/api/v3/users/noah")
      assert(noahStillExists.status == 200, "noah's account must not have been renamed away")
    }
  }
}
