package gitbucket.core.controller

import gitbucket.core.TestingGitBucketServer
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
class AccountDangerZoneControllerSpec extends AnyFunSuite {

  /** Perform a GET request through a signed-in web session (cookie-based) and return the HTTP status code. */
  private def getWeb(server: TestingGitBucketServer, path: String, login: String, password: String): Int = {
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

      val get = new HttpGet(s"http://localhost:${server.port}$path")
      val response = httpClient.execute(get)
      EntityUtils.consume(response.getEntity)
      response.getStatusLine.getStatusCode
    }
  }

  test("GET /:userName/_danger_zone returns OK for a logged in user") {
    Using.resource(new TestingGitBucketServer(19998)) { server =>
      assert(getWeb(server, "/root/_danger_zone", "root", "root") == 200)
    }
  }

  test("GET /:userName/_danger_zone behaves like /:userName/_edit when not authenticated") {
    Using.resource(new TestingGitBucketServer(19998)) { server =>
      val editStatus = server.getAnonymousApiStatus("/root/_edit")
      val dangerZoneStatus = server.getAnonymousApiStatus("/root/_danger_zone")
      assert(dangerZoneStatus == editStatus)
    }
  }
}
