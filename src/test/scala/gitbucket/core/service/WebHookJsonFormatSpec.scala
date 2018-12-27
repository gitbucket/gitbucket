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

  private def assert(payload: WebHookPayload, expected: String): Assertion = {
    val json = JsonFormat(payload)
    assert(json == expected.replaceAll("\n", ""))
  }

  test("WebHookCreatePayload") {
    fail("TODO")
  }

  test("WebHookPushPayload") {
    fail("TODO")
  }

  test("WebHookIssuesPayload") {
    val payload = WebHookIssuesPayload(
      action = "edited",
      number = 1,
      repository = apiRepository,
      issue = apiIssue,
      sender = apiUser
    )
    val expected = s"""{
        |"action":"edited",
        |"number":1,
        |"repository":$jsonRepository,
        |"issue":{
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
          |"html_url":"http://gitbucket.exmple.com/octocat/Hello-World/issues/1347"},
        |"sender":$jsonUser
        |}""".stripMargin
    assert(payload, expected)
  }

  test("WebHookPullRequestPayload") {
    fail("TODO")
  }

  test("WebHookIssueCommentPayload") {
    fail("TODO")
  }

  test("WebHookPullRequestReviewCommentPayload") {
    fail("TODO")
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
