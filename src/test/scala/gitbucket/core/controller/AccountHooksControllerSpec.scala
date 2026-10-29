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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
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

  /** Run a local hook receiver that answers 200 and counts the requests it gets. */
  private def withHookServer(f: (Int, AtomicInteger) => Unit): Unit = {
    val requests = new AtomicInteger()
    val hookServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0)
    hookServer.createContext(
      "/",
      exchange => {
        exchange.getRequestBody.readAllBytes()
        requests.incrementAndGet()
        exchange.sendResponseHeaders(200, -1) // no body
        exchange.close()
      }
    )
    hookServer.start()
    try {
      f(hookServer.getAddress.getPort, requests)
    } finally {
      hookServer.stop(0)
    }
  }

  test("POST /:userName/_hooks/test returns the status code of the hook response") {
    withHookServer { (port, requests) =>
      Using.resource(new TestingGitBucketServer(19998)) { server =>
        server.updateSettings("webhook.block_private_address" -> "false")
        val json = testHook(server, s"http://localhost:$port/")
        assert(json \ "response" \ "status" == JInt(200))
        assert(requests.get == 1)
      }
    }
  }

  test("POST /:userName/_hooks/test does not send to a private address by default") {
    withHookServer { (port, requests) =>
      Using.resource(new TestingGitBucketServer(19998)) { server =>
        val json = testHook(server, s"http://localhost:$port/")
        assert(json \ "response" \ "error" == JString("invalid url"))
        assert(requests.get == 0)
      }
    }
  }

  test("POST /:userName/_hooks/test reports a receiver that doesn't answer within the timeout") {
    val release = new CountDownLatch(1)
    val hookServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0)
    hookServer.createContext("/", _ => release.await()) // accepts the request, never answers
    hookServer.start()
    try {
      Using.resource(new TestingGitBucketServer(19998)) { server =>
        server.updateSettings("webhook.block_private_address" -> "false", "webhook.timeout" -> "1")
        val json = testHook(server, s"http://localhost:${hookServer.getAddress.getPort}/")
        assert(json \ "response" \ "error" == JString("timed out"))
      }
    } finally {
      release.countDown()
      hookServer.stop(0)
    }
  }

  test("POST /:userName/_hooks/test sends to a private address in the whitelist") {
    withHookServer { (port, requests) =>
      Using.resource(new TestingGitBucketServer(19998)) { server =>
        server.updateSettings("webhook.whitelist" -> "127.0.0.0/8")
        val json = testHook(server, s"http://127.0.0.1:$port/")
        assert(json \ "response" \ "status" == JInt(200))
        assert(requests.get == 1)
      }
    }
  }
}
