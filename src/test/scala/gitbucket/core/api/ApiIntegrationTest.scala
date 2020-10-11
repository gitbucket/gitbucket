package gitbucket.core.api

import java.nio.charset.StandardCharsets
import java.util.Base64

import gitbucket.core.TestingGitBucketServer
import gitbucket.core.util.HttpClientUtil
import org.apache.commons.io.IOUtils
import org.apache.http.client.methods.{HttpGet, HttpPost}
import org.apache.http.entity.StringEntity
import org.scalatest.funsuite.AnyFunSuite

import scala.util.Using

import org.json4s._
import org.json4s.jackson.JsonMethods._

class ApiIntegrationTest extends AnyFunSuite {

  private val AuthHeaderValue =
    s"Basic ${new String(Base64.getEncoder.encode("root:root".getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8)}"

  test("API integration test") {
    Using.resource(new TestingGitBucketServer(19999)) { server =>
      server.start()

      HttpClientUtil.withHttpClient(None) { httpClient => // Create a repository
      {
        val request = new HttpPost("http://localhost:19999/api/v3/user/repos")
        request.addHeader("Authorization", AuthHeaderValue)
        request.addHeader("Content-Type", "application/json")
        request.setEntity(new StringEntity("""{
                |"name": "test",
                |"description": "test repository",
                |"private": false,
                |"auto_init": true
                |}""".stripMargin))
        val response = httpClient.execute(request)
        assert(response.getStatusLine.getStatusCode == 200)

        val json = parse(IOUtils.toString(response.getEntity.getContent, StandardCharsets.UTF_8))
        assert((json \ "name").values == "test")
        assert((json \ "description").values == "test repository")
        assert((json \ "watchers").values == 0)
        assert((json \ "forks").values == 0)
        assert((json \ "private").values == false)
        assert((json \ "default_branch").values == "master")
        assert((json \ "owner" \ "login").values == "root")
        assert((json \ "has_issues").values == true)
        assert((json \ "forks_count").values == 0)
        assert((json \ "watchers_count").values == 0)
        assert((json \ "url").values == "http://localhost:19999/api/v3/repos/root/test")
        assert((json \ "http_url").values == "http://localhost:19999/git/root/test.git")
        assert((json \ "clone_url").values == "http://localhost:19999/git/root/test.git")
        assert((json \ "html_url").values == "http://localhost:19999/root/test")
      }

      // List repositories
      {
        val request = new HttpGet("http://localhost:19999/api/v3/user/repos")
        request.addHeader("Authorization", AuthHeaderValue)
        val response = httpClient.execute(request)
        assert(response.getStatusLine.getStatusCode == 200)

        val json = parse(IOUtils.toString(response.getEntity.getContent, StandardCharsets.UTF_8)).asInstanceOf[JArray]
        assert(json.arr.length == 1)
        assert((json(0) \ "name").values == "test")
        assert((json(0) \ "full_name").values == "root/test")
        assert((json(0) \ "description").values == "test repository")
        assert((json(0) \ "watchers").values == 0)
        assert((json(0) \ "forks").values == 0)
        assert((json(0) \ "private").values == false)
        assert((json(0) \ "default_branch").values == "master")
        assert((json(0) \ "owner" \ "login").values == "root")
        assert((json(0) \ "has_issues").values == true)
        assert((json(0) \ "forks_count").values == 0)
        assert((json(0) \ "watchers_count").values == 0)
        assert((json(0) \ "url").values == "http://localhost:19999/api/v3/repos/root/test")
        assert((json(0) \ "http_url").values == "http://localhost:19999/git/root/test.git")
        assert((json(0) \ "clone_url").values == "http://localhost:19999/git/root/test.git")
        assert((json(0) \ "html_url").values == "http://localhost:19999/root/test")
      }
      }
    }
  }

}
