package gitbucket.core.service

import gitbucket.core.api.JsonFormat
import gitbucket.core.service.WebHookService._
import org.json4s.jackson.JsonMethods.parse
import org.json4s._
import org.scalatest.{Assertion, FunSuite}

class WebHookJsonFormatSpec extends FunSuite {
  import gitbucket.core.api.ApiSpecModels._

  // TODO move to ApiSpecModels?
  val jsonUser = """{
                   |"login":"octocat",
                   |"email":"octocat@example.com",
                   |"type":"User",
                   |"site_admin":false,
                   |"created_at":"2011-04-14T16:00:49Z",
                   |"id":0,
                   |"url":"http://gitbucket.exmple.com/api/v3/users/octocat",
                   |"html_url":"http://gitbucket.exmple.com/octocat",
                   |"avatar_url":"http://gitbucket.exmple.com/octocat/_avatar"
                   |}""".stripMargin

  val jsonRepository = s"""{
                          |"name":"Hello-World",
                          |"full_name":"octocat/Hello-World",
                          |"description":"This your first repo!",
                          |"watchers":0,
                          |"forks":1,
                          |"private":false,
                          |"default_branch":"master",
                          |"owner":$jsonUser,
                          |"id":0,
                          |"forks_count":1,
                          |"watchers_count":0,
                          |"url":"http://gitbucket.exmple.com/api/v3/repos/octocat/Hello-World",
                          |"http_url":"http://gitbucket.exmple.com/git/octocat/Hello-World.git",
                          |"clone_url":"http://gitbucket.exmple.com/git/octocat/Hello-World.git",
                          |"html_url":"http://gitbucket.exmple.com/octocat/Hello-World"
                          |}""".stripMargin

  val jsonIssue = s"""{
       |"number":1347,
       |"title":"Found a bug",
       |"user":$jsonUser,
       |"labels":[{"name":"bug","color":"f29513","url":"http://gitbucket.exmple.com/api/v3/repos/octocat/Hello-World/labels/bug"}],
       |"state":"open",
       |"created_at":"2011-04-14T16:00:49Z",
       |"updated_at":"2011-04-14T16:00:49Z",
       |"body":"I'm having a problem with this.",
       |"id":0,
       |"comments_url":"http://gitbucket.exmple.com/api/v3/repos/octocat/Hello-World/issues/1347/comments",
       |"html_url":"http://gitbucket.exmple.com/octocat/Hello-World/issues/1347"
       |}""".stripMargin

  val jsonPullRequest = s"""{
       |"number":1347,
       |"state":"closed",
       |"updated_at":"2011-04-14T16:00:49Z",
       |"created_at":"2011-04-14T16:00:49Z",
       |"head":{"sha":"6dcb09b5b57875f334f61aebed695e2e4193db5e","ref":"new-topic","repo":$jsonRepository,"label":"new-topic","user":$jsonUser},
       |"base":{"sha":"6dcb09b5b57875f334f61aebed695e2e4193db5e","ref":"master","repo":$jsonRepository,"label":"master","user":$jsonUser},
       |"merged":true,
       |"merged_at":"2011-04-14T16:00:49Z",
       |"merged_by":$jsonUser,
       |"title":"new-feature",
       |"body":"Please pull these awesome changes",
       |"user":$jsonUser,
       |"labels":[{"name":"bug","color":"f29513","url":"http://gitbucket.exmple.com/api/v3/repos/octocat/Hello-World/labels/bug"}],
       |"assignee":$jsonUser,
       |"id":0,
       |"html_url":"http://gitbucket.exmple.com/octocat/Hello-World/pull/1347",
       |"url":"http://gitbucket.exmple.com/api/v3/repos/octocat/Hello-World/pulls/1347",
       |"commits_url":"http://gitbucket.exmple.com/api/v3/repos/octocat/Hello-World/pulls/1347/commits",
       |"review_comments_url":"http://gitbucket.exmple.com/api/v3/repos/octocat/Hello-World/pulls/1347/comments",
       |"review_comment_url":"http://gitbucket.exmple.com/api/v3/repos/octocat/Hello-World/pulls/comments/{number}",
       |"comments_url":"http://gitbucket.exmple.com/api/v3/repos/octocat/Hello-World/issues/1347/comments",
       |"statuses_url":"http://gitbucket.exmple.com/api/v3/repos/octocat/Hello-World/statuses/6dcb09b5b57875f334f61aebed695e2e4193db5e"
       |}""".stripMargin

  private def assert(payload: WebHookPayload, expected: String): Assertion = {
    val json = JsonFormat(payload)
    assert(json == expected.replaceAll("\n", ""))
  }

  test("WebHookCreatePayload") {
    val payload = WebHookCreatePayload(
      sender = account,
      repositoryInfo = repositoryInfo,
      repositoryOwner = account,
      ref = "v1.0",
      refType = "tag"
    )
    val expected = s"""{
        |"sender":$jsonUser,
        |"description":"This your first repo!",
        |"ref":"v1.0",
        |"ref_type":"tag",
        |"master_branch":"master",
        |"repository":$jsonRepository,
        |"pusher_type":"user"
        |}""".stripMargin
    assert(payload, expected)
  }

  test("WebHookPushPayload") {
    fail("TODO")
  }

  test("WebHookIssuesPayload") {
    val payload = WebHookIssuesPayload(
      action = "edited",
      number = 1347,
      repository = apiRepository,
      issue = apiIssue,
      sender = apiUser
    )
    val expected = s"""{
        |"action":"edited",
        |"number":1347,
        |"repository":$jsonRepository,
        |"issue":$jsonIssue,
        |"sender":$jsonUser
        |}""".stripMargin
    assert(payload, expected)
  }

  test("WebHookPullRequestPayload") {
    val payload = WebHookPullRequestPayload(
      action = "closed",
      issue = issuePR,
      issueUser = account,
      assignee = Some(account),
      pullRequest = pullRequest,
      headRepository = repositoryInfo,
      headOwner = account,
      baseRepository = repositoryInfo,
      baseOwner = account,
      labels = List(apiLabel),
      sender = account,
      mergedComment = Some((issueComment, account))
    )
    val expected = s"""{
        |"action":"closed",
        |"number":1347,
        |"repository":$jsonRepository,
        |"pull_request":$jsonPullRequest,
        |"sender":$jsonUser
        |}""".stripMargin
    assert(payload, expected)
  }

  test("WebHookIssueCommentPayload") {
    val payload = WebHookIssueCommentPayload(
      issue = issue,
      issueUser = account,
      comment = issueComment,
      commentUser = account,
      repository = repositoryInfo,
      repositoryUser = account,
      sender = account,
      labels = List(label)
    )
    val expected = s"""{
        |"action":"created",
        |"repository":$jsonRepository,
        |"issue":$jsonIssue,
        |"comment":{
          |"id":1,
          |"user":$jsonUser,
          |"body":"Me too",
          |"created_at":"2011-04-14T16:00:49Z",
          |"updated_at":"2011-04-14T16:00:49Z",
          |"html_url":"http://gitbucket.exmple.com/octocat/Hello-World/issues/1347#comment-1"},
        |"sender":$jsonUser
        |}""".stripMargin
    assert(payload, expected)
  }

  test("WebHookPullRequestReviewCommentPayload") {
    val payload = WebHookPullRequestReviewCommentPayload(
      action = "create",
      comment = commitComment,
      issue = issuePR,
      issueUser = account,
      assignee = Some(account),
      pullRequest = pullRequest,
      headRepository = repositoryInfo,
      headOwner = account,
      baseRepository = repositoryInfo,
      baseOwner = account,
      labels = List(apiLabel),
      sender = account,
      mergedComment = Some((issueComment, account))
    )
    val expected = s"""{
        |"action":"create",
        |"comment":{
          |"id":29724692,
          |"path":"README.md",
          |"commit_id":"0d1a26e67d8f5eaf1f6ba5c57fc3c7d91ac0fd1c",
          |"user":$jsonUser,
          |"body":"Maybe you should use more emoji on this line.",
          |"created_at":"2015-05-05T23:40:27Z",
          |"updated_at":"2015-05-05T23:40:27Z",
          |"url":"http://gitbucket.exmple.com/api/v3/repos/octocat/Hello-World/pulls/comments/29724692",
          |"html_url":"http://gitbucket.exmple.com/octocat/Hello-World/pull/1347#discussion_r29724692",
          |"pull_request_url":"http://gitbucket.exmple.com/api/v3/repos/octocat/Hello-World/pulls/1347",
          |"_links":{
            |"self":{"href":"http://gitbucket.exmple.com/api/v3/repos/octocat/Hello-World/pulls/comments/29724692"},
            |"html":{"href":"http://gitbucket.exmple.com/octocat/Hello-World/pull/1347#discussion_r29724692"},
            |"pull_request":{"href":"http://gitbucket.exmple.com/api/v3/repos/octocat/Hello-World/pulls/1347"}
          |}
        |},
        |"pull_request":$jsonPullRequest,
        |"repository":$jsonRepository,
        |"sender":$jsonUser
        |}""".stripMargin
    assert(payload, expected)
  }

  test("WebHookGollumPayload") {
    val payload = WebHookGollumPayload(
      pages = Seq(("edited", "Home", sha1)),
      repository = repositoryInfo,
      repositoryUser = account,
      sender = account
    )
    val expected = s"""{
        |"pages":[{"page_name":"Home","title":"Home","action":"edited","sha":"6dcb09b5b57875f334f61aebed695e2e4193db5e","html_url":"http://gitbucket.exmple.com/octocat/Hello-World/wiki/Home"}],
        |"repository":$jsonRepository,
        |"sender":$jsonUser
        |}""".stripMargin
    assert(payload, expected)
  }

}
