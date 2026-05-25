package gitbucket.core

import java.net.InetSocketAddress
import java.nio.file.Files
import java.io.File

import gitbucket.core.util.{FileUtil, HttpClientUtil}
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.{HttpGet, HttpPost}
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.{BasicCookieStore, CloseableHttpClient, HttpClients}
import org.apache.http.message.BasicNameValuePair
import org.apache.http.util.EntityUtils
import org.eclipse.jetty.server.handler.StatisticsHandler
import org.eclipse.jetty.server.{Handler, Server}
import org.eclipse.jetty.webapp.WebAppContext
import org.kohsuke.github.{GHRepository, GitHub, GitHubBuilder}

import java.util.{Arrays => JArrays}
import java.util.Base64
import scala.util.Using

class TestingGitBucketServer(val port: Int = 19999) extends AutoCloseable {
  case class ApiResponse(status: Int, body: String)

  private var server: Server = null
  private var dir: File = null

  start()

  private def start(): Unit = {
    System.setProperty("java.awt.headless", "true")

    dir = Files.createTempDirectory("gitbucket-test-").toFile
    System.setProperty("gitbucket.home", dir.getAbsolutePath)

    val address = new InetSocketAddress(port)
    server = new Server(address)

    val context = new WebAppContext
    context.setResourceBase("./target/webapp")
    context.setContextPath("")
    context.setInitParameter("org.eclipse.jetty.servlet.Default.dirAllowed", "false")
    context.setServer(server)

    val handler = addStatisticsHandler(context)
    server.setHandler(handler)

    server.start()

    HttpClientUtil.withHttpClient(None) { httpClient =>
      var launched = false
      var count = 0
      while (!launched && count < 10) {
        Thread.sleep(500)
        val res = httpClient.execute(new HttpGet(s"http://localhost:${port}/"))
        launched = res.getStatusLine.getStatusCode == 200
        count += 1
      }
    }
  }

  def client(login: String, password: String): GitHub =
    new GitHubBuilder()
      .withEndpoint(s"http://localhost:${port}/api/v3")
      .withPassword(login, password)
      .build()

  def getDirectory(): File = dir

  /** Create a user account via the admin REST API. */
  def createUser(login: String, password: String, email: String, adminLogin: String, adminPassword: String): Unit = {
    HttpClientUtil.withHttpClient(None) { httpClient =>
      val post = new HttpPost(s"http://localhost:$port/api/v3/admin/users")
      val credentials = Base64.getEncoder.encodeToString(s"$adminLogin:$adminPassword".getBytes("UTF-8"))
      post.setHeader("Authorization", s"Basic $credentials")
      post.setHeader("Content-Type", "application/json")
      post.setEntity(
        new StringEntity(
          s"""{"login":"$login","password":"$password","email":"$email"}""",
          "UTF-8"
        )
      )
      val response = httpClient.execute(post)
      EntityUtils.consume(response.getEntity)
      val status = response.getStatusLine.getStatusCode
      assert(status == 200, s"createUser failed with status $status")
    }
  }

  /** Create an organization via the admin REST API. */
  def createOrganization(login: String, adminLogin: String, adminPassword: String): Unit = {
    HttpClientUtil.withHttpClient(None) { httpClient =>
      val post = new HttpPost(s"http://localhost:$port/api/v3/admin/organizations")
      val credentials = Base64.getEncoder.encodeToString(s"$adminLogin:$adminPassword".getBytes("UTF-8"))
      post.setHeader("Authorization", s"Basic $credentials")
      post.setHeader("Content-Type", "application/json")
      post.setEntity(
        new StringEntity(
          s"""{"login":"$login","admin":"$adminLogin"}""",
          "UTF-8"
        )
      )
      val response = httpClient.execute(post)
      EntityUtils.consume(response.getEntity)
      val status = response.getStatusLine.getStatusCode
      assert(status == 200, s"createOrganization failed with status $status")
    }
  }

  /** Fork a repository as the given user via the web UI form endpoint. */
  def forkRepository(owner: String, repository: String, asLogin: String, asPassword: String): Unit = {
    withWebSession(asLogin, asPassword) { httpClient =>
      val fork = new HttpPost(s"http://localhost:$port/$owner/$repository/fork")
      fork.setEntity(new UrlEncodedFormEntity(JArrays.asList(new BasicNameValuePair("account", asLogin))))
      val forkResponse = httpClient.execute(fork)
      EntityUtils.consume(forkResponse.getEntity)
      assert(forkResponse.getStatusLine.getStatusCode == 302, "fork request failed")
    }
  }

  /** Rename a repository via the web settings form. */
  def renameRepository(owner: String, oldName: String, newName: String, login: String, password: String): Unit = {
    withWebSession(login, password) { httpClient =>
      val rename = new HttpPost(s"http://localhost:$port/$owner/$oldName/settings/rename")
      rename.setEntity(new UrlEncodedFormEntity(JArrays.asList(new BasicNameValuePair("repositoryName", newName))))
      val renameResponse = httpClient.execute(rename)
      EntityUtils.consume(renameResponse.getEntity)
      assert(renameResponse.getStatusLine.getStatusCode == 302, "rename request failed")
    }
  }

  /** Delete a repository via the web settings form. */
  def deleteRepository(owner: String, name: String, login: String, password: String): Unit = {
    withWebSession(login, password) { httpClient =>
      val delete = new HttpPost(s"http://localhost:$port/$owner/$name/settings/delete")
      delete.setEntity(new UrlEncodedFormEntity(JArrays.asList()))
      val deleteResponse = httpClient.execute(delete)
      EntityUtils.consume(deleteResponse.getEntity)
      assert(deleteResponse.getStatusLine.getStatusCode == 302, "delete request failed")
    }
  }

  /** Fork a repository via the REST API; returns the HTTP status code and response body. */
  def forkRepositoryViaApi(
    owner: String,
    repository: String,
    organization: Option[String],
    login: String,
    password: String
  ): ApiResponse = {
    HttpClientUtil.withHttpClient(None) { httpClient =>
      val post = new HttpPost(s"http://localhost:$port/api/v3/repos/$owner/$repository/forks")
      val credentials = Base64.getEncoder.encodeToString(s"$login:$password".getBytes("UTF-8"))
      post.setHeader("Authorization", s"Basic $credentials")
      post.setHeader("Content-Type", "application/json")
      val body = organization.map(org => s"""{"organization":"$org"}""").getOrElse("{}")
      post.setEntity(new StringEntity(body, "UTF-8"))
      val response = httpClient.execute(post)
      val responseBody = Option(response.getEntity).map(EntityUtils.toString(_, "UTF-8")).getOrElse("")
      ApiResponse(response.getStatusLine.getStatusCode, responseBody)
    }
  }

  /** Wait for a repository to appear via the API. Forking is asynchronous so
   *  callers cannot rely on the fork POST completing synchronously. */
  def waitForRepository(client: GitHub, fullName: String, timeoutMillis: Long = 10000): GHRepository = {
    val deadline = System.currentTimeMillis() + timeoutMillis
    while (System.currentTimeMillis() < deadline) {
      try return client.getRepository(fullName)
      catch { case _: java.io.IOException => Thread.sleep(500) }
    }
    throw new AssertionError(s"Repository $fullName did not appear within ${timeoutMillis}ms")
  }

  /** Make an unauthenticated GET request and return the HTTP status code. */
  def getAnonymousApiStatus(path: String): Int = {
    HttpClientUtil.withHttpClient(None) { httpClient =>
      val response = httpClient.execute(new HttpGet(s"http://localhost:$port$path"))
      EntityUtils.consume(response.getEntity)
      response.getStatusLine.getStatusCode
    }
  }

  /** Disable forking on a repository via the web settings options form. */
  def disableFork(owner: String, name: String, login: String, password: String): Unit = {
    withWebSession(login, password) { httpClient =>
      val post = new HttpPost(s"http://localhost:$port/$owner/$name/settings/options")
      post.setEntity(
        new UrlEncodedFormEntity(
          JArrays.asList(
            new BasicNameValuePair("issuesOption", "PUBLIC"),
            new BasicNameValuePair("wikiOption", "PUBLIC"),
            new BasicNameValuePair("mergeOptions", "merge-commit"),
            new BasicNameValuePair("defaultMergeOption", "merge-commit"),
            new BasicNameValuePair("safeMode", "true"),
            new BasicNameValuePair("allowFork", "false")
          )
        )
      )
      val response = httpClient.execute(post)
      EntityUtils.consume(response.getEntity)
      assert(response.getStatusLine.getStatusCode == 302, "disableFork settings update failed")
    }
  }

  private def withWebSession[T](login: String, password: String)(f: CloseableHttpClient => T): T = {
    Using.resource(HttpClients.custom().setDefaultCookieStore(new BasicCookieStore()).build()) { httpClient =>
      val signin = new HttpPost(s"http://localhost:$port/signin")
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
      f(httpClient)
    }
  }

  private def addStatisticsHandler(handler: Handler) = { // The graceful shutdown is implemented via the statistics handler.
    // See the following: https://bugs.eclipse.org/bugs/show_bug.cgi?id=420142
    val statisticsHandler = new StatisticsHandler
    statisticsHandler.setHandler(handler)
    statisticsHandler
  }

  def close(): Unit = {
    server.stop()
    FileUtil.deleteIfExists(dir)
  }
}
