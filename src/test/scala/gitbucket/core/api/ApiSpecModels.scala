package gitbucket.core.api

import java.util.{Base64, Calendar, Date, TimeZone}

import gitbucket.core.model.Account
import gitbucket.core.util.JGitUtil.{CommitInfo, DiffInfo}
import gitbucket.core.util.RepositoryName
import org.eclipse.jgit.diff.DiffEntry.ChangeType

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

  val apiUser = ApiUser(account)

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

  val apiCommit = ApiCommit(
    id = "0d1a26e67d8f5eaf1f6ba5c57fc3c7d91ac0fd1c",
    message = "Update README.md",
    timestamp = date1,
    added = Nil,
    removed = Nil,
    modified = List("README.md"),
    author = ApiPersonIdent("baxterthehacker", "baxterthehacker@users.noreply.github.com", date1),
    committer = ApiPersonIdent("baxterthehacker", "baxterthehacker@users.noreply.github.com", date1)
  )(RepositoryName("baxterthehacker", "public-repo"), true)

  val apiComment = ApiComment(
    id = 1,
    user = apiUser,
    body = "Me too",
    created_at = date1,
    updated_at = date1
  )(RepositoryName("octocat", "Hello-World"), 100, false)

  val apiCommentPR = ApiComment(
    id = 1,
    user = apiUser,
    body = "Me too",
    created_at = date1,
    updated_at = date1
  )(RepositoryName("octocat", "Hello-World"), 100, true)

  val apiPersonIdent = ApiPersonIdent("Monalisa Octocat", "support@example.com", date1)

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

  val apiCombinedCommitStatus = ApiCombinedCommitStatus(
    state = "success",
    sha = sha1,
    total_count = 2,
    statuses = List(apiCommitStatus),
    repository = repository
  )

  val apiLabel = ApiLabel(
    name = "bug",
    color = "f29513"
  )(RepositoryName("octocat", "Hello-World"))

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
    body = "Maybe you should use more emoji on this line.",
    created_at = date("2015-05-05T23:40:27Z"),
    updated_at = date("2015-05-05T23:40:27Z")
  )(RepositoryName("baxterthehacker/public-repo"), 1)

  val apiBranchProtection = ApiBranchProtection(
    true,
    Some(ApiBranchProtection.Status(ApiBranchProtection.Everyone, Seq("continuous-integration/travis-ci")))
  )

  val apiBranch = ApiBranch(
    name = "master",
    commit = ApiBranchCommit("468cab6982b37db5eb167568210ec188673fb653"),
    protection = apiBranchProtection
  )(
    repositoryName = repo1Name
  )

  val apiBranchForList = ApiBranchForList("master", ApiBranchCommit("468cab6982b37db5eb167568210ec188673fb653"))

  val apiPusher = ApiPusher(account)

  val apiEndPoint = ApiEndPoint()

  // TODO use factory method defined in companion object?
  val apiPlugin = ApiPlugin(
    id = "gist",
    name = "Gist Plugin",
    version = "4.16.0",
    description = "Provides Gist feature on GitBucket.",
    jarFileName = "gitbucket-gist-plugin-gitbucket_4.30.0-SNAPSHOT-4.17.0.jar"
  )

  val apiError = ApiError(
    message = "A repository with this name already exists on this account",
    documentation_url = Some("https://developer.github.com/v3/repos/#create")
  )

  // TODO use factory method defined in companion object?
  val apiGroup = ApiGroup("octocats", Some("Admin group"), date1)

  val apiRef = ApiRef(
    ref = "refs/heads/featureA",
    `object` = ApiObject("aa218f56b14c9653891f9e74264a383fa43fefbd")
  )

  // TODO use factory method defined in companion object?
  val apiContents = ApiContents(
    `type` = "file",
    name = "README.md",
    path = "README.md",
    sha = "3d21ec53a331a6f037a91c368710b99387d012c1",
    content = Some(Base64.getEncoder.encodeToString("README".getBytes("UTF-8"))),
    encoding = Some("base64")
  )(repo1Name)

  val apiCommits = ApiCommits(
    repositoryName = repo1Name,
    commitInfo = CommitInfo(
      id = "3d21ec53a331a6f037a91c368710b99387d012c1",
      shortMessage = "short message",
      fullMessage = "full message",
      parents = List("1da452aa92d7db1bc093d266c80a69857718c406"),
      authorTime = date1,
      authorName = "octocat",
      authorEmailAddress = "octocat@example.com",
      commitTime = date1,
      committerName = "octocat",
      committerEmailAddress = "octocat@example.com"
    ),
    diffs = Seq(
      DiffInfo(
        changeType = ChangeType.MODIFY,
        oldPath = "README.md",
        newPath = "README.md",
        oldContent = None,
        newContent = None,
        oldIsImage = false,
        newIsImage = false,
        oldObjectId = None,
        newObjectId = Some("6dcb09b5b57875f334f61aebed695e2e4193db5e"),
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
    commentCount = 1
  )
}
