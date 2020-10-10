package gitbucket.core.api

import gitbucket.core.TestingGitBucketServer
import gitbucket.core.util.HttpClientUtil
import org.scalatest.funsuite.AnyFunSuite

import scala.util.Using

class ApiIntegrationTest extends AnyFunSuite {

  test("API integration test") {
    Using.resource(new TestingGitBucketServer(8080)) { server =>
      server.start()

      HttpClientUtil.withHttpClient(None) { httpClient =>
        }
    }
  }

}
