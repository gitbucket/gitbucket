package gitbucket.core.api

import java.util.{Base64, Calendar, Date, TimeZone}

import gitbucket.core.model._
import gitbucket.core.service.RepositoryService.RepositoryInfo
import gitbucket.core.util.JGitUtil.{CommitInfo, DiffInfo, TagInfo}
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
    commitIdTo = sha1
  )

  val commitComment = CommitComment(
    userName = repo1Name.owner,
    repositoryName = repo1Name.name,
    commitId = "0d1a26e67d8f5eaf1f6ba5c57fc3c7d91ac0fd1c",
    commentId = 29724692,
    commentedUserName = "bear",
    content = "Maybe you should use more emoji on this line.",
    fileName = Some("README.md"),
    oldLine = Some(1),
    newLine = Some(1),
    registeredDate = date("2015-05-05T23:40:27Z"),
    updatedDate = date("2015-05-05T23:40:27Z"),
    issueId = Some(issuePR.issueId),
    originalCommitId = "0d1a26e67d8f5eaf1f6ba5c57fc3c7d91ac0fd1c",
    originalOldLine = None,
    originalNewLine = None
  )

  val apiUser = ApiUser(account)

  val apiRepository = ApiRepository(
    repository = repository,
    owner = apiUser,
    forkedCount = repositoryInfo.forkedCount,
    watchers = 0,
    urlIsHtmlUrl = false
  )

  val apiLabel = ApiLabel(
    label = label,
    repositoryName = repo1Name
  )

  val apiIssue = ApiIssue(
    issue = issue,
    repositoryName = repo1Name,
    user = apiUser,
    labels = List(apiLabel)
  )

  val apiIssuePR = ApiIssue(
    issue = issuePR,
    repositoryName = repo1Name,
    user = apiUser,
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

// TODO ------------

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
    repository = apiRepository
  )

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
