package gitbucket.core.service

import gitbucket.core.api.JsonFormat
import gitbucket.core.service.WebHookService._
import org.scalatest.Assertion
import org.scalatest.funsuite.AnyFunSuite

class WebHookJsonFormatSpec extends AnyFunSuite {
  import gitbucket.core.api.ApiSpecModels._

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
    import gitbucket.core.util.GitSpecUtil._
    import org.eclipse.jgit.lib.{Constants, ObjectId}

    withTestRepository { git =>
      createFile(git, Constants.HEAD, "README.md", "body1", message = "initial")
      createFile(git, Constants.HEAD, "README.md", "body1\nbody2", message = "modified")

      val branchId = git.getRepository.resolve("master")

      val payload = WebHookPushPayload(
        git = git,
        sender = account,
        refName = "refs/heads/master",
        repositoryInfo = repositoryInfo,
        commits = List(commitInfo(branchId.name)),
        repositoryOwner = account,
        newId = ObjectId.fromString(sha1),
        oldId = ObjectId.fromString(sha1)
      )
      val expected = s"""{
          |"pusher":{"name":"octocat","email":"octocat@example.com"},
          |"sender":$jsonUser,
          |"ref":"refs/heads/master",
          |"before":"6dcb09b5b57875f334f61aebed695e2e4193db5e",
          |"after":"6dcb09b5b57875f334f61aebed695e2e4193db5e",
          |"commits":[${jsonCommit(branchId.name)}],
          |"repository":$jsonRepository,
          |"compare":"http://gitbucket.exmple.com/octocat/Hello-World/commit/6dcb09b5b57875f334f61aebed695e2e4193db5e",
          |"head_commit":${jsonCommit(branchId.name)}
          |}""".stripMargin
      assert(payload, expected)
    }
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
      assignedUser = Some(account),
      sender = account,
      labels = List(label)
    )
    val expected = s"""{
        |"action":"created",
        |"repository":$jsonRepository,
        |"issue":$jsonIssue,
        |"comment":$jsonComment,
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
        |"comment":$jsonPullRequestReviewComment,
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
