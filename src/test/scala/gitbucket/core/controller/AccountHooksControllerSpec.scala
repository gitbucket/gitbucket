package gitbucket.core.controller

import com.sun.net.httpserver.HttpServer
import gitbucket.core.TestingGitBucketServer
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.HttpPost
import org.apache.http.impl.client.{BasicCookieStore, HttpClients}
import org.apache.http.message.BasicNameValuePair
import org.apache.http.util.EntityUtils
import org.json4s.*
import org.json4s.jackson.JsonMethods.parse
import org.scalatest.funsuite.AnyFunSuite

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.{Arrays => JArrays}
import scala.util.Using

/**
 * Need to run `sbt package` before running this test.
 */
class AccountHooksControllerSpec extends AnyFunSuite {

  /** Call the account test hook endpoint through a signed-in web session and return the JSON response. */
  private def testHook(server: TestingGitBucketServer, hookUrl: String): JValue = {
    Using.resource(HttpClients.custom().setDefaultCookieStore(new BasicCookieStore()).build()) { httpClient =>
      val signin = new HttpPost(s"http://localhost:${server.port}/signin")
      signin.setEntity(
        new UrlEncodedFormEntity(
          JArrays.asList(new BasicNameValuePair("userName", "root"), new BasicNameValuePair("password", "root"))
        )
      )
      val signinResponse = httpClient.execute(signin)
      EntityUtils.consume(signinResponse.getEntity)
      assert(signinResponse.getStatusLine.getStatusCode < 400, "signin failed")

      val post = new HttpPost(s"http://localhost:${server.port}/root/_hooks/test")
      post.addHeader("X-Requested-With", "XMLHttpRequest")
      post.setEntity(
        new UrlEncodedFormEntity(
          JArrays.asList(
            new BasicNameValuePair("url", hookUrl),
            new BasicNameValuePair("ctype", "json"),
            new BasicNameValuePair("token", ""),
            new BasicNameValuePair("events", "push")
          )
        )
      )
      val response = httpClient.execute(post)
      assert(response.getStatusLine.getStatusCode == 200)
      parse(EntityUtils.toString(response.getEntity, StandardCharsets.UTF_8))
    }
  }

  test("POST /:userName/_hooks/test returns the status code of the hook response") {
    val hookServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0)
    hookServer.createContext(
      "/",
      exchange => {
        exchange.getRequestBody.readAllBytes()
        exchange.sendResponseHeaders(200, -1) // no body
        exchange.close()
      }
    )
    hookServer.start()
    try {
      Using.resource(new TestingGitBucketServer(19998)) { server =>
        val json = testHook(server, s"http://localhost:${hookServer.getAddress.getPort}/")
        assert(json \ "response" \ "status" == JInt(200))
      }
    } finally {
      hookServer.stop(0)
    }
  }
}
