package gitbucket.core.controller

import gitbucket.core.TestingGitBucketServer
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.{HttpGet, HttpPost}
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.{BasicCookieStore, CloseableHttpClient, HttpClients}
import org.apache.http.impl.cookie.BasicClientCookie
import org.apache.http.message.BasicNameValuePair
import org.apache.http.util.EntityUtils
import org.scalatest.funsuite.AnyFunSuite

import java.io.File
import scala.jdk.CollectionConverters.*
import scala.util.Using

/**
 * Need to run `sbt package` before running this test.
 */
class SigninSessionControllerSpec extends AnyFunSuite {

  private val noRedirects = RequestConfig.custom().setRedirectsEnabled(false).build()

  private def withClient[T](f: (CloseableHttpClient, BasicCookieStore) => T): T = {
    val cookies = new BasicCookieStore()
    Using.resource(HttpClients.custom().setDefaultCookieStore(cookies).build())(f(_, cookies))
  }

  private def sessionId(cookies: BasicCookieStore): Option[String] =
    cookies.getCookies.asScala.find(_.getName == "JSESSIONID").map(_.getValue)

  // The server's own home (Directory in this JVM may point to an earlier test's home), and the session ID without
  // Jetty's worker name, which the cookie carries after it ("<id>.node0")
  private def uploadDir(server: TestingGitBucketServer, cookieValue: String): File =
    new File(server.getDirectory(), s"tmp/_upload/${cookieValue.split('.').head}")

  private def status(client: CloseableHttpClient, url: String): Int = {
    val get = new HttpGet(url)
    get.setConfig(noRedirects)
    Using.resource(client.execute(get)) { res =>
      EntityUtils.consume(res.getEntity)
      res.getStatusLine.getStatusCode
    }
  }

  /** Posts a form and returns the response's status code and Set-Cookie headers. */
  private def postForm(client: CloseableHttpClient, url: String, fields: (String, String)*): (Int, Seq[String]) = {
    val post = new HttpPost(url)
    post.setConfig(noRedirects)
    post.setEntity(new UrlEncodedFormEntity(fields.map { case (k, v) => new BasicNameValuePair(k, v) }.asJava))
    Using.resource(client.execute(post)) { res =>
      EntityUtils.consume(res.getEntity)
      (res.getStatusLine.getStatusCode, res.getHeaders("Set-Cookie").toSeq.map(_.getValue))
    }
  }

  /** Signs in and returns the Set-Cookie headers of the response. */
  private def signin(client: CloseableHttpClient, port: Int, user: String = "root"): Seq[String] = {
    val (status, cookies) =
      postForm(client, s"http://localhost:$port/signin", "userName" -> user, "password" -> user)
    assert(status == 302, "signin failed")
    cookies
  }

  /** Sets the admin flag of a user as root, via the admin page. */
  private def setAdmin(server: TestingGitBucketServer, user: String, isAdmin: Boolean): Unit =
    withClient { (root, _) =>
      signin(root, server.port)
      val (status, _) = postForm(
        root,
        s"http://localhost:${server.port}/admin/users/$user/_edituser",
        "userName" -> user,
        "fullName" -> user,
        "mailAddress" -> s"$user@example.com",
        "isAdmin" -> isAdmin.toString
      )
      assert(status == 302, "editing the user failed")
    }

  private def uploadImage(client: CloseableHttpClient, port: Int): String = {
    val post = new HttpPost(s"http://localhost:$port/upload/image")
    val boundary = "----upload"
    val body = s"--$boundary\r\n" +
      "Content-Disposition: form-data; name=\"file\"; filename=\"a.png\"\r\n" +
      "Content-Type: image/png\r\n\r\n" +
      s"png\r\n--$boundary--\r\n"
    post.setHeader("Content-Type", s"multipart/form-data; boundary=$boundary")
    post.setEntity(new StringEntity(body, "UTF-8"))
    Using.resource(client.execute(post)) { res =>
      assert(res.getStatusLine.getStatusCode == 200, "upload failed")
      EntityUtils.toString(res.getEntity)
    }
  }

  test("sign-in issues a new session ID and the ID from before sign-in stays anonymous") {
    Using.resource(new TestingGitBucketServer(19994)) { server =>
      withClient { (client, cookies) =>
        status(client, s"http://localhost:${server.port}/signin")
        val before = sessionId(cookies)
        assert(before.isDefined, "no session before sign-in")

        signin(client, server.port)
        val after = sessionId(cookies)
        assert(after.isDefined && after != before, "the session ID didn't change at sign-in")
        assert(status(client, s"http://localhost:${server.port}/root/_edit") == 200)

        withClient { (other, otherCookies) =>
          val oldSession = new BasicClientCookie("JSESSIONID", before.get)
          oldSession.setDomain("localhost")
          oldSession.setPath("/")
          otherCookies.addCookie(oldSession)
          assert(status(other, s"http://localhost:${server.port}/root/_edit") == 401)
        }
      }
    }
  }

  test("the session cookie is sent with SameSite=Lax") {
    Using.resource(new TestingGitBucketServer(19994)) { server =>
      withClient { (client, _) =>
        val sessionCookie = signin(client, server.port).find(_.startsWith("JSESSIONID="))
        assert(sessionCookie.isDefined, "no session cookie at sign-in")
        assert(sessionCookie.get.contains("SameSite=Lax"), sessionCookie.get)
        assert(sessionCookie.get.contains("HttpOnly"), sessionCookie.get)
      }
    }
  }

  test("a session ID in the URL is not accepted") {
    Using.resource(new TestingGitBucketServer(19994)) { server =>
      withClient { (client, cookies) =>
        signin(client, server.port)
        val id = sessionId(cookies).get
        withClient { (other, _) =>
          assert(status(other, s"http://localhost:${server.port}/root/_edit;jsessionid=$id") == 401)
        }
      }
    }
  }

  test("disabling a user ends the user's existing session") {
    Using.resource(new TestingGitBucketServer(19994)) { server =>
      server.createUser("alice", "alice", "alice@example.com", "root", "root")
      withClient { (alice, _) =>
        signin(alice, server.port, "alice")
        assert(status(alice, s"http://localhost:${server.port}/api/v3/user") == 200)
        assert(status(alice, s"http://localhost:${server.port}/new") == 200)

        server.suspendUser("alice", "root", "root")
        assert(status(alice, s"http://localhost:${server.port}/api/v3/user") == 401)
        assert(status(alice, s"http://localhost:${server.port}/new") == 401)
      }
    }
  }

  test("removing the admin flag applies to the user's existing session") {
    Using.resource(new TestingGitBucketServer(19994)) { server =>
      server.createUser("alice", "alice", "alice@example.com", "root", "root")
      setAdmin(server, "alice", isAdmin = true)
      withClient { (alice, _) =>
        signin(alice, server.port, "alice")
        assert(status(alice, s"http://localhost:${server.port}/admin/users") == 200)

        setAdmin(server, "alice", isAdmin = false)
        assert(status(alice, s"http://localhost:${server.port}/admin/users") == 401)
        assert(status(alice, s"http://localhost:${server.port}/alice/_edit") == 200, "alice is still signed in")
      }
    }
  }

  test("files uploaded before sign-in move to the new session ID") {
    Using.resource(new TestingGitBucketServer(19994)) { server =>
      withClient { (client, cookies) =>
        val fileId = uploadImage(client, server.port)
        val before = sessionId(cookies).get
        assert(new File(uploadDir(server, before), fileId).isFile)

        signin(client, server.port)
        val after = sessionId(cookies).get
        assert(!uploadDir(server, before).exists, "the upload directory of the old session ID is left behind")
        assert(new File(uploadDir(server, after), fileId).isFile)
      }
    }
  }
}
