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
class AccountDeleteControllerSpec extends AnyFunSuite {

  test("GET /:userName/_delete does not delete the last administrator on a fresh install") {
    Using.resource(new TestingGitBucketServer(19996)) { server =>
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

        val delete = new HttpGet(s"http://localhost:${server.port}/root/_delete")
        delete.setConfig(RequestConfig.custom().setRedirectsEnabled(false).build())
        val deleteResponse = httpClient.execute(delete)
        EntityUtils.consume(deleteResponse.getEntity)
        // On a fresh install root is the only administrator, so the delete must be rejected
        // and redirect back to the danger zone rather than invalidating the session.
        assert(deleteResponse.getStatusLine.getStatusCode == 302)
        val location = Option(deleteResponse.getFirstHeader("Location")).map(_.getValue).getOrElse("")
        assert(location.endsWith("/root/_danger_zone"), s"expected redirect to /root/_danger_zone, got $location")

        // root must still be authenticated - the account/session must not have been removed/invalidated.
        val whoami = new HttpGet(s"http://localhost:${server.port}/root/_edit")
        val whoamiResponse = httpClient.execute(whoami)
        EntityUtils.consume(whoamiResponse.getEntity)
        assert(whoamiResponse.getStatusLine.getStatusCode == 200, "root account should still exist and be logged in")
      }
    }
  }
}
