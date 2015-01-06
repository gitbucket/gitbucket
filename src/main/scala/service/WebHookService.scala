package service

import model.Profile._
import profile.simple._
import model.{WebHook, Account, Issue, PullRequest}
import org.slf4j.LoggerFactory
import service.RepositoryService.RepositoryInfo
import util.JGitUtil
import org.eclipse.jgit.diff.DiffEntry
import util.JGitUtil.CommitInfo
import org.eclipse.jgit.api.Git
import org.apache.http.message.BasicNameValuePair
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.NameValuePair

trait WebHookService {
  import WebHookService._

  private val logger = LoggerFactory.getLogger(classOf[WebHookService])

  def getWebHookURLs(owner: String, repository: String)(implicit s: Session): List[WebHook] =
    WebHooks.filter(_.byRepository(owner, repository)).sortBy(_.url).list

  def addWebHookURL(owner: String, repository: String, url :String)(implicit s: Session): Unit =
    WebHooks insert WebHook(owner, repository, url)

  def deleteWebHookURL(owner: String, repository: String, url :String)(implicit s: Session): Unit =
    WebHooks.filter(_.byPrimaryKey(owner, repository, url)).delete

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

object WebHookService {

  trait WebHookPayload

  case class WebHookPushPayload(
    pusher: WebHookApiUser,
    ref: String,
    commits: List[WebHookCommit],
    repository: WebHookRepository
  ) extends WebHookPayload

  object WebHookPayload {
    def apply(git: Git, pusher: Account, refName: String, repositoryInfo: RepositoryInfo,
              commits: List[CommitInfo], repositoryOwner: Account): WebHookPayload =
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

  case class WebHookCommit(
    id: String,
    message: String,
    timestamp: String,
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

  case class WebHookRepository(
    name: String,
    url: String,
    description: String,
    watchers: Int,
    forks: Int,
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
  }

  case class WebHookCommitUser(
    name: String,
    email: String)

  case class WebHookPullRequestPayload(
    val action: String,
    val number: Int,
    val repository: WebHookRepository,
    val pull_request: WebHookPullRequest,
    val sender: WebHookApiUser
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
      val headRepoPayload = WebHookRepository(headRepository, owner=WebHookApiUser(headOwner))
      val baseRepoPayload = WebHookRepository(baseRepository, owner=WebHookApiUser(baseOwner))
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

  case class WebHookPullRequest(
    number: Int,
    updated_at: String,
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
        updated_at = issue.updatedDate.toString(),
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
        repo: WebHookRepository): WebHookPullRequestCommit = WebHookPullRequestCommit(
                                                               label = s"${repo.owner.login}:${ref}",
                                                               sha   = sha,
                                                               ref   = ref,
                                                               repo  = repo,
                                                               user  = repo.owner)
  }
}
