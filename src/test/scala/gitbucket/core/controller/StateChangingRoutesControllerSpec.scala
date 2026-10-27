package gitbucket.core.controller

import gitbucket.core.TestingGitBucketServer
import gitbucket.core.util.StringUtil
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.{HttpGet, HttpPost, HttpRequestBase}
import org.apache.http.impl.client.{BasicCookieStore, CloseableHttpClient, HttpClients}
import org.apache.http.message.BasicNameValuePair
import org.apache.http.util.EntityUtils
import org.bouncycastle.bcpg.{ArmoredOutputStream, PublicKeyAlgorithmTags, PublicKeyPacket}
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyPair
import org.eclipse.jgit.api.Git
import org.json4s.*
import org.json4s.jackson.JsonMethods.parse
import org.scalatest.funsuite.AnyFunSuite

import java.io.{ByteArrayOutputStream, DataOutputStream, File}
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.util.{Base64, Date, Arrays => JArrays}
import scala.util.Using

/**
 * Routes of the web UI that delete or revert something must not run on a GET request: a link or an image on any
 * other site could trigger them with the session cookie of a signed-in user.
 *
 * For each route: a GET leaves the object in place, a POST changes it as before.
 *
 * Need to run `sbt package` before running this test.
 */
class StateChangingRoutesControllerSpec extends AnyFunSuite {

  private implicit val formats: Formats = DefaultFormats

  private case class Route(name: String, url: String, exists: () => Boolean)

  test("routes that delete or revert something do nothing on GET and work on POST") {
    Using.resource(new TestingGitBucketServer(19997)) { server =>
      withSession(server) { web =>
        val base = s"http://localhost:${server.port}"
        def api(path: String): JValue = {
          val response = server.getApi(path, "root", "root")
          assert(response.status == 200, s"GET $path: ${response.status}")
          parse(response.body)
        }
        def apiPost(path: String, body: String): JValue = {
          val response = server.postApi(path, body, "root", "root")
          assert(response.status == 200 || response.status == 201, s"POST $path: ${response.status} ${response.body}")
          parse(response.body)
        }
        def page(path: String): String = {
          val (status, body) = web.get(s"$base$path")
          assert(status == 200, s"GET $path: $status")
          body
        }
        def idIn(html: String, prefix: String): String = {
          val pattern = (java.util.regex.Pattern.quote(prefix) + "([0-9]+)\"").r
          pattern.findFirstMatchIn(html).map(_.group(1)).getOrElse(fail(s"no link $prefix<id> on the page"))
        }

        server.client("root", "root").createRepository("routes").autoInit(true).create()
        val repoApi = "/api/v3/repos/root/routes"
        val defaultBranch = (api(repoApi) \ "default_branch").extract[String]
        val defaultBranchSha = (api(s"$repoApi/branches/$defaultBranch") \ "commit" \ "sha").extract[String]

        // SSH key
        assert(web.post(s"$base/root/_ssh", "title" -> "key", "publicKey" -> sshPublicKey()) == 302)
        val sshKeyId = idIn(page("/root/_ssh"), "/root/_ssh/delete/")
        val sshKey = Route(
          "delete SSH key",
          s"$base/root/_ssh/delete/$sshKeyId",
          () => page("/root/_ssh").contains(s"/_ssh/delete/$sshKeyId\"")
        )

        // GPG key
        assert(web.post(s"$base/root/_gpg", "title" -> "key", "publicKey" -> gpgPublicKey()) == 302)
        val gpgKeyId = idIn(page("/root/_gpg"), "/root/_gpg/delete/")
        val gpgKey = Route(
          "delete GPG key",
          s"$base/root/_gpg/delete/$gpgKeyId",
          () => page("/root/_gpg").contains(s"/_gpg/delete/$gpgKeyId\"")
        )

        // personal access token
        assert(web.post(s"$base/root/_personalToken", "note" -> "token") == 302)
        val tokenId = idIn(page("/root/_application"), "/root/_personalToken/delete/")
        val token = Route(
          "delete personal access token",
          s"$base/root/_personalToken/delete/$tokenId",
          () => page("/root/_application").contains(s"/_personalToken/delete/$tokenId\"")
        )

        // account webhook
        val accountHookUrl = "http://example.com/account-hook"
        assert(
          web.post(s"$base/root/_hooks/new", "url" -> accountHookUrl, "events.push" -> "on", "ctype" -> "json") == 302
        )
        val accountHook = Route(
          "delete account webhook",
          s"$base/root/_hooks/delete?url=${StringUtil.urlEncode(accountHookUrl)}",
          () => page("/root/_hooks").contains(s"/_hooks/edit?url=${StringUtil.urlEncode(accountHookUrl)}\"")
        )

        // group
        server.createOrganization("routes-group", "root", "root")
        val group = Route(
          "delete group",
          s"$base/routes-group/_deletegroup",
          () => server.getApi("/api/v3/orgs/routes-group", "root", "root").status == 200
        )

        // repository webhook
        val repoHookUrl = "http://example.com/repository-hook"
        apiPost(
          s"$repoApi/hooks",
          s"""{"name":"web","config":{"url":"$repoHookUrl","content_type":"json"},"events":["push"]}"""
        )
        val repoHook = Route(
          "delete repository webhook",
          s"$base/root/routes/settings/hooks/delete?url=${StringUtil.urlEncode(repoHookUrl)}",
          () => (api(s"$repoApi/hooks") \ "config" \ "url").extractOpt[List[String]].exists(_.contains(repoHookUrl))
        )

        // deploy key
        assert(
          web.post(
            s"$base/root/routes/settings/deploykey",
            "title" -> "deploy",
            "publicKey" -> sshPublicKey(),
            "allowWrite" -> "false"
          ) == 302
        )
        val deployKeyId = idIn(page("/root/routes/settings/deploykey"), "/root/routes/settings/deploykey/delete/")
        val deployKey = Route(
          "delete deploy key",
          s"$base/root/routes/settings/deploykey/delete/$deployKeyId",
          () => page("/root/routes/settings/deploykey").contains(s"/settings/deploykey/delete/$deployKeyId\"")
        )

        // branch
        def branchExists(name: String) = server.getApi(s"$repoApi/branches/$name", "root", "root").status == 200
        apiPost(s"$repoApi/git/refs", s"""{"ref":"refs/heads/feature/x","sha":"$defaultBranchSha"}""")
        val branch = Route("delete branch", s"$base/root/routes/delete/feature/x", () => branchExists("feature/x"))

        // branch of a pull request
        apiPost(s"$repoApi/git/refs", s"""{"ref":"refs/heads/topic","sha":"$defaultBranchSha"}""")
        val topicContent = Base64.getEncoder.encodeToString("topic".getBytes)
        val topicFile = s"""{"message":"Add topic.txt","content":"$topicContent","branch":"topic"}"""
        val topicStatus = server.putApi(s"$repoApi/contents/topic.txt", topicFile, "root", "root").status
        assert(topicStatus == 200 || topicStatus == 201, s"add topic.txt: $topicStatus")
        val pull = s"""{"title":"Topic","head":"topic","base":"$defaultBranch","body":""}"""
        val pullNumber = (apiPost(s"$repoApi/pulls", pull) \ "number").extract[Int]
        val pullBranch = Route(
          "delete pull request branch",
          s"$base/root/routes/pull/$pullNumber/delete_branch",
          () => branchExists("topic")
        )

        // milestone
        val milestoneNumber = (apiPost(s"$repoApi/milestones", """{"title":"v1"}""") \ "number").extract[Int]
        val milestone = Route(
          "delete milestone",
          s"$base/root/routes/issues/milestones/$milestoneNumber/delete",
          () => api(s"$repoApi/milestones").children.nonEmpty
        )

        // wiki page, and two revisions of it for the revert routes
        def savePage(content: String, isNew: Boolean): Unit = {
          val status = web.post(
            s"$base/root/routes/wiki/${if (isNew) "_new" else "_edit"}",
            "pageName" -> "Page",
            "content" -> content,
            "message" -> s"Save $content",
            "currentPageName" -> (if (isNew) "" else "Page"),
            "id" -> (if (isNew) "" else wikiHead(server))
          )
          assert(status == 302, s"save wiki page: $status")
        }
        def pageContent(): String = web.get(s"$base/root/routes/wiki/Page")._2
        savePage("first text", isNew = true)
        val first = wikiHead(server)
        savePage("second text", isNew = false)
        val second = wikiHead(server)
        val pageRevert = Route(
          "revert wiki page",
          s"$base/root/routes/wiki/Page/_revert/$first...$second",
          () => pageContent().contains("second text")
        )
        val wikiDelete = Route(
          "delete wiki page",
          s"$base/root/routes/wiki/Page/_delete",
          () => web.get(s"$base/root/routes/wiki/Page")._1 == 200
        )

        Seq(sshKey, gpgKey, token, accountHook, group, repoHook, deployKey, branch, pullBranch, milestone, pageRevert)
          .foreach { route =>
            assert(route.exists(), s"${route.name}: not there before the test")
            web.get(route.url)
            assert(route.exists(), s"${route.name}: changed by GET ${route.url}")
            assert(web.post(route.url) == 302, s"${route.name}: POST ${route.url}")
            assert(!route.exists(), s"${route.name}: not changed by POST ${route.url}")
          }

        // reverting the whole wiki: revert the revert again
        val reverted = wikiHead(server)
        val wikiRevert = Route(
          "revert wiki changes",
          s"$base/root/routes/wiki/_revert/$second...$reverted",
          () => pageContent().contains("first text")
        )
        Seq(wikiRevert, wikiDelete).foreach { route =>
          assert(route.exists(), s"${route.name}: not there before the test")
          web.get(route.url)
          assert(route.exists(), s"${route.name}: changed by GET ${route.url}")
          assert(web.post(route.url) == 302, s"${route.name}: POST ${route.url}")
          assert(!route.exists(), s"${route.name}: not changed by POST ${route.url}")
        }
      }
    }
  }

  private def wikiHead(server: TestingGitBucketServer): String =
    Using.resource(Git.open(new File(server.getDirectory(), "repositories/root/routes.wiki.git"))) { git =>
      git.getRepository.resolve("HEAD").name
    }

  /** A new RSA public key in OpenSSH format. */
  private def sshPublicKey(): String = {
    val generator = KeyPairGenerator.getInstance("RSA")
    generator.initialize(2048)
    val key = generator.generateKeyPair().getPublic.asInstanceOf[RSAPublicKey]
    val bytes = new ByteArrayOutputStream()
    Using.resource(new DataOutputStream(bytes)) { out =>
      def write(data: Array[Byte]): Unit = { out.writeInt(data.length); out.write(data) }
      write("ssh-rsa".getBytes("US-ASCII"))
      write(key.getPublicExponent.toByteArray)
      write(key.getModulus.toByteArray)
    }
    s"ssh-rsa ${Base64.getEncoder.encodeToString(bytes.toByteArray)} test"
  }

  /** A new RSA public key, ASCII-armored as GPG exports it. */
  private def gpgPublicKey(): String = {
    val generator = KeyPairGenerator.getInstance("RSA")
    generator.initialize(2048)
    val rsaKeyPair = generator.generateKeyPair()
    val keyPair =
      new JcaPGPKeyPair(PublicKeyPacket.VERSION_4, PublicKeyAlgorithmTags.RSA_GENERAL, rsaKeyPair, new Date())
    val ring = new PGPPublicKeyRing(JArrays.asList(keyPair.getPublicKey))
    val bytes = new ByteArrayOutputStream()
    Using.resource(new ArmoredOutputStream(bytes))(ring.encode(_))
    bytes.toString("US-ASCII")
  }

  private class WebSession(httpClient: CloseableHttpClient) {
    def get(url: String): (Int, String) = execute(new HttpGet(url))

    def post(url: String, params: (String, String)*): Int = {
      val request = new HttpPost(url)
      request.setEntity(
        new UrlEncodedFormEntity(JArrays.asList(params.map { case (k, v) => new BasicNameValuePair(k, v) }*), "UTF-8")
      )
      execute(request)._1
    }

    private def execute(request: HttpRequestBase): (Int, String) = {
      request.setConfig(RequestConfig.custom().setRedirectsEnabled(false).build())
      Using.resource(httpClient.execute(request)) { response =>
        (response.getStatusLine.getStatusCode, EntityUtils.toString(response.getEntity, "UTF-8"))
      }
    }
  }

  private def withSession(server: TestingGitBucketServer)(f: WebSession => Unit): Unit =
    Using.resource(HttpClients.custom().setDefaultCookieStore(new BasicCookieStore()).build()) { httpClient =>
      val web = new WebSession(httpClient)
      assert(web.post(s"http://localhost:${server.port}/signin", "userName" -> "root", "password" -> "root") == 302)
      f(web)
    }
}
