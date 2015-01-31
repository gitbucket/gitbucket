package service

import model.Profile._
import profile.simple._
import model.{WebHook, Account, Issue, PullRequest, IssueComment, Repository}
import org.slf4j.LoggerFactory
import service.RepositoryService.RepositoryInfo
import util.JGitUtil
import org.eclipse.jgit.diff.DiffEntry
import util.JGitUtil.CommitInfo
import org.eclipse.jgit.api.Git
import org.apache.http.message.BasicNameValuePair
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.NameValuePair
import java.util.Date

trait WebHookService {
  import WebHookService._

  private val logger = LoggerFactory.getLogger(classOf[WebHookService])

  def getWebHookURLs(owner: String, repository: String)(implicit s: Session): List[WebHook] =
    WebHooks.filter(_.byRepository(owner, repository)).sortBy(_.url).list

  def addWebHookURL(owner: String, repository: String, url :String)(implicit s: Session): Unit =
    WebHooks insert WebHook(owner, repository, url)

  def deleteWebHookURL(owner: String, repository: String, url :String)(implicit s: Session): Unit =
    WebHooks.filter(_.byPrimaryKey(owner, repository, url)).delete

  def callWebHookOf(owner: String, repository: String, eventName: String)(makePayload: => Option[WebHookPayload])(implicit s: Session, c: ApiContext): Unit = {
    val webHookURLs = getWebHookURLs(owner, repository)
    if(webHookURLs.nonEmpty){
      makePayload.map(callWebHook(eventName, webHookURLs, _))
    }
  }

  def apiJson(obj: AnyRef)(implicit c: ApiContext): String = {
    org.json4s.jackson.Serialization.write(obj)(jsonFormats + apiPathSerializer(c))
  }

  def callWebHook(eventName: String, webHookURLs: List[WebHook], payload: WebHookPayload)(implicit c: ApiContext): Unit = {
    import org.apache.http.client.methods.HttpPost
    import org.apache.http.impl.client.HttpClientBuilder
    import scala.concurrent._
    import ExecutionContext.Implicits.global

    if(webHookURLs.nonEmpty){
      val json = apiJson(payload)
      val httpClient = HttpClientBuilder.create.build

      webHookURLs.foreach { webHookUrl =>
        val f = Future {
          logger.debug(s"start web hook invocation for ${webHookUrl}")
          val httpPost = new HttpPost(webHookUrl.url)
          httpPost.addHeader("X-Github-Event", eventName)

          val params: java.util.List[NameValuePair] = new java.util.ArrayList()
          params.add(new BasicNameValuePair("payload", json))
          httpPost.setEntity(new UrlEncodedFormEntity(params, "UTF-8"))

          httpClient.execute(httpPost)
          httpPost.releaseConnection()
          logger.debug(s"end web hook invocation for ${webHookUrl}")
        }
        f.onSuccess {
          case s => logger.debug(s"Success: web hook request to ${webHookUrl.url}")
        }
        f.onFailure {
          case t => logger.error(s"Failed: web hook request to ${webHookUrl.url}", t)
        }
      }
    }
    logger.debug("end callWebHook")
  }
}


trait WebHookPullRequestService extends WebHookService {
  self: AccountService with RepositoryService with PullRequestService with IssuesService =>

  import WebHookService._
  // https://developer.github.com/v3/activity/events/types/#issuesevent
  def callIssuesWebHook(action: String, repository: RepositoryService.RepositoryInfo, issue: Issue, baseUrl: String, sender: model.Account)(implicit s: Session, context:ApiContext): Unit = {
    callWebHookOf(repository.owner, repository.name, "issues"){
      val users = getAccountsByUserNames(Set(repository.owner, issue.userName), Set(sender))
      for{
        repoOwner <- users.get(repository.owner)
        issueUser <- users.get(issue.userName)
      } yield {
        WebHookIssuesPayload(
          action       = action,
          number       = issue.issueId,
          repository   = WebHookRepository(repository, WebHookApiUser(repoOwner)),
          issue        = WebHookIssue(issue, WebHookApiUser(issueUser)),
          sender       = WebHookApiUser(sender))
      }
    }
  }

  def callPullRequestWebHook(action: String, repository: RepositoryService.RepositoryInfo, issueId: Int, baseUrl: String, sender: model.Account)(implicit s: Session, context:ApiContext): Unit = {
    import WebHookService._
    callWebHookOf(repository.owner, repository.name, "pull_request"){
      for{
        (issue, pullRequest) <- getPullRequest(repository.owner, repository.name, issueId)
        users = getAccountsByUserNames(Set(repository.owner, pullRequest.requestUserName), Set(sender))
        baseOwner <- users.get(repository.owner)
        headOwner <- users.get(pullRequest.requestUserName)
        headRepo  <- getRepository(pullRequest.requestUserName, pullRequest.requestRepositoryName, baseUrl)
      } yield {
        WebHookPullRequestPayload(
          action         = action,
          issue          = issue,
          pullRequest    = pullRequest,
          headRepository = headRepo,
          headOwner      = headOwner,
          baseRepository = repository,
          baseOwner      = baseOwner,
          sender         = sender)
      }
    }
  }
}

trait WebHookIssueCommentService extends WebHookPullRequestService {
  self: AccountService with RepositoryService with PullRequestService with IssuesService =>

  import WebHookService._
  def callIssueCommentWebHook(repository: RepositoryService.RepositoryInfo, issue: Issue, issueCommentId: Int, sender: model.Account)(implicit s: Session, context:ApiContext): Unit = {
    callWebHookOf(repository.owner, repository.name, "issue_comment"){
      for{
        issueComment <- getComment(repository.owner, repository.name, issueCommentId.toString())
        users = getAccountsByUserNames(Set(issue.userName, repository.owner, issueComment.userName), Set(sender))
        issueUser <- users.get(issue.userName)
        repoOwner <- users.get(repository.owner)
        commenter <- users.get(issueComment.userName)
      } yield {
        WebHookIssueCommentPayload(
          issue          = issue,
          issueUser      = issueUser,
          comment        = issueComment,
          commentUser    = commenter,
          repository     = repository,
          repositoryUser = repoOwner,
          sender         = sender)
      }
    }
  }
}

object WebHookService {
  case class ApiContext(baseUrl:String)

  case class ApiPath(path:String)

  import org.json4s._
  import org.json4s.jackson.Serialization
  val jsonFormats = {
    import scala.util.Try
    import org.joda.time.format._
    import org.joda.time.DateTime
    import org.joda.time.DateTimeZone
    val parserISO = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
    Serialization.formats(NoTypeHints) + new CustomSerializer[Date](format =>
      (
        { case JString(s) => Try(parserISO.parseDateTime(s)).toOption.map(_.toDate)
          .getOrElse(throw new MappingException("Can't convert " + s + " to Date")) },
        { case x: Date => JString(parserISO.print(new DateTime(x).withZone(DateTimeZone.UTC))) }
      )
    ) + FieldSerializer[WebHookApiUser]() + FieldSerializer[WebHookPullRequest]() + FieldSerializer[WebHookRepository]() +
      FieldSerializer[WebHookCommitListItemParent]() + FieldSerializer[WebHookCommitListItem]() + FieldSerializer[WebHookCommitListItemCommit]()
  }
  def apiPathSerializer(c:ApiContext) = new CustomSerializer[ApiPath](format =>
      (
        {
          case JString(s) if s.startsWith(c.baseUrl) => ApiPath(s.substring(c.baseUrl.length))
          case JString(s) => throw new MappingException("Can't convert " + s + " to ApiPath")
        },
        { case ApiPath(path) => JString(c.baseUrl+path) }
      )
    )

  trait WebHookPayload

  // https://developer.github.com/v3/activity/events/types/#pushevent
  case class WebHookPushPayload(
    pusher: WebHookApiUser,
    ref: String,
    commits: List[WebHookCommit],
    repository: WebHookRepository
  ) extends WebHookPayload

  object WebHookPushPayload {
    def apply(git: Git, pusher: Account, refName: String, repositoryInfo: RepositoryInfo,
              commits: List[CommitInfo], repositoryOwner: Account): WebHookPushPayload =
      WebHookPushPayload(
        WebHookApiUser(pusher),
        refName,
        commits.map{ commit => WebHookCommit(git, repositoryInfo, commit) },
        WebHookRepository(
          repositoryInfo,
          owner= WebHookApiUser(repositoryOwner)
        )
      )
  }

  // https://developer.github.com/v3/activity/events/types/#issuesevent
  case class WebHookIssuesPayload(
    action: String,
    number: Int,
    repository: WebHookRepository,
    issue: WebHookIssue,
    sender: WebHookApiUser) extends WebHookPayload

  // https://developer.github.com/v3/activity/events/types/#pullrequestevent
  case class WebHookPullRequestPayload(
    action: String,
    number: Int,
    repository: WebHookRepository,
    pull_request: WebHookPullRequest,
    sender: WebHookApiUser
  ) extends WebHookPayload

  object WebHookPullRequestPayload{
    def apply(action: String,
        issue: Issue,
        pullRequest: PullRequest,
        headRepository: RepositoryInfo,
        headOwner: Account,
        baseRepository: RepositoryInfo,
        baseOwner: Account,
        sender: model.Account): WebHookPullRequestPayload = {
      val headRepoPayload = WebHookRepository(headRepository, headOwner)
      val baseRepoPayload = WebHookRepository(baseRepository, baseOwner)
      val senderPayload = WebHookApiUser(sender)
      val pr = WebHookPullRequest(issue, pullRequest, headRepoPayload, baseRepoPayload, senderPayload)
      WebHookPullRequestPayload(
        action       = action,
        number       = issue.issueId,
        repository   = pr.base.repo,
        pull_request = pr,
        sender       = senderPayload
      )
    }
  }

  // https://developer.github.com/v3/activity/events/types/#issuecommentevent
  case class WebHookIssueCommentPayload(
    action: String,
    repository: WebHookRepository,
    issue: WebHookIssue,
    comment: WebHookComment,
    sender: WebHookApiUser
  ) extends WebHookPayload

  object WebHookIssueCommentPayload{
    def apply(
        issue: Issue,
        issueUser: Account,
        comment: IssueComment,
        commentUser: Account,
        repository: RepositoryInfo,
        repositoryUser: Account,
        sender: Account): WebHookIssueCommentPayload =
      WebHookIssueCommentPayload(
        action       = "created",
        repository   = WebHookRepository(repository, repositoryUser),
        issue        = WebHookIssue(issue, WebHookApiUser(issueUser)),
        comment      = WebHookComment(comment, WebHookApiUser(commentUser)),
        sender       = WebHookApiUser(sender))
  }

  case class WebHookCommit(
    id: String,
    message: String,
    timestamp: Date,
    url: String,
    added: List[String],
    removed: List[String],
    modified: List[String],
    author: WebHookCommitUser,
    committer: WebHookCommitUser)

  object WebHookCommit{
    def apply(git: Git, repositoryInfo: RepositoryInfo, commit: CommitInfo): WebHookCommit = {
      val diffs = JGitUtil.getDiffs(git, commit.id, false)
      val commitUrl = repositoryInfo.httpUrl.replaceFirst("/git/", "/").stripSuffix(".git") + "/commit/" + commit.id
      WebHookCommit(
        id        = commit.id,
        message   = commit.fullMessage,
        timestamp = commit.commitTime,
        url       = commitUrl,
        added     = diffs._1.collect { case x if(x.changeType == DiffEntry.ChangeType.ADD)    => x.newPath },
        removed   = diffs._1.collect { case x if(x.changeType == DiffEntry.ChangeType.DELETE) => x.oldPath },
        modified  = diffs._1.collect { case x if(x.changeType != DiffEntry.ChangeType.ADD &&
          x.changeType != DiffEntry.ChangeType.DELETE) => x.newPath },
        author    = WebHookCommitUser.author(commit),
        committer = WebHookCommitUser.committer(commit)
      )
    }
  }

  case class WebHookApiUser(
    login: String,
    email: String,
    `type`: String,
    site_admin: Boolean,
    created_at: Date) {
    val url                 = ApiPath(s"/api/v3/users/${login}")
    val html_url            = ApiPath(s"/${login}")
    // val followers_url       = ApiPath(s"/api/v3/users/${login}/followers")
    // val following_url       = ApiPath(s"/api/v3/users/${login}/following{/other_user}")
    // val gists_url           = ApiPath(s"/api/v3/users/${login}/gists{/gist_id}")
    // val starred_url         = ApiPath(s"/api/v3/users/${login}/starred{/owner}{/repo}")
    // val subscriptions_url   = ApiPath(s"/api/v3/users/${login}/subscriptions")
    // val organizations_url   = ApiPath(s"/api/v3/users/${login}/orgs")
    // val repos_url           = ApiPath(s"/api/v3/users/${login}/repos")
    // val events_url          = ApiPath(s"/api/v3/users/${login}/events{/privacy}")
    // val received_events_url = ApiPath(s"/api/v3/users/${login}/received_events")
  }

  object WebHookApiUser{
    def apply(user: Account): WebHookApiUser = WebHookApiUser(
      login      = user.fullName,
      email      = user.mailAddress,
      `type`     = if(user.isGroupAccount){ "Organization" }else{ "User" },
      site_admin = user.isAdmin,
      created_at = user.registeredDate
    )
  }

  case class WebHookCommitUser(
    name: String,
    email: String,
    date: Date)

  object WebHookCommitUser {
    def author(commit: CommitInfo): WebHookCommitUser =
      WebHookCommitUser(
        name  = commit.authorName,
        email = commit.authorEmailAddress,
        date  = commit.authorTime)
    def committer(commit: CommitInfo): WebHookCommitUser =
      WebHookCommitUser(
        name  = commit.committerName,
        email = commit.committerEmailAddress,
        date  = commit.commitTime)
  }

  // https://developer.github.com/v3/repos/
  case class WebHookRepository(
    name: String,
    full_name: String,
    description: String,
    watchers: Int,
    forks: Int,
    `private`: Boolean,
    default_branch: String,
    owner: WebHookApiUser) {
    val forks_count   = forks
    val watchers_coun = watchers
    val url       = ApiPath(s"/api/v3/repos/${full_name}")
    val http_url  = ApiPath(s"/git/${full_name}.git")
    val clone_url = ApiPath(s"/git/${full_name}.git")
    val html_url  = ApiPath(s"/${full_name}")
  }

  object WebHookRepository{
    def apply(
        repository: Repository,
        owner: WebHookApiUser,
        forkedCount: Int =0,
        watchers: Int = 0): WebHookRepository =
      WebHookRepository(
        name        = repository.repositoryName,
        full_name   = s"${repository.userName}/${repository.repositoryName}",
        description = repository.description.getOrElse(""),
        watchers    = 0,
        forks       = forkedCount,
        `private`   = repository.isPrivate,
        default_branch = repository.defaultBranch,
        owner       = owner
      )

    def apply(repositoryInfo: RepositoryInfo, owner: WebHookApiUser): WebHookRepository =
      WebHookRepository(repositoryInfo.repository, owner, forkedCount=repositoryInfo.forkedCount)

    def apply(repositoryInfo: RepositoryInfo, owner: Account): WebHookRepository =
      this(repositoryInfo.repository, WebHookApiUser(owner))

  }

  // https://developer.github.com/v3/pulls/
  case class WebHookPullRequest(
    number: Int,
    updated_at: Date,
    created_at: Date,
    head: WebHookPullRequestCommit,
    base: WebHookPullRequestCommit,
    mergeable: Option[Boolean],
    title: String,
    body: String,
    user: WebHookApiUser) {
    val html_url            = ApiPath(s"${base.repo.html_url.path}/pull/${number}")
    //val diff_url            = ApiPath("${base.repo.html_url.path}/pull/${number}.diff")
    //val patch_url           = ApiPath("${base.repo.html_url.path}/pull/${number}.patch")
    val url                 = ApiPath(s"${base.repo.url.path}/pulls/${number}")
    //val issue_url           = ApiPath("${base.repo.url.path}/issues/${number}")
    //val commits_url         = ApiPath("${base.repo.url.path}/pulls/${number}/commits")
    //val review_comments_url = ApiPath("${base.repo.url.path}/pulls/${number}/comments")
    //val review_comment_url  = ApiPath("${base.repo.url.path}/pulls/comments/{number}")
    //val comments_url        = ApiPath("${base.repo.url.path}/issues/${number}/comments")
    //val statuses_url        = ApiPath("${base.repo.url.path}/statuses/${head.sha}")
  }

  object WebHookPullRequest{
    def apply(issue: Issue, pullRequest: PullRequest, headRepo: WebHookRepository, baseRepo: WebHookRepository, user: WebHookApiUser): WebHookPullRequest = WebHookPullRequest(
        number     = issue.issueId,
        updated_at = issue.updatedDate,
        created_at = issue.registeredDate,
        head       = WebHookPullRequestCommit(
                       sha  = pullRequest.commitIdTo,
                       ref  = pullRequest.requestBranch,
                       repo = headRepo),
        base       = WebHookPullRequestCommit(
                       sha  = pullRequest.commitIdFrom,
                       ref  = pullRequest.branch,
                       repo = baseRepo),
        mergeable  = Some(true), // TODO: need check mergeable.
        title      = issue.title,
        body       = issue.content.getOrElse(""),
        user       = user
      )
  }

  case class WebHookPullRequestCommit(
    label: String,
    sha: String,
    ref: String,
    repo: WebHookRepository,
    user: WebHookApiUser)

  object WebHookPullRequestCommit{
    def apply(sha: String,
        ref: String,
        repo: WebHookRepository): WebHookPullRequestCommit =
          WebHookPullRequestCommit(
            label = s"${repo.owner.login}:${ref}",
            sha   = sha,
            ref   = ref,
            repo  = repo,
            user  = repo.owner)
  }

  // https://developer.github.com/v3/issues/
  case class WebHookIssue(
    number: Int,
    title: String,
    user: WebHookApiUser,
    // labels,
    state: String,
    created_at: Date,
    updated_at: Date,
    body: String)

  object WebHookIssue{
    def apply(issue: Issue, user: WebHookApiUser): WebHookIssue =
      WebHookIssue(
        number = issue.issueId,
        title  = issue.title,
        user   = user,
        state  = if(issue.closed){ "closed" }else{ "open" },
        body   = issue.content.getOrElse(""),
        created_at = issue.registeredDate,
        updated_at = issue.updatedDate)
  }

  // https://developer.github.com/v3/issues/comments/
  case class WebHookComment(
    id: Int,
    user: WebHookApiUser,
    body: String,
    created_at: Date,
    updated_at: Date)

  object WebHookComment{
    def apply(comment: IssueComment, user: WebHookApiUser): WebHookComment =
      WebHookComment(
        id = comment.commentId,
        user = user,
        body = comment.content,
        created_at = comment.registeredDate,
        updated_at = comment.updatedDate)
  }

  // https://developer.github.com/v3/issues/comments/#create-a-comment
  case class CreateAComment(body: String)


  // https://developer.github.com/v3/repos/commits/
  case class WebHookCommitListItemParent(sha: String)(repoFullName:String){
    val url = ApiPath(s"/api/v3/repos/${repoFullName}/commits/${sha}")
  }
  case class WebHookCommitListItemCommit(
    message: String,
    author: WebHookCommitUser,
    committer: WebHookCommitUser)(sha:String, repoFullName: String) {
    val url = ApiPath(s"/api/v3/repos/${repoFullName}/git/commits/${sha}")
  }

  case class WebHookCommitListItem(
    sha: String,
    commit: WebHookCommitListItemCommit,
    author: Option[WebHookApiUser],
    committer: Option[WebHookApiUser],
    parents: Seq[WebHookCommitListItemParent])(repoFullName: String) {
    val url = ApiPath(s"/api/v3/repos/${repoFullName}/commits/${sha}")
  }

  object WebHookCommitListItem {
    def apply(commit: CommitInfo, repoFullName:String): WebHookCommitListItem = WebHookCommitListItem(
      sha    = commit.id,
      commit = WebHookCommitListItemCommit(
        message   = commit.fullMessage,
        author    = WebHookCommitUser.author(commit),
        committer = WebHookCommitUser.committer(commit)
        )(commit.id, repoFullName),
      author    = None,
      committer = None,
      parents   = commit.parents.map(WebHookCommitListItemParent(_)(repoFullName)))(repoFullName)
  }

}
