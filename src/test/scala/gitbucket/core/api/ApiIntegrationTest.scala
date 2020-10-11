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
import org.kohsuke.github.GitHub

class ApiIntegrationTest extends AnyFunSuite {

  private val AuthHeaderValue =
    s"Basic ${new String(Base64.getEncoder.encode("root:root".getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8)}"

  test("API integration test") {
    Using.resource(new TestingGitBucketServer(19999)) { server =>
      server.start()

      val github = GitHub.connectToEnterprise(s"http://localhost:${server.port}/api/v3", "root", "root")

      {
        val repository = github
          .createRepository("test")
          .description("test repository")
          .private_(false)
          .autoInit(true)
          .create()

        assert(repository.getName == "test")
        assert(repository.getDescription == "test repository")
        assert(repository.getDefaultBranch == "master")
        assert(repository.getWatchers == 0)
        assert(repository.getWatchersCount == 0)
        assert(repository.getForks == 0)
        assert(repository.getForksCount == 0)
        assert(repository.isPrivate == false)
        assert(repository.getOwner.getLogin == "root")
        assert(repository.hasIssues == true)
        assert(repository.getUrl.toString == s"http://localhost:${server.port}/api/v3/repos/root/test")
        assert(repository.getHttpTransportUrl == s"http://localhost:${server.port}/git/root/test.git")
        assert(repository.getHtmlUrl.toString == s"http://localhost:${server.port}/root/test")
      }
      {
        val repositories = github.getUser("root").listRepositories().toList
        assert(repositories.size() == 1)

        val repository = repositories.get(0)
        assert(repository.getName == "test")
        assert(repository.getDescription == "test repository")
        assert(repository.getDefaultBranch == "master")
        assert(repository.getWatchers == 0)
        assert(repository.getWatchersCount == 0)
        assert(repository.getForks == 0)
        assert(repository.getForksCount == 0)
        assert(repository.isPrivate == false)
        assert(repository.getOwner.getLogin == "root")
        assert(repository.hasIssues == true)
        assert(repository.getUrl.toString == s"http://localhost:${server.port}/api/v3/repos/root/test")
        assert(repository.getHttpTransportUrl == s"http://localhost:${server.port}/git/root/test.git")
        assert(repository.getHtmlUrl.toString == s"http://localhost:${server.port}/root/test")
      }
    }
  }

}
