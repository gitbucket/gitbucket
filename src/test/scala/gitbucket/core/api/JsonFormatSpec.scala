package gitbucket.core.api

import org.json4s.jackson.JsonMethods.parse
import org.json4s._
import org.scalatest.FunSuite

class JsonFormatSpec extends FunSuite {
  import ApiSpecModels._

  val apiUserJson = """{
    "login":"octocat",
    "email":"octocat@example.com",
    "type":"User",
    "site_admin":false,
    "id": 0,
    "created_at":"2011-04-14T16:00:49Z",
    "url":"http://gitbucket.exmple.com/api/v3/users/octocat",
    "html_url":"http://gitbucket.exmple.com/octocat",
    "avatar_url":"http://gitbucket.exmple.com/octocat/_avatar"
  }"""

  val repositoryJson = s"""{
    "name" : "Hello-World",
    "full_name" : "octocat/Hello-World",
    "description" : "This your first repo!",
    "id": 0,
    "watchers" : 0,
    "forks" : 0,
    "private" : false,
    "default_branch" : "master",
    "owner" : $apiUserJson,
    "forks_count" : 0,
    "watchers_count" : 0,
    "url" : "${context.baseUrl}/api/v3/repos/octocat/Hello-World",
    "http_url" : "${context.baseUrl}/git/octocat/Hello-World.git",
    "clone_url" : "${context.baseUrl}/git/octocat/Hello-World.git",
    "html_url" : "${context.baseUrl}/octocat/Hello-World"
  }"""

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

  val apiCommentJson = s"""{
    "id": 1,
    "body": "Me too",
    "user": $apiUserJson,
    "html_url" : "${context.baseUrl}/octocat/Hello-World/issues/100#comment-1",
    "created_at": "2011-04-14T16:00:49Z",
    "updated_at": "2011-04-14T16:00:49Z"
  }"""

  val apiCommentPRJson = s"""{
    "id": 1,
    "body": "Me too",
    "user": $apiUserJson,
    "html_url" : "${context.baseUrl}/octocat/Hello-World/pull/100#comment-1",
    "created_at": "2011-04-14T16:00:49Z",
    "updated_at": "2011-04-14T16:00:49Z"
  }"""

  val apiPersonIdentJson = """ {
    "name": "Monalisa Octocat",
    "email": "support@example.com",
    "date": "2011-04-14T16:00:49Z"
  }"""

  val apiCommitListItemJson = s"""{
    "url": "${context.baseUrl}/api/v3/repos/octocat/Hello-World/commits/6dcb09b5b57875f334f61aebed695e2e4193db5e",
    "sha": "6dcb09b5b57875f334f61aebed695e2e4193db5e",
    "commit": {
      "url": "${context.baseUrl}/api/v3/repos/octocat/Hello-World/git/commits/6dcb09b5b57875f334f61aebed695e2e4193db5e",
      "author": $apiPersonIdentJson,
      "committer": $apiPersonIdentJson,
      "message": "Fix all the bugs"
    },
    "author": $apiUserJson,
    "committer": $apiUserJson,
    "parents": [
      {
        "url": "${context.baseUrl}/api/v3/repos/octocat/Hello-World/commits/6dcb09b5b57875f334f61aebed695e2e4193db5e",
        "sha": "6dcb09b5b57875f334f61aebed695e2e4193db5e"
      }
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

  val apiLabelJson = s"""{
    "name": "bug",
    "color": "f29513",
    "url": "${context.baseUrl}/api/v3/repos/octocat/Hello-World/labels/bug"
  }"""

  val apiIssueJson = s"""{
    "number": 1347,
    "state": "open",
    "title": "Found a bug",
    "body": "I'm having a problem with this.",
    "user": $apiUserJson,
    "id": 0,
    "labels": [$apiLabelJson],
    "comments_url": "${context.baseUrl}/api/v3/repos/octocat/Hello-World/issues/1347/comments",
    "html_url": "${context.baseUrl}/octocat/Hello-World/issues/1347",
    "created_at": "2011-04-14T16:00:49Z",
    "updated_at": "2011-04-14T16:00:49Z"
  }"""

  val apiIssuePRJson = s"""{
    "number": 1347,
    "state": "open",
    "title": "Found a bug",
    "body": "I'm having a problem with this.",
    "user": $apiUserJson,
    "id": 0,
    "labels": [$apiLabelJson],
    "comments_url": "${context.baseUrl}/api/v3/repos/octocat/Hello-World/issues/1347/comments",
    "html_url": "${context.baseUrl}/octocat/Hello-World/pull/1347",
    "pull_request": {
      "url": "${context.baseUrl}/api/v3/repos/octocat/Hello-World/pulls/1347",
      "html_url": "${context.baseUrl}/octocat/Hello-World/pull/1347"
      // "diff_url": "${context.baseUrl}/octocat/Hello-World/pull/1347.diff",
      // "patch_url": "${context.baseUrl}/octocat/Hello-World/pull/1347.patch"
    },
    "created_at": "2011-04-14T16:00:49Z",
    "updated_at": "2011-04-14T16:00:49Z"
  }"""

  val apiPullRequestJson = s"""{
    "number": 1347,
    "state" : "open",
    "id": 0,
    "updated_at": "2011-04-14T16:00:49Z",
    "created_at": "2011-04-14T16:00:49Z",
  //  "closed_at": "2011-04-14T16:00:49Z",
    "head": {
      "sha": "6dcb09b5b57875f334f61aebed695e2e4193db5e",
      "ref": "new-topic",
      "repo": $repositoryJson,
      "label": "new-topic",
      "user": $apiUserJson
    },
    "base": {
      "sha": "6dcb09b5b57875f334f61aebed695e2e4193db5e",
      "ref": "master",
      "repo": $repositoryJson,
      "label": "master",
      "user": $apiUserJson
    },
  //  "merge_commit_sha": "e5bd3914e2e596debea16f433f57875b5b90bcd6",
  //  "mergeable": true,
    "merged": false,
    "merged_at": "2011-04-14T16:00:49Z",
    "merged_by": $apiUserJson,
    "title": "new-feature",
    "body": "Please pull these awesome changes",
    "user": $apiUserJson,
    "assignee": $apiUserJson,
    "labels": [$apiLabelJson],
    "html_url": "${context.baseUrl}/octocat/Hello-World/pull/1347",
    "url": "${context.baseUrl}/api/v3/repos/octocat/Hello-World/pulls/1347",
    "commits_url": "${context.baseUrl}/api/v3/repos/octocat/Hello-World/pulls/1347/commits",
    "review_comments_url": "${context.baseUrl}/api/v3/repos/octocat/Hello-World/pulls/1347/comments",
    "review_comment_url": "${context.baseUrl}/api/v3/repos/octocat/Hello-World/pulls/comments/{number}",
    "comments_url": "${context.baseUrl}/api/v3/repos/octocat/Hello-World/issues/1347/comments",
    "statuses_url": "${context.baseUrl}/api/v3/repos/octocat/Hello-World/statuses/6dcb09b5b57875f334f61aebed695e2e4193db5e"
  //  "diff_url": "${context.baseUrl}/octocat/Hello-World/pull/1347.diff",
  //  "patch_url": "${context.baseUrl}/octocat/Hello-World/pull/1347.patch",
  //  "issue_url": "${context.baseUrl}/api/v3/repos/octocat/Hello-World/issues/1347",
  //  "state": "open",
  //  "comments": 10,
  //  "commits": 3,
  //  "additions": 100,
  //  "deletions": 3,
  //  "changed_files": 5
    }"""

  val apiPullRequestReviewCommentJson = s"""{
    "url": "http://gitbucket.exmple.com/api/v3/repos/baxterthehacker/public-repo/pulls/comments/29724692",
    "id": 29724692,
    // "diff_hunk": "@@ -1 +1 @@\\n-# public-repo",
    "path": "README.md",
    // "position": 1,
    // "original_position": 1,
    "commit_id": "0d1a26e67d8f5eaf1f6ba5c57fc3c7d91ac0fd1c",
    // "original_commit_id": "0d1a26e67d8f5eaf1f6ba5c57fc3c7d91ac0fd1c",
    "user": $apiUserJson,
    "body": "Maybe you should use more emoji on this line.",
    "created_at": "2015-05-05T23:40:27Z",
    "updated_at": "2015-05-05T23:40:27Z",
    "html_url": "http://gitbucket.exmple.com/baxterthehacker/public-repo/pull/1#discussion_r29724692",
    "pull_request_url": "http://gitbucket.exmple.com/api/v3/repos/baxterthehacker/public-repo/pulls/1",
    "_links": {
      "self": {
        "href": "http://gitbucket.exmple.com/api/v3/repos/baxterthehacker/public-repo/pulls/comments/29724692"
      },
      "html": {
        "href": "http://gitbucket.exmple.com/baxterthehacker/public-repo/pull/1#discussion_r29724692"
      },
      "pull_request": {
        "href": "http://gitbucket.exmple.com/api/v3/repos/baxterthehacker/public-repo/pulls/1"
      }
    }
  }"""

  val apiBranchProtectionJson = """{
    "enabled": true,
    "required_status_checks": {
      "enforcement_level": "everyone",
      "contexts": [
        "continuous-integration/travis-ci"
      ]
    }
  }"""

  val apiBranchJson = s"""{
    "name": "master",
    "commit": {"sha": "468cab6982b37db5eb167568210ec188673fb653"},
    "protection": $apiBranchProtectionJson,
    "_links": {
      "self": "http://gitbucket.exmple.com/api/v3/repos/octocat/Hello-World/branches/master",
      "html": "http://gitbucket.exmple.com/octocat/Hello-World/tree/master"
    }
  }"""

  val apiBranchForListJson = """{"name": "master", "commit": {"sha": "468cab6982b37db5eb167568210ec188673fb653"}}"""

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

  def assertJson(resultJson: String, expectJson: String) = {
    import java.util.regex.Pattern
    val json2 = Pattern.compile("""^\s*//.*$""", Pattern.MULTILINE).matcher(expectJson).replaceAll("")
    val js2 = try {
      parse(json2)
    } catch {
      case e: com.fasterxml.jackson.core.JsonParseException => {
        val p = java.lang.Math.max(e.getLocation.getCharOffset() - 10, 0).toInt
        val message = json2.substring(p, java.lang.Math.min(p + 100, json2.length))
        throw new com.fasterxml.jackson.core.JsonParseException(e.getProcessor, message + e.getMessage)
      }
    }
    val js1 = parse(resultJson)
    assert(js1 === js2)
  }

  test("apiUser") {
    assertJson(JsonFormat(apiUser), apiUserJson)
  }
  test("repository") {
    assertJson(JsonFormat(repository), repositoryJson)
  }
  test("apiPushCommit") {
    assertJson(JsonFormat(apiCommit), apiPushCommitJson)
  }
  test("apiComment") {
    assertJson(JsonFormat(apiComment), apiCommentJson)
    assertJson(JsonFormat(apiCommentPR), apiCommentPRJson)
  }
  test("apiCommitListItem") {
    assertJson(JsonFormat(apiCommitListItem), apiCommitListItemJson)
  }
  test("apiCommitStatus") {
    assertJson(JsonFormat(apiCommitStatus), apiCommitStatusJson)
  }
  test("apiCombinedCommitStatus") {
    assertJson(JsonFormat(apiCombinedCommitStatus), apiCombinedCommitStatusJson)
  }
  test("apiLabel") {
    assertJson(JsonFormat(apiLabel), apiLabelJson)
  }
  test("apiIssue") {
    assertJson(JsonFormat(apiIssue), apiIssueJson)
    assertJson(JsonFormat(apiIssuePR), apiIssuePRJson)
  }
  test("apiPullRequest") {
    assertJson(JsonFormat(apiPullRequest), apiPullRequestJson)
  }
  test("apiPullRequestReviewComment") {
    assertJson(JsonFormat(apiPullRequestReviewComment), apiPullRequestReviewCommentJson)
  }
  test("apiBranchProtection") {
    assertJson(JsonFormat(apiBranchProtection), apiBranchProtectionJson)
  }
  test("apiBranch") {
    assertJson(JsonFormat(apiBranch), apiBranchJson)
    assertJson(JsonFormat(apiBranchForList), apiBranchForListJson)
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
