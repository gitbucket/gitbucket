package gitbucket.core.api

import org.json4s.jackson.JsonMethods.parse
import org.json4s._
import org.scalatest.FunSuite

class JsonFormatSpec extends FunSuite {
  import ApiSpecModels._

  val apiUserJson = """"""
  val repositoryJson = s""""""

  val apiCommitStatusJson = s"""{
    "created_at":"2011-04-14T16:00:49Z",
    "updated_at":"2011-04-14T16:00:49Z",
    "state":"success",
    "target_url":"https://ci.example.com/1000/output",
    "description":"Build has completed successfully",
    "id":1,
    "context":"Default",
    "creator":$apiUserJson,
    "url": "http://gitbucket.exmple.com/api/v3/repos/octocat/Hello-World/commits/6dcb09b5b57875f334f61aebed695e2e4193db5e/statuses"
  }"""

  val apiPushCommitJson = s"""{
      "id": "0d1a26e67d8f5eaf1f6ba5c57fc3c7d91ac0fd1c",
      // "distinct": true,
      "message": "Update README.md",
      "timestamp": "2011-04-14T16:00:49Z",
      "url": "http://gitbucket.exmple.com/baxterthehacker/public-repo/commit/0d1a26e67d8f5eaf1f6ba5c57fc3c7d91ac0fd1c",
      "author": {
        "name": "baxterthehacker",
        "email": "baxterthehacker@users.noreply.github.com",
        // "username": "baxterthehacker",
        "date" : "2011-04-14T16:00:49Z"
      },
      "committer": {
        "name": "baxterthehacker",
        "email": "baxterthehacker@users.noreply.github.com",
        // "username": "baxterthehacker",
        "date" : "2011-04-14T16:00:49Z"
      },
      "added": [

      ],
      "removed": [

      ],
      "modified": [
        "README.md"
      ]
    }"""

  val apiCombinedCommitStatusJson = s"""{
    "state": "success",
    "sha": "$sha1",
    "total_count": 2,
    "statuses": [ $apiCommitStatusJson ],
    "repository": $repositoryJson,
    "url": "${context.baseUrl}/api/v3/repos/octocat/Hello-World/commits/$sha1/status"
  }"""

  val apiLabelJson = s""""""

  val apiPluginJson = """{
   "id": "gist",
    "name": "Gist Plugin",
    "version": "4.16.0",
    "description": "Provides Gist feature on GitBucket.",
    "jarFileName": "gitbucket-gist-plugin-gitbucket_4.30.0-SNAPSHOT-4.17.0.jar"
  }"""

  val apiPusherJson = """{"name":"octocat","email":"octocat@example.com"}"""

  val apiEndPointJson = """{"rate_limit_url":"http://gitbucket.exmple.com/api/v3/rate_limit"}"""

  val apiErrorJson = """{
    "message": "A repository with this name already exists on this account",
    "documentation_url": "https://developer.github.com/v3/repos/#create"
  }"""

  val apiGroupJson = """{
    "login": "octocats",
    "description": "Admin group",
    "created_at": "2011-04-14T16:00:49Z",
    "id": 0,
    "url": "http://gitbucket.exmple.com/api/v3/orgs/octocats",
    "html_url": "http://gitbucket.exmple.com/octocats",
    "avatar_url": "http://gitbucket.exmple.com/octocats/_avatar"
  }""".stripMargin

  val apiRefJson = """{
    "ref": "refs/heads/featureA",
    "object": {"sha":"aa218f56b14c9653891f9e74264a383fa43fefbd"}
  }""".stripMargin

  val apiContentsJson =
    """{
    "type": "file",
    "name": "README.md",
    "path": "README.md",
    "sha": "3d21ec53a331a6f037a91c368710b99387d012c1",
    "content": "UkVBRE1F",
    "encoding": "base64",
    "download_url": "http://gitbucket.exmple.com/api/v3/repos/octocat/Hello-World/raw/3d21ec53a331a6f037a91c368710b99387d012c1/README.md"
  }"""

  val apiCommitsJson = s"""{
    "url": "http://gitbucket.exmple.com/api/v3/repos/octocat/Hello-World/commits/3d21ec53a331a6f037a91c368710b99387d012c1",
    "sha": "3d21ec53a331a6f037a91c368710b99387d012c1",
    "html_url": "http://gitbucket.exmple.comoctocat/Hello-World/commit/3d21ec53a331a6f037a91c368710b99387d012c1",
    "comment_url": "http://gitbucket.exmple.com",
    "commit": {
      "url": "http://gitbucket.exmple.com/api/v3/repos/octocat/Hello-World/commits/3d21ec53a331a6f037a91c368710b99387d012c1",
      "author": {
        "name": "octocat",
        "email": "octocat@example.com",
        "date": "2011-04-14T16:00:49Z"
      },
      "committer": {
        "name": "octocat",
        "email": "octocat@example.com",
        "date": "2011-04-14T16:00:49Z"
      },
      "message": "short message",
      "comment_count": 1,
      "tree": {
        "url": "http://gitbucket.exmple.com/api/v3/repos/octocat/Hello-World/tree/3d21ec53a331a6f037a91c368710b99387d012c1",
        "sha": "3d21ec53a331a6f037a91c368710b99387d012c1"
      }
    },
    "author": $apiUserJson,
    "committer": $apiUserJson,
    "parents": [
      {
        "url": "http://gitbucket.exmple.com/api/v3/repos/octocat/Hello-World/tree/1da452aa92d7db1bc093d266c80a69857718c406",
        "sha": "1da452aa92d7db1bc093d266c80a69857718c406"
      }
    ],
    "stats": {
      "additions": 2,
      "deletions": 1,
      "total": 3
    },
    "files": [
      {
        "filename": "README.md",
        "additions": 2,
        "deletions": 1,
        "changes": 3,
        "status": "modified",
        "raw_url": "http://gitbucket.exmple.com/octocat/Hello-World/raw/3d21ec53a331a6f037a91c368710b99387d012c1/README.md",
        "blob_url": "http://gitbucket.exmple.com/octocat/Hello-World/blob/3d21ec53a331a6f037a91c368710b99387d012c1/README.md",
        "patch": "@@ -1 +1,2 @@\\n-body1\\n\\\\ No newline at end of file\\n+body1\\n+body2\\n\\\\ No newline at end of file"
      }
    ]
  }"""

  @deprecated
  def assertJson(resultJson: String, expectJson: String) = {
    fail("TODO")
  }

  private def expected(json: String) = json.replaceAll("\n", "")

  test("apiUser") {
    assert(JsonFormat(apiUser) == expected(jsonUser))
  }
  test("apiRepository") {
    assert(JsonFormat(apiRepository) == expected(jsonRepository))
  }
  test("apiPushCommit") {
    assertJson(JsonFormat(apiCommit), apiPushCommitJson)
  }
  test("apiComment") {
    assert(JsonFormat(apiComment) == expected(jsonComment))
    assert(JsonFormat(apiCommentPR) == expected(jsonCommentPR))
  }
  test("apiCommitListItem") {
    assert(JsonFormat(apiCommitListItem) == expected(jsonCommitListItem))
  }
  test("apiCommitStatus") {
    assertJson(JsonFormat(apiCommitStatus), apiCommitStatusJson)
  }
  test("apiCombinedCommitStatus") {
    assertJson(JsonFormat(apiCombinedCommitStatus), apiCombinedCommitStatusJson)
  }
  test("apiLabel") {
    assert(JsonFormat(apiLabel) == expected(jsonLabel))
  }
  test("apiIssue") {
    assert(JsonFormat(apiIssue) == expected(jsonIssue))
    assert(JsonFormat(apiIssuePR) == expected(jsonIssuePR))
  }
  test("apiPullRequest") {
    assert(JsonFormat(apiPullRequest) == expected(jsonPullRequest))
  }
  test("apiPullRequestReviewComment") {
    assert(JsonFormat(apiPullRequestReviewComment) == expected(jsonPullRequestReviewComment))
  }
  test("apiBranchProtection") {
    assert(JsonFormat(apiBranchProtection) == expected(jsonBranchProtection))
  }
  test("apiBranch") {
    assert(JsonFormat(apiBranch) == expected(jsonBranch))
    assert(JsonFormat(apiBranchForList) == expected(jsonBranchForList))
  }
  test("apiCommits") {
    assertJson(JsonFormat(apiCommits), apiCommitsJson)
  }
  test("apiContents") {
    assertJson(JsonFormat(apiContents), apiContentsJson)
  }
  test("apiEndPoint") {
    assertJson(JsonFormat(apiEndPoint), apiEndPointJson)
  }
  test("apiError") {
    assertJson(JsonFormat(apiError), apiErrorJson)
  }
  test("apiGroup") {
    assertJson(JsonFormat(apiGroup), apiGroupJson)
  }
  test("apiPlugin") {
    assertJson(JsonFormat(apiPlugin), apiPluginJson)
  }
  test("apiPusher") {
    assertJson(JsonFormat(apiPusher), apiPusherJson)
  }
  test("apiRef") {
    assertJson(JsonFormat(apiRef), apiRefJson)
  }
}
