package gitbucket.core.api

import gitbucket.core.util.RepositoryName

import org.json4s.jackson.JsonMethods.parse
import org.json4s._
import org.scalatest.FunSuite

import java.util.{Calendar, TimeZone, Date}

class JsonFormatSpec extends FunSuite {
  val date1 = {
    val d = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    d.set(2011, 3, 14, 16, 0, 49)
    d.getTime
  }
  def date(date: String): Date = {
    val f = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
    f.setTimeZone(TimeZone.getTimeZone("UTC"))
    f.parse(date)
  }
  val sha1 = "6dcb09b5b57875f334f61aebed695e2e4193db5e"
  val repo1Name = RepositoryName("octocat/Hello-World")
  implicit val context = JsonFormat.Context("http://gitbucket.exmple.com", None)

  val apiUser =
    ApiUser(login = "octocat", email = "octocat@example.com", `type` = "User", site_admin = false, created_at = date1)
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

  val repository = ApiRepository(
    name = repo1Name.name,
    full_name = repo1Name.fullName,
    description = "This your first repo!",
    watchers = 0,
    forks = 0,
    `private` = false,
    default_branch = "master",
    owner = apiUser
  )(urlIsHtmlUrl = false)
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

  val apiCommitStatus = ApiCommitStatus(
    created_at = date1,
    updated_at = date1,
    state = "success",
    target_url = Some("https://ci.example.com/1000/output"),
    description = Some("Build has completed successfully"),
    id = 1,
    context = "Default",
    creator = apiUser
  )(sha1, repo1Name)
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

  val apiPushCommit = ApiCommit(
    id = "0d1a26e67d8f5eaf1f6ba5c57fc3c7d91ac0fd1c",
    message = "Update README.md",
    timestamp = date1,
    added = Nil,
    removed = Nil,
    modified = List("README.md"),
    author = ApiPersonIdent("baxterthehacker", "baxterthehacker@users.noreply.github.com", date1),
    committer = ApiPersonIdent("baxterthehacker", "baxterthehacker@users.noreply.github.com", date1)
  )(RepositoryName("baxterthehacker", "public-repo"), true)
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

  val apiComment = ApiComment(id = 1, user = apiUser, body = "Me too", created_at = date1, updated_at = date1)(
    RepositoryName("octocat", "Hello-World"),
    100,
    false
  )
  val apiCommentJson = s"""{
    "id": 1,
    "body": "Me too",
    "user": $apiUserJson,
    "html_url" : "${context.baseUrl}/octocat/Hello-World/issues/100#comment-1",
    "created_at": "2011-04-14T16:00:49Z",
    "updated_at": "2011-04-14T16:00:49Z"
  }"""

  val apiCommentPR = ApiComment(id = 1, user = apiUser, body = "Me too", created_at = date1, updated_at = date1)(
    RepositoryName("octocat", "Hello-World"),
    100,
    true
  )
  val apiCommentPRJson = s"""{
    "id": 1,
    "body": "Me too",
    "user": $apiUserJson,
    "html_url" : "${context.baseUrl}/octocat/Hello-World/pull/100#comment-1",
    "created_at": "2011-04-14T16:00:49Z",
    "updated_at": "2011-04-14T16:00:49Z"
  }"""

  val apiPersonIdent = ApiPersonIdent("Monalisa Octocat", "support@example.com", date1)
  val apiPersonIdentJson = """ {
    "name": "Monalisa Octocat",
    "email": "support@example.com",
    "date": "2011-04-14T16:00:49Z"
  }"""

  val apiCommitListItem = ApiCommitListItem(
    sha = sha1,
    commit = ApiCommitListItem.Commit(
      message = "Fix all the bugs",
      author = apiPersonIdent,
      committer = apiPersonIdent
    )(sha1, repo1Name),
    author = Some(apiUser),
    committer = Some(apiUser),
    parents = Seq(ApiCommitListItem.Parent("6dcb09b5b57875f334f61aebed695e2e4193db5e")(repo1Name))
  )(repo1Name)
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

  val apiCombinedCommitStatus = ApiCombinedCommitStatus(
    state = "success",
    sha = sha1,
    total_count = 2,
    statuses = List(apiCommitStatus),
    repository = repository
  )
  val apiCombinedCommitStatusJson = s"""{
    "state": "success",
    "sha": "$sha1",
    "total_count": 2,
    "statuses": [ $apiCommitStatusJson ],
    "repository": $repositoryJson,
    "url": "${context.baseUrl}/api/v3/repos/octocat/Hello-World/commits/$sha1/status"
  }"""

  val apiLabel = ApiLabel(name = "bug", color = "f29513")(RepositoryName("octocat", "Hello-World"))
  val apiLabelJson = s"""{
    "name": "bug",
    "color": "f29513",
    "url": "${context.baseUrl}/api/v3/repos/octocat/Hello-World/labels/bug"
  }"""

  val apiIssue = ApiIssue(
    number = 1347,
    title = "Found a bug",
    user = apiUser,
    labels = List(apiLabel),
    state = "open",
    body = "I'm having a problem with this.",
    created_at = date1,
    updated_at = date1
  )(RepositoryName("octocat", "Hello-World"), false)
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

  val apiIssuePR = ApiIssue(
    number = 1347,
    title = "Found a bug",
    user = apiUser,
    labels = List(apiLabel),
    state = "open",
    body = "I'm having a problem with this.",
    created_at = date1,
    updated_at = date1
  )(RepositoryName("octocat", "Hello-World"), true)
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

  val apiPullRequest = ApiPullRequest(
    number = 1347,
    state = "open",
    updated_at = date1,
    created_at = date1,
    head = ApiPullRequest.Commit(sha = sha1, ref = "new-topic", repo = repository)("octocat"),
    base = ApiPullRequest.Commit(sha = sha1, ref = "master", repo = repository)("octocat"),
    mergeable = None,
    merged = false,
    merged_at = Some(date1),
    merged_by = Some(apiUser),
    title = "new-feature",
    body = "Please pull these awesome changes",
    user = apiUser,
    labels = List(apiLabel),
    assignee = Some(apiUser)
  )

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

  // https://developer.github.com/v3/activity/events/types/#pullrequestreviewcommentevent
  val apiPullRequestReviewComment = ApiPullRequestReviewComment(
    id = 29724692,
    // "diff_hunk": "@@ -1 +1 @@\n-# public-repo",
    path = "README.md",
    // "position": 1,
    // "original_position": 1,
    commit_id = "0d1a26e67d8f5eaf1f6ba5c57fc3c7d91ac0fd1c",
    // "original_commit_id": "0d1a26e67d8f5eaf1f6ba5c57fc3c7d91ac0fd1c",
    user = apiUser,
    body = "Maybe you should use more emojji on this line.",
    created_at = date("2015-05-05T23:40:27Z"),
    updated_at = date("2015-05-05T23:40:27Z")
  )(RepositoryName("baxterthehacker/public-repo"), 1)
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
    "body": "Maybe you should use more emojji on this line.",
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

  val apiBranchProtection = ApiBranchProtection(
    true,
    Some(ApiBranchProtection.Status(ApiBranchProtection.Everyone, Seq("continuous-integration/travis-ci")))
  )
  val apiBranchProtectionJson = """{
    "enabled": true,
    "required_status_checks": {
      "enforcement_level": "everyone",
      "contexts": [
        "continuous-integration/travis-ci"
      ]
    }
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
    assertJson(JsonFormat(apiPushCommit), apiPushCommitJson)
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
}
