package gitbucket.core.controller

import gitbucket.core.TestingGitBucketServer
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.{HttpGet, HttpPost, HttpRequestBase}
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.{BasicCookieStore, CloseableHttpClient, HttpClients}
import org.apache.http.message.BasicNameValuePair
import org.apache.http.util.EntityUtils
import org.scalatest.funsuite.AnyFunSuite
import gitbucket.core.scalatra.CsrfTokenSupport

import java.nio.file.{Files, Paths}
import java.util.Base64
import scala.jdk.CollectionConverters.*
import scala.util.Using

/**
 * Need to run `sbt package` before running this test.
 */
class CsrfControllerSpec extends AnyFunSuite {

  private val port = 19993
  private val Field = CsrfTokenSupport.DefaultKey
  private def url(path: String) = s"http://localhost:$port$path"

  private val noRedirects = RequestConfig.custom().setRedirectsEnabled(false).build()

  private def withClient[T](f: (CloseableHttpClient, BasicCookieStore) => T): T = {
    val cookies = new BasicCookieStore()
    Using.resource(HttpClients.custom().setDefaultCookieStore(cookies).build())(f(_, cookies))
  }

  private def cookie(cookies: BasicCookieStore, name: String): Option[String] =
    cookies.getCookies.asScala.find(_.getName == name).map(_.getValue)

  private def execute(client: CloseableHttpClient, request: HttpRequestBase): (Int, String) = {
    request.setConfig(noRedirects)
    Using.resource(client.execute(request)) { res =>
      (res.getStatusLine.getStatusCode, Option(res.getEntity).map(EntityUtils.toString(_, "UTF-8")).getOrElse(""))
    }
  }

  private def get(client: CloseableHttpClient, path: String): (Int, String) = execute(client, new HttpGet(url(path)))

  private def postForm(
    client: CloseableHttpClient,
    path: String,
    fields: Seq[(String, String)],
    headers: (String, String)*
  ): Int = {
    val post = new HttpPost(url(path))
    post.setEntity(new UrlEncodedFormEntity(fields.map { case (k, v) => new BasicNameValuePair(k, v) }.asJava))
    headers.foreach { case (k, v) => post.setHeader(k, v) }
    execute(client, post)._1
  }

  /** Posts a file upload; the token as multipart field and/or in headers. Returns the status code and body. */
  private def postMultipart(
    client: CloseableHttpClient,
    path: String,
    csrfField: Option[String] = None,
    headers: Seq[(String, String)] = Nil,
    fileName: String = "a.png",
    content: String = "png"
  ): (Int, String) = {
    val post = new HttpPost(url(path))
    val boundary = "----upload"
    val field = csrfField.map { token =>
      s"--$boundary\r\nContent-Disposition: form-data; name=\"$Field\"\r\n\r\n$token\r\n"
    }
    val body = field.getOrElse("") + s"--$boundary\r\n" +
      s"Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\n" +
      "Content-Type: application/octet-stream\r\n\r\n" +
      s"$content\r\n--$boundary--\r\n"
    post.setHeader("Content-Type", s"multipart/form-data; boundary=$boundary")
    headers.foreach { case (k, v) => post.setHeader(k, v) }
    post.setEntity(new StringEntity(body, "UTF-8"))
    execute(client, post)
  }

  /** Opens the sign-in page and returns the token from it. */
  private def signinPage(client: CloseableHttpClient, cookies: BasicCookieStore): String = {
    get(client, "/signin")
    val token = cookie(cookies, "XSRF-TOKEN")
    assert(token.isDefined, "no XSRF-TOKEN cookie on the sign-in page")
    token.get
  }

  /** Opens the sign-in page, signs in with the token from it and returns the session's new token. */
  private def signin(client: CloseableHttpClient, cookies: BasicCookieStore): String = {
    val signinToken = signinPage(client, cookies)
    val status = postForm(client, "/signin", Seq("userName" -> "root", "password" -> "root", Field -> signinToken))
    assert(status == 302, "signin failed")
    val token = cookie(cookies, "XSRF-TOKEN")
    assert(token.isDefined && !token.contains(signinToken), "no new token after sign-in")
    token.get
  }

  private def editProfile(fullName: String): Seq[(String, String)] =
    Seq("fullName" -> fullName, "mailAddress" -> "root@localhost")

  /** The full name as shown in the profile form. */
  private def fullName(client: CloseableHttpClient): String =
    """id="fullName" class="form-control" value="([^"]*)"""".r
      .findFirstMatchIn(get(client, "/root/_edit")._2)
      .get
      .group(1)

  test("a form post of a signed-in user without the token is refused") {
    Using.resource(new TestingGitBucketServer(port)) { _ =>
      withClient { (client, cookies) =>
        signin(client, cookies)
        assert(postForm(client, "/root/_edit", editProfile("Forged")) == 403)
        assert(postForm(client, "/root/_edit", editProfile("Forged") :+ (Field -> "wrong")) == 403)
        assert(fullName(client) != "Forged")
      }
    }
  }

  test("a form post with the token in the form field is accepted") {
    Using.resource(new TestingGitBucketServer(port)) { _ =>
      withClient { (client, cookies) =>
        val token = signin(client, cookies)
        assert(postForm(client, "/root/_edit", editProfile("Field") :+ (Field -> token)) == 302)
        assert(fullName(client) == "Field")
      }
    }
  }

  test("the token is accepted in the X-CSRF-TOKEN and the X-XSRF-TOKEN header") {
    Using.resource(new TestingGitBucketServer(port)) { _ =>
      withClient { (client, cookies) =>
        val token = signin(client, cookies)
        assert(postForm(client, "/root/_edit", editProfile("Csrf"), "X-CSRF-TOKEN" -> token) == 302)
        assert(fullName(client) == "Csrf")
        assert(postForm(client, "/root/_edit", editProfile("Xsrf"), "X-XSRF-TOKEN" -> token) == 302)
        assert(fullName(client) == "Xsrf")
      }
    }
  }

  test("sign-in from the sign-in page needs the token") {
    Using.resource(new TestingGitBucketServer(port)) { _ =>
      withClient { (client, cookies) =>
        get(client, "/signin")
        assert(postForm(client, "/signin", Seq("userName" -> "root", "password" -> "root")) == 403)
        assert(get(client, "/root/_edit")._1 == 401, "signed in without the token")
        signin(client, cookies)
        assert(get(client, "/root/_edit")._1 == 200)
      }
    }
  }

  test("a sign-in without a session is refused (login CSRF)") {
    Using.resource(new TestingGitBucketServer(port)) { _ =>
      withClient { (client, _) =>
        assert(postForm(client, "/signin", Seq("userName" -> "root", "password" -> "root")) == 403)
        assert(postForm(client, "/signin", Seq("userName" -> "root", "password" -> "root", Field -> "x")) == 403)
        assert(get(client, "/root/_edit")._1 == 401, "signed in without a session")
      }
    }
  }

  test("sign-in replaces the token: the token from before sign-in is refused afterwards") {
    Using.resource(new TestingGitBucketServer(port)) { _ =>
      withClient { (client, cookies) =>
        val signinToken = signinPage(client, cookies)
        val status = postForm(client, "/signin", Seq("userName" -> "root", "password" -> "root", Field -> signinToken))
        assert(status == 302)
        assert(postForm(client, "/root/_edit", editProfile("Before") :+ (Field -> signinToken)) == 403)
        val token = cookie(cookies, "XSRF-TOKEN").get
        assert(token != signinToken)
        assert(postForm(client, "/root/_edit", editProfile("After") :+ (Field -> token)) == 302)
      }
    }
  }

  test("a post with cookies but without a valid session is refused (expired session, server restart)") {
    Using.resource(new TestingGitBucketServer(port)) { _ =>
      withClient { (client, _) =>
        val status =
          postForm(client, "/root/_edit", editProfile("Stale") :+ (Field -> "old"), "Cookie" -> "JSESSIONID=stale")
        assert(status == 403)
      }
    }
  }

  test("a sign-in with cookies but without a valid session is refused") {
    Using.resource(new TestingGitBucketServer(port)) { _ =>
      withClient { (client, _) =>
        val fields = Seq("userName" -> "root", "password" -> "root", Field -> "old")
        assert(postForm(client, "/signin", fields, "Cookie" -> "JSESSIONID=stale") == 403)
      }
    }
  }

  test("requests without cookies get no CSRF check (webhook receivers, scripts)") {
    Using.resource(new TestingGitBucketServer(port)) { _ =>
      withClient { (client, _) =>
        val (status, body) = execute(client, new HttpPost(url("/root/_edit")))
        assert(status != 403 && !body.contains("CSRF"), s"$status $body")
      }
    }
  }

  test("pages carry the token in a meta tag and in their forms") {
    Using.resource(new TestingGitBucketServer(port)) { _ =>
      withClient { (client, cookies) =>
        val token = signin(client, cookies)
        val (status, page) = get(client, "/root/_edit")
        assert(status == 200)
        assert(page.contains(s"""<meta name="csrf-token" content="$token""""))
        assert(page.contains(s"""name="$Field" value="$token""""))
      }
    }
  }

  test("the XSRF-TOKEN cookie is sent with SameSite=Lax") {
    Using.resource(new TestingGitBucketServer(port)) { _ =>
      withClient { (client, _) =>
        val get = new HttpGet(url("/signin"))
        val setCookie = Using.resource(client.execute(get)) { res =>
          EntityUtils.consume(res.getEntity)
          res.getHeaders("Set-Cookie").map(_.getValue).find(_.startsWith("XSRF-TOKEN="))
        }
        assert(setCookie.isDefined, "no XSRF-TOKEN cookie")
        assert(setCookie.get.contains("SameSite=Lax"), setCookie.get)
        assert(setCookie.get.contains("Path=/"), setCookie.get)
      }
    }
  }

  test("API requests authenticated by the session cookie need the token") {
    Using.resource(new TestingGitBucketServer(port)) { _ =>
      withClient { (client, cookies) =>
        val token = signin(client, cookies)
        def createRepository(name: String, headers: (String, String)*): Int = {
          val post = new HttpPost(url("/api/v3/user/repos"))
          post.setHeader("Content-Type", "application/json")
          headers.foreach { case (k, v) => post.setHeader(k, v) }
          post.setEntity(new StringEntity(s"""{"name":"$name"}""", "UTF-8"))
          execute(client, post)._1
        }
        assert(createRepository("forged") == 403)
        assert(createRepository("allowed", "X-CSRF-TOKEN" -> token) == 200)
      }
    }
  }

  private val rootBasic = "Basic " + Base64.getEncoder.encodeToString("root:root".getBytes("UTF-8"))

  /** Creates a repository through the API, authenticated with Basic authentication. */
  private def createRepositoryWithBasic(client: CloseableHttpClient, name: String, headers: (String, String)*): Int = {
    val post = new HttpPost(url("/api/v3/user/repos"))
    post.setHeader("Authorization", rootBasic)
    post.setHeader("Content-Type", "application/json")
    headers.foreach { case (k, v) => post.setHeader(k, v) }
    post.setEntity(new StringEntity(s"""{"name":"$name"}""", "UTF-8"))
    execute(client, post)._1
  }

  test("API clients with an Authorization header are not checked, also when they keep the session cookie") {
    Using.resource(new TestingGitBucketServer(port)) { _ =>
      withClient { (client, cookies) =>
        assert(createRepositoryWithBasic(client, "first") == 200)
        assert(cookie(cookies, "JSESSIONID").isDefined, "the API response sets no session cookie")
        assert(createRepositoryWithBasic(client, "second") == 200) // sends the session cookie of the first call
        assert(get(client, "/signin")._1 == 200) // a session with a token, but no signed-in user
        assert(createRepositoryWithBasic(client, "third") == 200)
      }
    }
  }

  test("an Authorization header doesn't skip the check of a signed-in session") {
    Using.resource(new TestingGitBucketServer(port)) { _ =>
      withClient { (client, cookies) =>
        val token = signin(client, cookies)
        // the web UI uses the session's account and ignores the header, e.g. one the browser sends by itself
        assert(postForm(client, "/root/_edit", editProfile("Forged"), "Authorization" -> "Basic eDp4") == 403)
        assert(postForm(client, "/root/_edit", editProfile("Forged"), "Authorization" -> rootBasic) == 403)
        assert(fullName(client) != "Forged")
        assert(createRepositoryWithBasic(client, "forged") == 403)
        assert(createRepositoryWithBasic(client, "allowed", "X-CSRF-TOKEN" -> token) == 200)
      }
    }
  }

  test("a sign-in with an Authorization header needs the token") {
    Using.resource(new TestingGitBucketServer(port)) { _ =>
      withClient { (client, cookies) =>
        get(client, "/signin")
        val credentials = Seq("userName" -> "root", "password" -> "root")
        assert(postForm(client, "/signin", credentials, "Authorization" -> rootBasic) == 403)
        cookies.clear()
        assert(postForm(client, "/signin", credentials, "Authorization" -> rootBasic) == 403)
        assert(get(client, "/root/_edit")._1 == 401, "signed in without the token")
      }
    }
  }

  test("uploads need the token, in the header or as a multipart field") {
    Using.resource(new TestingGitBucketServer(port)) { _ =>
      withClient { (client, cookies) =>
        val token = signin(client, cookies)
        assert(postMultipart(client, "/upload/image")._1 == 403)
        assert(postMultipart(client, "/upload/image", headers = Seq("X-CSRF-TOKEN" -> token))._1 == 200)
        assert(postMultipart(client, "/upload/image", csrfField = Some(token))._1 == 200)
      }
    }
  }

  test("all upload routes need the token") {
    Using.resource(new TestingGitBucketServer(port)) { server =>
      server.client("root", "root").createRepository("files").autoInit(true).create()
      withClient { (client, cookies) =>
        val token = signin(client, cookies)
        Seq(
          "/upload/tmp",
          "/upload/file/root/files",
          "/upload/wiki/root/files",
          "/upload/release/root/files/v1.0"
        ).foreach { path =>
          assert(postMultipart(client, path)._1 == 403, path)
          val (status, body) = postMultipart(client, path, headers = Seq("X-CSRF-TOKEN" -> token))
          assert(status == 200, s"$path: $status $body")
        }
      }
    }
  }

  test("the data import form sends the token as multipart field") {
    Using.resource(new TestingGitBucketServer(port)) { _ =>
      withClient { (client, cookies) =>
        val token = signin(client, cookies)
        assert(postMultipart(client, "/upload/import", fileName = "empty.sql", content = "")._1 == 403)
        val (status, _) =
          postMultipart(client, "/upload/import", csrfField = Some(token), fileName = "empty.sql", content = "")
        assert(status == 302)
      }
    }
  }

  test("a broken multipart body gets the upload servlet's response, not an error of the token check") {
    Using.resource(new TestingGitBucketServer(port)) { _ =>
      withClient { (client, cookies) =>
        val token = signin(client, cookies)
        val post = new HttpPost(url("/upload/image"))
        post.setHeader("Content-Type", "multipart/form-data; boundary=----upload")
        post.setHeader("X-CSRF-TOKEN", token)
        post.setEntity(new StringEntity("not a multipart body", "UTF-8"))
        // 400 is the upload servlet's own answer, as for a request that skips the check
        val response = execute(client, post)
        assert(response._1 == 400, response)
      }
    }
  }

  test("an old token is refused after signing out and in again") {
    Using.resource(new TestingGitBucketServer(port)) { _ =>
      withClient { (client, cookies) =>
        val oldToken = signin(client, cookies)
        get(client, "/signout")
        val newToken = signin(client, cookies)
        assert(newToken != oldToken)
        assert(postForm(client, "/root/_edit", editProfile("Old") :+ (Field -> oldToken)) == 403)
        assert(postForm(client, "/root/_edit", editProfile("New") :+ (Field -> newToken)) == 302)
      }
    }
  }

  test("git and git-lfs requests are not checked") {
    Using.resource(new TestingGitBucketServer(port)) { server =>
      server.client("root", "root").createRepository("files").autoInit(true).create()
      withClient { (client, cookies) =>
        signin(client, cookies) // a session with a token, but no token in the requests
        val receivePack = new HttpPost(url("/git/root/files.git/git-receive-pack"))
        receivePack.setHeader("Content-Type", "application/x-git-receive-pack-request")
        val (gitStatus, gitBody) = execute(client, receivePack)
        assert(gitStatus != 403 && !gitBody.contains("CSRF"), s"$gitStatus $gitBody")
      }
      withClient { (lfsClient, _) =>
        val batch = new HttpPost(url("/root/files/info/lfs/objects/batch"))
        val credentials = Base64.getEncoder.encodeToString("root:root".getBytes("UTF-8"))
        batch.setHeader("Authorization", s"Basic $credentials")
        batch.setHeader("Content-Type", "application/vnd.git-lfs+json")
        batch.setEntity(new StringEntity("""{"operation":"upload","objects":[]}""", "UTF-8"))
        val (lfsStatus, lfsBody) = execute(lfsClient, batch)
        assert(lfsStatus != 403 && !lfsBody.contains("CSRF"), s"$lfsStatus $lfsBody")
      }
    }
  }

  test("every POST form in the templates contains the token field") {
    val forms = for {
      file <- Files.walk(Paths.get("src/main/twirl")).iterator().asScala.filter(_.toString.endsWith(".html")).toSeq
      lines = Files.readAllLines(file).asScala.toIndexedSeq
      (line, index) <- lines.zipWithIndex if """(?i)<form[^>]*method="?post""".r.findFirstIn(line).isDefined
    } yield {
      val tagEnd = (index until lines.size).find(i => lines(i).contains(">")).get
      (s"$file:${index + 1}", lines.lift(tagEnd + 1).map(_.trim))
    }
    assert(forms.size >= 50)
    val missing = forms.collect { case (location, next) if !next.contains("@helpers.csrfField") => location }
    assert(missing.isEmpty, missing.mkString(", "))
  }
}
