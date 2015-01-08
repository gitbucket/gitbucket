package service

import model.Profile._
import profile.simple._
import model.{WebHook, Account, Issue, PullRequest, IssueComment}
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

  def callWebHookOf(owner: String, repository: String, eventName: String)(makePayload: => Option[WebHookPayload])(implicit s: Session): Unit = {
    val webHookURLs = getWebHookURLs(owner, repository)
    if(webHookURLs.nonEmpty){
      makePayload.map(callWebHook(eventName, webHookURLs, _))
    }
  }

  def callWebHook(eventName: String, webHookURLs: List[WebHook], payload: WebHookPayload): Unit = {
    import org.json4s._
    import org.json4s.jackson.Serialization
    import org.json4s.jackson.Serialization.{read, write}
    import org.apache.http.client.methods.HttpPost
    import org.apache.http.impl.client.HttpClientBuilder
    import scala.concurrent._
    import ExecutionContext.Implicits.global

    logger.debug("start callWebHook")
    implicit val formats = Serialization.formats(NoTypeHints)

    if(webHookURLs.nonEmpty){
      val json = write(payload)
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

  // https://developer.github.com/v3/activity/events/types/#issuesevent
  def callIssuesWebHook(action: String, repository: RepositoryService.RepositoryInfo, issue: Issue, baseUrl: String, sender: model.Account)(implicit s: Session): Unit = {
    import WebHookService._
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

  def callPullRequestWebHook(action: String, repository: RepositoryService.RepositoryInfo, issueId: Int, baseUrl: String, sender: model.Account)(implicit s: Session): Unit = {
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

  def callIssueCommentWebHook(repository: RepositoryService.RepositoryInfo, issue: Issue, issueCommentId: Int, sender: model.Account)(implicit s: Session): Unit = {
    import WebHookService._
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
    timestamp: String, // "2014-10-09T17:10:36-07:00",
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
        timestamp = commit.commitTime.toString,
        url       = commitUrl,
        added     = diffs._1.collect { case x if(x.changeType == DiffEntry.ChangeType.ADD)    => x.newPath },
        removed   = diffs._1.collect { case x if(x.changeType == DiffEntry.ChangeType.DELETE) => x.oldPath },
        modified  = diffs._1.collect { case x if(x.changeType != DiffEntry.ChangeType.ADD &&
          x.changeType != DiffEntry.ChangeType.DELETE) => x.newPath },
        author    = WebHookCommitUser(
          name  = commit.authorName,
          email = commit.authorEmailAddress
        ),
        committer = WebHookCommitUser(
          name  = commit.committerName,
          email = commit.committerEmailAddress
        )
      )
    }
  }

  case class WebHookApiUser(
    login: String,
    `type`: String,
    site_admin: Boolean)

  object WebHookApiUser{
    def apply(user: Account): WebHookApiUser = WebHookApiUser(
      login      = user.fullName,
      `type`     = if(user.isGroupAccount){ "Organization" }else{ "User" },
      site_admin = user.isAdmin
    )
  }

  case class WebHookCommitUser(
    name: String,
    email: String)

  // https://developer.github.com/v3/repos/
  case class WebHookRepository(
    name: String,
    url: String,
    description: String,
    watchers: Int,
    forks: Int, // forks_count
    `private`: Boolean,
    owner: WebHookApiUser)

  object WebHookRepository{
    def apply(repositoryInfo: RepositoryInfo, owner: WebHookApiUser): WebHookRepository =
      WebHookRepository(
        name        = repositoryInfo.name,
        url         = repositoryInfo.httpUrl,
        description = repositoryInfo.repository.description.getOrElse(""),
        watchers    = 0,
        forks       = repositoryInfo.forkedCount,
        `private`   = repositoryInfo.repository.isPrivate,
        owner       = owner
      )
    def apply(repositoryInfo: RepositoryInfo, owner: Account): WebHookRepository =
      this(repositoryInfo, WebHookApiUser(owner))
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
    user: WebHookApiUser,
    url: String)

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
        mergeable  = None,
        title      = issue.title,
        body       = issue.content.getOrElse(""),
        user       = user,
        url        = s"${baseRepo.url}/pulls/${issue.issueId}"
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
}
