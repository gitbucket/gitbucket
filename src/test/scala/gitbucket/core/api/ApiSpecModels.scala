package gitbucket.core.api

import java.util.{Calendar, Date, TimeZone}

import gitbucket.core.model._
import gitbucket.core.plugin.PluginInfo
import gitbucket.core.service.ProtectedBranchService.ProtectedBranchInfo
import gitbucket.core.service.RepositoryService.RepositoryInfo
import gitbucket.core.util.JGitUtil.{CommitInfo, DiffInfo, FileInfo, TagInfo}
import gitbucket.core.util.RepositoryName
import org.eclipse.jgit.diff.DiffEntry.ChangeType
import org.eclipse.jgit.lib.ObjectId

object ApiSpecModels {

  implicit val context = JsonFormat.Context("http://gitbucket.exmple.com", None)

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

  // Models

  val account = Account(
    userName = "octocat",
    fullName = "octocat",
    mailAddress = "octocat@example.com",
    password = "1234",
    isAdmin = false,
    url = None,
    registeredDate = date1,
    updatedDate = date1,
    lastLoginDate = Some(date1),
    image = None,
    isGroupAccount = false,
    isRemoved = false,
    description = None
  )

  val sha1 = "6dcb09b5b57875f334f61aebed695e2e4193db5e"
  val repo1Name = RepositoryName("octocat/Hello-World")

  val repository = Repository(
    userName = repo1Name.owner,
    repositoryName = repo1Name.name,
    isPrivate = false,
    description = Some("This your first repo!"),
    defaultBranch = "master",
    registeredDate = date1,
    updatedDate = date1,
    lastActivityDate = date1,
    originUserName = Some("octopus plus cat"),
    originRepositoryName = Some("Hello World"),
    parentUserName = Some("github"),
    parentRepositoryName = Some("Hello-World"),
    options = RepositoryOptions(
      issuesOption = "PUBLIC",
      externalIssuesUrl = Some("https://external.com/gitbucket"),
      wikiOption = "PUBLIC",
      externalWikiUrl = Some("https://external.com/gitbucket"),
      allowFork = true,
      mergeOptions = "merge-commit,squash,rebase",
      defaultMergeOption = "merge-commit"
    )
  )

  val repositoryInfo = RepositoryInfo(
    owner = repo1Name.owner,
    name = repo1Name.name,
    repository = repository,
    issueCount = 1,
    pullCount = 1,
    forkedCount = 1,
    branchList = Seq("master", "develop"),
    tags = Seq(
      TagInfo(name = "v1.0", time = date("2015-05-05T23:40:27Z"), id = "id1", message = "1.0 released"),
      TagInfo(name = "v2.0", time = date("2016-05-05T23:40:27Z"), id = "id2", message = "2.0 released")
    ),
    managers = Seq("myboss")
  )

  val label = Label(
    userName = repo1Name.owner,
    repositoryName = repo1Name.name,
    labelId = 10,
    labelName = "bug",
    color = "f29513"
  )

  val issue = Issue(
    userName = repo1Name.owner,
    repositoryName = repo1Name.name,
    issueId = 1347,
    openedUserName = "bear",
    milestoneId = None,
    priorityId = None,
    assignedUserName = None,
    title = "Found a bug",
    content = Some("I'm having a problem with this."),
    closed = false,
    registeredDate = date1,
    updatedDate = date1,
    isPullRequest = false
  )

  val issuePR = issue.copy(
    title = "new-feature",
    content = Some("Please pull these awesome changes"),
    closed = true,
    isPullRequest = true
  )

  val issueComment = IssueComment(
    userName = repo1Name.owner,
    repositoryName = repo1Name.name,
    issueId = issue.issueId,
    commentId = 1,
    action = "comment",
    commentedUserName = "bear",
    content = "Me too",
    registeredDate = date1,
    updatedDate = date1
  )

  val pullRequest = PullRequest(
    userName = repo1Name.owner,
    repositoryName = repo1Name.name,
    issueId = issuePR.issueId,
    branch = "master",
    requestUserName = "bear",
    requestRepositoryName = repo1Name.name,
    requestBranch = "new-topic",
    commitIdFrom = sha1,
    commitIdTo = sha1,
    isDraft = true
  )

  val commitComment = CommitComment(
    userName = repo1Name.owner,
    repositoryName = repo1Name.name,
    commitId = sha1,
    commentId = 29724692,
    commentedUserName = "bear",
    content = "Maybe you should use more emoji on this line.",
    fileName = Some("README.md"),
    oldLine = Some(1),
    newLine = Some(1),
    registeredDate = date("2015-05-05T23:40:27Z"),
    updatedDate = date("2015-05-05T23:40:27Z"),
    issueId = Some(issuePR.issueId),
    originalCommitId = sha1,
    originalOldLine = None,
    originalNewLine = None
  )

  val commitStatus = CommitStatus(
    commitStatusId = 1,
    userName = repo1Name.owner,
    repositoryName = repo1Name.name,
    commitId = sha1,
    context = "Default",
    state = CommitState.SUCCESS,
    targetUrl = Some("https://ci.example.com/1000/output"),
    description = Some("Build has completed successfully"),
    creator = account.userName,
    registeredDate = date1,
    updatedDate = date1
  )

  // APIs

  val apiUser = ApiUser(account)

  val apiRepository = ApiRepository(
    repository = repository,
    owner = apiUser,
    forkedCount = repositoryInfo.forkedCount,
    watchers = 0
  )

  val apiLabel = ApiLabel(
    label = label,
    repositoryName = repo1Name
  )

  val apiIssue = ApiIssue(
    issue = issue,
    repositoryName = repo1Name,
    user = apiUser,
    assignee = Some(apiUser),
    labels = List(apiLabel)
  )

  val apiNotAssignedIssue = ApiIssue(
    issue = issue,
    repositoryName = repo1Name,
    user = apiUser,
    assignee = None,
    labels = List(apiLabel)
  )

  val apiIssuePR = ApiIssue(
    issue = issuePR,
    repositoryName = repo1Name,
    user = apiUser,
    assignee = Some(apiUser),
    labels = List(apiLabel)
  )

  val apiComment = ApiComment(
    comment = issueComment,
    repositoryName = repo1Name,
    issueId = issueComment.issueId,
    user = apiUser,
    isPullRequest = false
  )

  val apiCommentPR = ApiComment(
    comment = issueComment,
    repositoryName = repo1Name,
    issueId = issueComment.issueId,
    user = apiUser,
    isPullRequest = true
  )

  val apiPullRequest = ApiPullRequest(
    issue = issuePR,
    pullRequest = pullRequest,
    headRepo = apiRepository,
    baseRepo = apiRepository,
    user = apiUser,
    labels = List(apiLabel),
    assignee = Some(apiUser),
    mergedComment = Some((issueComment, account))
  )

  // https://developer.github.com/v3/activity/events/types/#pullrequestreviewcommentevent
  val apiPullRequestReviewComment = ApiPullRequestReviewComment(
    comment = commitComment,
    commentedUser = apiUser,
    repositoryName = repo1Name,
    issueId = commitComment.issueId.get
  )

  val commitInfo = (id: String) =>
    CommitInfo(
      id = id,
      shortMessage = "short message",
      fullMessage = "full message",
      parents = List("1da452aa92d7db1bc093d266c80a69857718c406"),
      authorTime = date1,
      authorName = account.userName,
      authorEmailAddress = account.mailAddress,
      commitTime = date1,
      committerName = account.userName,
      committerEmailAddress = account.mailAddress,
      None,
      None
  )

  val apiCommitListItem = ApiCommitListItem(
    commit = commitInfo(sha1),
    repositoryName = repo1Name
  )

  val apiCommit = {
    val commit = commitInfo(sha1)
    ApiCommit(
      id = commit.id,
      message = commit.fullMessage,
      timestamp = commit.commitTime,
      added = Nil,
      removed = Nil,
      modified = List("README.md"),
      author = ApiPersonIdent.author(commit),
      committer = ApiPersonIdent.committer(commit)
    )(repo1Name)
  }

  val apiCommits = ApiCommits(
    repositoryName = repo1Name,
    commitInfo = commitInfo(sha1),
    diffs = Seq(
      DiffInfo(
        changeType = ChangeType.MODIFY,
        oldPath = "doc/README.md",
        newPath = "doc/README.md",
        oldContent = None,
        newContent = None,
        oldIsImage = false,
        newIsImage = false,
        oldObjectId = None,
        newObjectId = Some(sha1),
        oldMode = "old_mode",
        newMode = "new_mode",
        tooLarge = false,
        patch = Some("""@@ -1 +1,2 @@
                       |-body1
                       |\ No newline at end of file
                       |+body1
                       |+body2
                       |\ No newline at end of file""".stripMargin)
      )
    ),
    author = account,
    committer = account,
    commentCount = 2
  )

  val apiCommitStatus = ApiCommitStatus(
    status = commitStatus,
    creator = apiUser
  )

  val apiCombinedCommitStatus = ApiCombinedCommitStatus(
    sha = sha1,
    statuses = Iterable((commitStatus, account)),
    repository = apiRepository
  )

  val apiBranchProtection = ApiBranchProtection(
    info = ProtectedBranchInfo(
      owner = repo1Name.owner,
      repository = repo1Name.name,
      enabled = true,
      contexts = Seq("continuous-integration/travis-ci"),
      includeAdministrators = true
    )
  )

  val apiBranch = ApiBranch(
    name = "master",
    commit = ApiBranchCommit(sha1),
    protection = apiBranchProtection
  )(
    repositoryName = repo1Name
  )

  val apiBranchForList = ApiBranchForList(
    name = "master",
    commit = ApiBranchCommit(sha1)
  )

  val apiContents = ApiContents(
    fileInfo = FileInfo(
      id = ObjectId.fromString(sha1),
      isDirectory = false,
      name = "README.md",
      path = "doc/README.md",
      message = "message",
      commitId = sha1,
      time = date1,
      author = account.userName,
      mailAddress = account.mailAddress,
      linkUrl = None
    ),
    repositoryName = repo1Name,
    content = Some("README".getBytes("UTF-8"))
  )

  val apiEndPoint = ApiEndPoint()

  val apiError = ApiError(
    message = "A repository with this name already exists on this account",
    documentation_url = Some("https://developer.github.com/v3/repos/#create")
  )

  val apiGroup = ApiGroup(
    account.copy(
      isAdmin = true,
      isGroupAccount = true,
      description = Some("Admin group")
    )
  )

  val apiPlugin = ApiPlugin(
    plugin = PluginInfo(
      pluginId = "gist",
      pluginName = "Gist Plugin",
      pluginVersion = "4.16.0",
      gitbucketVersion = Some("4.30.1"),
      description = "Provides Gist feature on GitBucket.",
      pluginClass = null,
      pluginJar = new java.io.File("gitbucket-gist-plugin-gitbucket_4.30.0-SNAPSHOT-4.17.0.jar"),
      classLoader = null
    )
  )

  val apiPusher = ApiPusher(account)

  val apiRef = ApiRef(
    ref = "refs/heads/featureA",
    `object` = ApiObject(sha1)
  )

  val assetFileName = "010203040a0b0c0d"

  val apiReleaseAsset = ApiReleaseAsset(
    name = "release.zip",
    size = 100
  )(
    tag = "tag1",
    fileName = assetFileName,
    repositoryName = repo1Name
  )

  val apiRelease = ApiRelease(
    name = "release1",
    tag_name = "tag1",
    body = Some("content"),
    author = apiUser,
    assets = Seq(apiReleaseAsset)
  )

  // JSON String for APIs

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

  val jsonLabel =
    """{"name":"bug","color":"f29513","url":"http://gitbucket.exmple.com/api/v3/repos/octocat/Hello-World/labels/bug"}"""

  val jsonIssue = s"""{
       |"number":1347,
       |"title":"Found a bug",
       |"user":$jsonUser,
       |"assignee":$jsonUser,
       |"labels":[$jsonLabel],
       |"state":"open",
       |"created_at":"2011-04-14T16:00:49Z",
       |"updated_at":"2011-04-14T16:00:49Z",
       |"body":"I'm having a problem with this.",
       |"id":0,
       |"assignees":[$jsonUser],
       |"comments_url":"http://gitbucket.exmple.com/api/v3/repos/octocat/Hello-World/issues/1347/comments",
       |"html_url":"http://gitbucket.exmple.com/octocat/Hello-World/issues/1347"
       |}""".stripMargin

  val jsonNotAssignedIssue = s"""{
       |"number":1347,
       |"title":"Found a bug",
       |"user":$jsonUser,
       |"labels":[$jsonLabel],
       |"state":"open",
       |"created_at":"2011-04-14T16:00:49Z",
       |"updated_at":"2011-04-14T16:00:49Z",
       |"body":"I'm having a problem with this.",
       |"id":0,
       |"assignees":[],
       |"comments_url":"http://gitbucket.exmple.com/api/v3/repos/octocat/Hello-World/issues/1347/comments",
       |"html_url":"http://gitbucket.exmple.com/octocat/Hello-World/issues/1347"
       |}""".stripMargin

  val jsonIssuePR = s"""{
       |"number":1347,
       |"title":"new-feature",
       |"user":$jsonUser,
       |"assignee":$jsonUser,
       |"labels":[$jsonLabel],
       |"state":"closed",
       |"created_at":"2011-04-14T16:00:49Z",
       |"updated_at":"2011-04-14T16:00:49Z",
       |"body":"Please pull these awesome changes",
       |"id":0,
       |"assignees":[$jsonUser],
       |"comments_url":"http://gitbucket.exmple.com/api/v3/repos/octocat/Hello-World/issues/1347/comments",
       |"html_url":"http://gitbucket.exmple.com/octocat/Hello-World/pull/1347",
       |"pull_request":{
         |"url":"http://gitbucket.exmple.com/api/v3/repos/octocat/Hello-World/pulls/1347",
         |"html_url":"http://gitbucket.exmple.com/octocat/Hello-World/pull/1347"}
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
       |"labels":[$jsonLabel],
       |"assignee":$jsonUser,
       |"draft":true,
       |"id":0,
       |"html_url":"http://gitbucket.exmple.com/octocat/Hello-World/pull/1347",
       |"url":"http://gitbucket.exmple.com/api/v3/repos/octocat/Hello-World/pulls/1347",
       |"commits_url":"http://gitbucket.exmple.com/api/v3/repos/octocat/Hello-World/pulls/1347/commits",
       |"review_comments_url":"http://gitbucket.exmple.com/api/v3/repos/octocat/Hello-World/pulls/1347/comments",
       |"review_comment_url":"http://gitbucket.exmple.com/api/v3/repos/octocat/Hello-World/pulls/comments/{number}",
       |"comments_url":"http://gitbucket.exmple.com/api/v3/repos/octocat/Hello-World/issues/1347/comments",
       |"statuses_url":"http://gitbucket.exmple.com/api/v3/repos/octocat/Hello-World/statuses/6dcb09b5b57875f334f61aebed695e2e4193db5e"
       |}""".stripMargin

  val jsonPullRequestReviewComment = s"""{
       |"id":29724692,
       |"path":"README.md",
       |"commit_id":"6dcb09b5b57875f334f61aebed695e2e4193db5e",
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
         |"pull_request":{"href":"http://gitbucket.exmple.com/api/v3/repos/octocat/Hello-World/pulls/1347"}}
       |}""".stripMargin

  val jsonComment = s"""{
       |"id":1,
       |"user":$jsonUser,
       |"body":"Me too",
       |"created_at":"2011-04-14T16:00:49Z",
       |"updated_at":"2011-04-14T16:00:49Z",
       |"html_url":"http://gitbucket.exmple.com/octocat/Hello-World/issues/1347#comment-1"
       |}""".stripMargin

  val jsonCommentPR = s"""{
       |"id":1,
       |"user":$jsonUser,
       |"body":"Me too",
       |"created_at":"2011-04-14T16:00:49Z",
       |"updated_at":"2011-04-14T16:00:49Z",
       |"html_url":"http://gitbucket.exmple.com/octocat/Hello-World/pull/1347#comment-1"
       |}""".stripMargin

  val jsonCommitListItem = s"""{
       |"sha":"6dcb09b5b57875f334f61aebed695e2e4193db5e",
       |"commit":{
         |"message":"full message",
         |"author":{"name":"octocat","email":"octocat@example.com","date":"2011-04-14T16:00:49Z"},
         |"committer":{"name":"octocat","email":"octocat@example.com","date":"2011-04-14T16:00:49Z"},
         |"url":"http://gitbucket.exmple.com/api/v3/repos/octocat/Hello-World/git/commits/6dcb09b5b57875f334f61aebed695e2e4193db5e"
       |},
       |"parents":[{
         |"sha":"1da452aa92d7db1bc093d266c80a69857718c406",
         |"url":"http://gitbucket.exmple.com/api/v3/repos/octocat/Hello-World/commits/1da452aa92d7db1bc093d266c80a69857718c406"}],
       |"url":"http://gitbucket.exmple.com/api/v3/repos/octocat/Hello-World/commits/6dcb09b5b57875f334f61aebed695e2e4193db5e"
       |}""".stripMargin

  val jsonCommit = (id: String) => s"""{
       |"id":"$id",
       |"message":"full message",
       |"timestamp":"2011-04-14T16:00:49Z",
       |"added":[],
       |"removed":[],
       |"modified":["README.md"],
       |"author":{"name":"octocat","email":"octocat@example.com","date":"2011-04-14T16:00:49Z"},
       |"committer":{"name":"octocat","email":"octocat@example.com","date":"2011-04-14T16:00:49Z"},
       |"url":"http://gitbucket.exmple.com/api/v3/octocat/Hello-World/commits/$id",
       |"html_url":"http://gitbucket.exmple.com/octocat/Hello-World/commit/$id"
       |}""".stripMargin

  val jsonCommits = s"""{
       |"url":"http://gitbucket.exmple.com/api/v3/repos/octocat/Hello-World/commits/6dcb09b5b57875f334f61aebed695e2e4193db5e",
       |"sha":"6dcb09b5b57875f334f61aebed695e2e4193db5e",
       |"html_url":"http://gitbucket.exmple.comoctocat/Hello-World/commit/6dcb09b5b57875f334f61aebed695e2e4193db5e",
       |"comment_url":"http://gitbucket.exmple.com",
       |"commit":{
         |"url":"http://gitbucket.exmple.com/api/v3/repos/octocat/Hello-World/commits/6dcb09b5b57875f334f61aebed695e2e4193db5e",
         |"author":{"name":"octocat","email":"octocat@example.com","date":"2011-04-14T16:00:49Z"},
         |"committer":{"name":"octocat","email":"octocat@example.com","date":"2011-04-14T16:00:49Z"},
         |"message":"short message",
         |"comment_count":2,
         |"tree":{"url":"http://gitbucket.exmple.com/api/v3/repos/octocat/Hello-World/tree/6dcb09b5b57875f334f61aebed695e2e4193db5e","sha":"6dcb09b5b57875f334f61aebed695e2e4193db5e"}
       |},
       |"author":$jsonUser,
       |"committer":$jsonUser,
       |"parents":[{
         |"url":"http://gitbucket.exmple.com/api/v3/repos/octocat/Hello-World/tree/1da452aa92d7db1bc093d266c80a69857718c406",
         |"sha":"1da452aa92d7db1bc093d266c80a69857718c406"}],
       |"stats":{"additions":2,"deletions":1,"total":3},
       |"files":[{
         |"filename":"doc/README.md",
         |"additions":2,
         |"deletions":1,
         |"changes":3,
         |"status":"modified",
         |"raw_url":"http://gitbucket.exmple.com/octocat/Hello-World/raw/6dcb09b5b57875f334f61aebed695e2e4193db5e/doc/README.md",
         |"blob_url":"http://gitbucket.exmple.com/octocat/Hello-World/blob/6dcb09b5b57875f334f61aebed695e2e4193db5e/doc/README.md",
         |"patch":"@@ -1 +1,2 @@\\n-body1\\n\\\\ No newline at end of file\\n+body1\\n+body2\\n\\\\ No newline at end of file"}]
       |}""".stripMargin

  val jsonCommitStatus = s"""{
       |"created_at":"2011-04-14T16:00:49Z",
       |"updated_at":"2011-04-14T16:00:49Z",
       |"state":"success",
       |"target_url":"https://ci.example.com/1000/output",
       |"description":"Build has completed successfully",
       |"id":1,
       |"context":"Default",
       |"creator":$jsonUser,
       |"url":"http://gitbucket.exmple.com/api/v3/repos/octocat/Hello-World/commits/6dcb09b5b57875f334f61aebed695e2e4193db5e/statuses"
       |}""".stripMargin

  val jsonCombinedCommitStatus = s"""{
       |"state":"success",
       |"sha":"6dcb09b5b57875f334f61aebed695e2e4193db5e",
       |"total_count":1,
       |"statuses":[$jsonCommitStatus],
       |"repository":$jsonRepository,
       |"url":"http://gitbucket.exmple.com/api/v3/repos/octocat/Hello-World/commits/6dcb09b5b57875f334f61aebed695e2e4193db5e/status"
       |}""".stripMargin

  val jsonBranchProtection =
    """{
       |"enabled":true,
       |"required_status_checks":{"enforcement_level":"everyone","contexts":["continuous-integration/travis-ci"]}
       |}""".stripMargin

  val jsonBranch = s"""{
       |"name":"master",
       |"commit":{"sha":"6dcb09b5b57875f334f61aebed695e2e4193db5e"},
       |"protection":$jsonBranchProtection,
       |"_links":{
         |"self":"http://gitbucket.exmple.com/api/v3/repos/octocat/Hello-World/branches/master",
         |"html":"http://gitbucket.exmple.com/octocat/Hello-World/tree/master"}
       |}""".stripMargin

  val jsonBranchForList = """{"name":"master","commit":{"sha":"6dcb09b5b57875f334f61aebed695e2e4193db5e"}}"""

  val jsonContents =
    """{
       |"type":"file",
       |"name":"README.md",
       |"path":"doc/README.md",
       |"sha":"6dcb09b5b57875f334f61aebed695e2e4193db5e",
       |"content":"UkVBRE1F",
       |"encoding":"base64",
       |"download_url":"http://gitbucket.exmple.com/api/v3/repos/octocat/Hello-World/raw/6dcb09b5b57875f334f61aebed695e2e4193db5e/doc/README.md"
       |}""".stripMargin

  val jsonEndPoint = """{"rate_limit_url":"http://gitbucket.exmple.com/api/v3/rate_limit"}"""

  val jsonError = """{
       |"message":"A repository with this name already exists on this account",
       |"documentation_url":"https://developer.github.com/v3/repos/#create"
       |}""".stripMargin

  val jsonGroup = """{
       |"login":"octocat",
       |"description":"Admin group",
       |"created_at":"2011-04-14T16:00:49Z",
       |"id":0,
       |"url":"http://gitbucket.exmple.com/api/v3/orgs/octocat",
       |"html_url":"http://gitbucket.exmple.com/octocat",
       |"avatar_url":"http://gitbucket.exmple.com/octocat/_avatar"
       |}""".stripMargin

  val jsonPlugin = """{
       |"id":"gist",
       |"name":"Gist Plugin",
       |"version":"4.16.0",
       |"description":"Provides Gist feature on GitBucket.",
       |"jarFileName":"gitbucket-gist-plugin-gitbucket_4.30.0-SNAPSHOT-4.17.0.jar"
       |}""".stripMargin

  val jsonPusher = """{"name":"octocat","email":"octocat@example.com"}"""

  val jsonRef = """{"ref":"refs/heads/featureA","object":{"sha":"6dcb09b5b57875f334f61aebed695e2e4193db5e"}}"""

  val jsonReleaseAsset =
    s"""{
      |"name":"release.zip",
      |"size":100,
      |"label":"release.zip",
      |"file_id":"${assetFileName}",
      |"browser_download_url":"http://gitbucket.exmple.com/api/v3/repos/octocat/Hello-World/releases/tag1/assets/${assetFileName}"
      |}""".stripMargin

  val jsonRelease =
    s"""{
       |"name":"release1",
       |"tag_name":"tag1",
       |"body":"content",
       |"author":${jsonUser},
       |"assets":[${jsonReleaseAsset}]
       |}""".stripMargin
}
