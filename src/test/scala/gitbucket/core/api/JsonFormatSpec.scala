package gitbucket.core.api

import gitbucket.core.util.RepositoryName

import org.specs2.mutable.Specification
import org.json4s.jackson.JsonMethods.{pretty, parse}
import org.json4s._
import org.specs2.matcher._

import java.util.{Calendar, TimeZone}



class JsonFormatSpec extends Specification {
  val date1 = {
    val d = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    d.set(2011,3,14,16,0,49)
    d.getTime
  }
  val sha1 = "6dcb09b5b57875f334f61aebed695e2e4193db5e"
  val repo1Name = RepositoryName("octocat/Hello-World")
  implicit val context = JsonFormat.Context("http://gitbucket.exmple.com")

  val apiUser = ApiUser(
    login= "octocat",
    email= "octocat@example.com",
    `type`=  "User",
    site_admin= false,
    created_at= date1)
  val apiUserJson = """{
    "login":"octocat",
    "email":"octocat@example.com",
    "type":"User",
    "site_admin":false,
    "created_at":"2011-04-14T16:00:49Z",
    "url":"http://gitbucket.exmple.com/api/v3/users/octocat",
    "html_url":"http://gitbucket.exmple.com/octocat"
  }"""

  val repository = ApiRepository(
    name = repo1Name.name,
    full_name = repo1Name.fullName,
    description = "This your first repo!",
    watchers = 0,
    forks = 0,
    `private` = false,
    default_branch = "master",
    owner = apiUser)
  val repositoryJson = s"""{
    "name" : "Hello-World",
    "full_name" : "octocat/Hello-World",
    "description" : "This your first repo!",
    "watchers" : 0,
    "forks" : 0,
    "private" : false,
    "default_branch" : "master",
    "owner" : $apiUserJson,
    "forks_count" : 0,
    "watchers_coun" : 0,
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

  val apiComment = ApiComment(
    id =1,
    user = apiUser,
    body= "Me too",
    created_at= date1,
    updated_at= date1)(RepositoryName("octocat","Hello-World"), 100)
  val apiCommentJson = s"""{
    "id": 1,
    "body": "Me too",
    "user": $apiUserJson,
    "html_url" : "${context.baseUrl}/octocat/Hello-World/issues/100#comment-1",
    "created_at": "2011-04-14T16:00:49Z",
    "updated_at": "2011-04-14T16:00:49Z"
  }"""

  val apiPersonIdent    = ApiPersonIdent("Monalisa Octocat","support@example.com",date1)
  val apiPersonIdentJson = """ {
    "name": "Monalisa Octocat",
    "email": "support@example.com",
    "date": "2011-04-14T16:00:49Z"
  }"""

  val apiCommitListItem = ApiCommitListItem(
    sha = sha1,
    commit = ApiCommitListItem.Commit(
      message   = "Fix all the bugs",
      author    = apiPersonIdent,
      committer = apiPersonIdent
      )(sha1, repo1Name),
    author = Some(apiUser),
    committer= Some(apiUser),
    parents= Seq(ApiCommitListItem.Parent("6dcb09b5b57875f334f61aebed695e2e4193db5e")(repo1Name)))(repo1Name)
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
    repository = repository)
  val apiCombinedCommitStatusJson = s"""{
    "state": "success",
    "sha": "$sha1",
    "total_count": 2,
    "statuses": [ $apiCommitStatusJson ],
    "repository": $repositoryJson,
    "url": "${context.baseUrl}/api/v3/repos/octocat/Hello-World/commits/$sha1/status"
  }"""

  val apiIssue = ApiIssue(
      number = 1347,
      title  = "Found a bug",
      user   = apiUser,
      state  = "open",
      body   = "I'm having a problem with this.",
      created_at = date1,
      updated_at = date1)(RepositoryName("octocat","Hello-World"))
  val apiIssueJson = s"""{
    "number": 1347,
    "state": "open",
    "title": "Found a bug",
    "body": "I'm having a problem with this.",
    "user": $apiUserJson,
    "comments_url": "${context.baseUrl}/api/v3/repos/octocat/Hello-World/issues/1347/comments",
    "html_url": "${context.baseUrl}/octocat/Hello-World/issues/1347",
    "created_at": "2011-04-14T16:00:49Z",
    "updated_at": "2011-04-14T16:00:49Z"
  }"""

  val apiPullRequest = ApiPullRequest(
      number     = 1347,
      updated_at = date1,
      created_at = date1,
      head       = ApiPullRequest.Commit(
                     sha  = sha1,
                     ref  = "new-topic",
                     repo = repository)("octocat"),
      base       = ApiPullRequest.Commit(
                     sha  = sha1,
                     ref  = "master",
                     repo = repository)("octocat"),
      mergeable  = None,
      title      = "new-feature",
      body       = "Please pull these awesome changes",
      user       = apiUser
    )
  val apiPullRequestJson = s"""{
    "url": "${context.baseUrl}/api/v3/repos/octocat/Hello-World/pulls/1347",
    "html_url": "${context.baseUrl}/octocat/Hello-World/pull/1347",
  //  "diff_url": "${context.baseUrl}/octocat/Hello-World/pull/1347.diff",
  //  "patch_url": "${context.baseUrl}/octocat/Hello-World/pull/1347.patch",
  //  "issue_url": "${context.baseUrl}/api/v3/repos/octocat/Hello-World/issues/1347",
    "commits_url": "${context.baseUrl}/api/v3/repos/octocat/Hello-World/pulls/1347/commits",
    "review_comments_url": "${context.baseUrl}/api/v3/repos/octocat/Hello-World/pulls/1347/comments",
    "review_comment_url": "${context.baseUrl}/api/v3/repos/octocat/Hello-World/pulls/comments/{number}",
    "comments_url": "${context.baseUrl}/api/v3/repos/octocat/Hello-World/issues/1347/comments",
    "statuses_url": "${context.baseUrl}/api/v3/repos/octocat/Hello-World/statuses/6dcb09b5b57875f334f61aebed695e2e4193db5e",
    "number": 1347,
  //  "state": "open",
    "title": "new-feature",
    "body": "Please pull these awesome changes",
    "created_at": "2011-04-14T16:00:49Z",
    "updated_at": "2011-04-14T16:00:49Z",
  //  "closed_at": "2011-04-14T16:00:49Z",
  //  "merged_at": "2011-04-14T16:00:49Z",
    "head": {
      "label": "new-topic",
      "ref": "new-topic",
      "sha": "6dcb09b5b57875f334f61aebed695e2e4193db5e",
      "user": $apiUserJson,
      "repo": $repositoryJson
    },
    "base": {
      "label": "master",
      "ref": "master",
      "sha": "6dcb09b5b57875f334f61aebed695e2e4193db5e",
      "user": $apiUserJson,
      "repo": $repositoryJson
    },
    "user": $apiUserJson
  //  "merge_commit_sha": "e5bd3914e2e596debea16f433f57875b5b90bcd6",
  //  "merged": false,
  //  "mergeable": true,
  //  "merged_by": $$apiUserJson,
  //  "comments": 10,
  //  "commits": 3,
  //  "additions": 100,
  //  "deletions": 3,
  //  "changed_files": 5
    }"""
  def beFormatted(json2Arg:String) = new Matcher[String] {
    def apply[S <: String](e: Expectable[S]) = {
      import java.util.regex.Pattern
      val json2 = Pattern.compile("""^\s*//.*$""", Pattern.MULTILINE).matcher(json2Arg).replaceAll("")
      val js2 = try{
        parse(json2)
      }catch{
        case e:com.fasterxml.jackson.core.JsonParseException => {
          val p = java.lang.Math.max(e.getLocation.getCharOffset()-10,0).toInt
          val message = json2.substring(p,java.lang.Math.min(p+100,json2.length))
          throw new com.fasterxml.jackson.core.JsonParseException(message + e.getMessage , e.getLocation)
        }
      }
      val js1 = parse(e.value)
      result(js1 == js2,
        "expected",
        {
            val diff = js2 diff js1
            s"${pretty(js1)} is not ${pretty(js2)} \n\n ${pretty(Extraction.decompose(diff)(org.json4s.DefaultFormats))}"
        },
        e)
    }
  }
  "JsonFormat" should {
    "apiUser" in {
        JsonFormat(apiUser) must beFormatted(apiUserJson)
    }
    "repository" in {
        JsonFormat(repository) must beFormatted(repositoryJson)
    }
    "apiComment" in {
        JsonFormat(apiComment) must beFormatted(apiCommentJson)
    }
    "apiCommitListItem" in {
        JsonFormat(apiCommitListItem) must beFormatted(apiCommitListItemJson)
    }
    "apiCommitStatus" in {
      JsonFormat(apiCommitStatus) must beFormatted(apiCommitStatusJson)
    }
    "apiCombinedCommitStatus" in {
      JsonFormat(apiCombinedCommitStatus) must beFormatted(apiCombinedCommitStatusJson)
    }
    "apiIssue" in {
      JsonFormat(apiIssue) must beFormatted(apiIssueJson)
    }
    "apiPullRequest" in {
      JsonFormat(apiPullRequest) must beFormatted(apiPullRequestJson)
    }
  }
}
