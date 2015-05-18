package gitbucket.core.service

import gitbucket.core.api._
import gitbucket.core.model.{WebHook, Account, Issue, PullRequest, IssueComment}
import gitbucket.core.model.Profile._
import profile.simple._
import gitbucket.core.util.JGitUtil.CommitInfo
import gitbucket.core.util.RepositoryName
import gitbucket.core.service.RepositoryService.RepositoryInfo

import org.apache.http.NameValuePair
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.message.BasicNameValuePair
import org.eclipse.jgit.api.Git
import org.slf4j.LoggerFactory


trait WebHookService {
  import WebHookService._

  private val logger = LoggerFactory.getLogger(classOf[WebHookService])

  def getWebHookURLs(owner: String, repository: String)(implicit s: Session): List[WebHook] =
    WebHooks.filter(_.byRepository(owner, repository)).sortBy(_.url).list

  def addWebHookURL(owner: String, repository: String, url :String)(implicit s: Session): Unit =
    WebHooks insert WebHook(owner, repository, url)

  def deleteWebHookURL(owner: String, repository: String, url :String)(implicit s: Session): Unit =
    WebHooks.filter(_.byPrimaryKey(owner, repository, url)).delete

  def callWebHookOf(owner: String, repository: String, eventName: String)(makePayload: => Option[WebHookPayload])(implicit s: Session, c: JsonFormat.Context): Unit = {
    val webHookURLs = getWebHookURLs(owner, repository)
    if(webHookURLs.nonEmpty){
      makePayload.map(callWebHook(eventName, webHookURLs, _))
    }
  }

  def callWebHook(eventName: String, webHookURLs: List[WebHook], payload: WebHookPayload)(implicit c: JsonFormat.Context): Unit = {
    import org.apache.http.client.methods.HttpPost
    import org.apache.http.impl.client.HttpClientBuilder
    import scala.concurrent._
    import ExecutionContext.Implicits.global

    if(webHookURLs.nonEmpty){
      val json = JsonFormat(payload)
      val httpClient = HttpClientBuilder.create.build

      webHookURLs.foreach { webHookUrl =>
        val f = Future {
          logger.debug(s"start web hook invocation for ${webHookUrl}")
          val httpPost = new HttpPost(webHookUrl.url)
          httpPost.addHeader("Content-Type", "application/x-www-form-urlencoded")
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
  def callIssuesWebHook(action: String, repository: RepositoryService.RepositoryInfo, issue: Issue, baseUrl: String, sender: Account)(implicit s: Session, context:JsonFormat.Context): Unit = {
    callWebHookOf(repository.owner, repository.name, "issues"){
      val users = getAccountsByUserNames(Set(repository.owner, issue.openedUserName), Set(sender))
      for{
        repoOwner <- users.get(repository.owner)
        issueUser <- users.get(issue.openedUserName)
      } yield {
        WebHookIssuesPayload(
          action       = action,
          number       = issue.issueId,
          repository   = ApiRepository(repository, ApiUser(repoOwner)),
          issue        = ApiIssue(issue, RepositoryName(repository), ApiUser(issueUser)),
          sender       = ApiUser(sender))
      }
    }
  }

  def callPullRequestWebHook(action: String, repository: RepositoryService.RepositoryInfo, issueId: Int, baseUrl: String, sender: Account)(implicit s: Session, context:JsonFormat.Context): Unit = {
    import WebHookService._
    callWebHookOf(repository.owner, repository.name, "pull_request"){
      for{
        (issue, pullRequest) <- getPullRequest(repository.owner, repository.name, issueId)
        users = getAccountsByUserNames(Set(repository.owner, pullRequest.requestUserName, issue.openedUserName), Set(sender))
        baseOwner <- users.get(repository.owner)
        headOwner <- users.get(pullRequest.requestUserName)
        issueUser <- users.get(issue.openedUserName)
        headRepo  <- getRepository(pullRequest.requestUserName, pullRequest.requestRepositoryName, baseUrl)
      } yield {
        WebHookPullRequestPayload(
          action         = action,
          issue          = issue,
          issueUser      = issueUser,
          pullRequest    = pullRequest,
          headRepository = headRepo,
          headOwner      = headOwner,
          baseRepository = repository,
          baseOwner      = baseOwner,
          sender         = sender)
      }
    }
  }

  /** @return Map[(issue, issueUser, pullRequest, baseOwner, headOwner), webHooks] */
  def getPullRequestsByRequestForWebhook(userName:String, repositoryName:String, branch:String)
                                       (implicit s: Session): Map[(Issue, Account, PullRequest, Account, Account), List[WebHook]] =
    (for{
      is <- Issues if is.closed    === false.bind
      pr <- PullRequests if pr.byPrimaryKey(is.userName, is.repositoryName, is.issueId)
      if pr.requestUserName        === userName.bind
      if pr.requestRepositoryName  === repositoryName.bind
      if pr.requestBranch          === branch.bind
      bu <- Accounts if bu.userName === pr.userName
      ru <- Accounts if ru.userName === pr.requestUserName
      iu <- Accounts if iu.userName === is.openedUserName
      wh <- WebHooks if wh.byRepository(is.userName , is.repositoryName)
    } yield {
      ((is, iu, pr, bu, ru), wh)
    }).list.groupBy(_._1).mapValues(_.map(_._2))

  def callPullRequestWebHookByRequestBranch(action: String, requestRepository: RepositoryService.RepositoryInfo, requestBranch: String, baseUrl: String, sender: Account)(implicit s: Session, context:JsonFormat.Context): Unit = {
    import WebHookService._
    for{
      ((issue, issueUser, pullRequest, baseOwner, headOwner), webHooks) <- getPullRequestsByRequestForWebhook(requestRepository.owner, requestRepository.name, requestBranch)
      baseRepo <- getRepository(pullRequest.userName, pullRequest.repositoryName, baseUrl)
    } yield {
      val payload = WebHookPullRequestPayload(
        action         = action,
        issue          = issue,
        issueUser      = issueUser,
        pullRequest    = pullRequest,
        headRepository = requestRepository,
        headOwner      = headOwner,
        baseRepository = baseRepo,
        baseOwner      = baseOwner,
        sender         = sender)
      callWebHook("pull_request", webHooks, payload)
    }
  }
}

trait WebHookIssueCommentService extends WebHookPullRequestService {
  self: AccountService with RepositoryService with PullRequestService with IssuesService =>

  import WebHookService._
  def callIssueCommentWebHook(repository: RepositoryService.RepositoryInfo, issue: Issue, issueCommentId: Int, sender: Account)(implicit s: Session, context:JsonFormat.Context): Unit = {
    callWebHookOf(repository.owner, repository.name, "issue_comment"){
      for{
        issueComment <- getComment(repository.owner, repository.name, issueCommentId.toString())
        users = getAccountsByUserNames(Set(issue.openedUserName, repository.owner, issueComment.commentedUserName), Set(sender))
        issueUser <- users.get(issue.openedUserName)
        repoOwner <- users.get(repository.owner)
        commenter <- users.get(issueComment.commentedUserName)
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
    pusher: ApiUser,
    ref: String,
    commits: List[ApiCommit],
    repository: ApiRepository
  ) extends WebHookPayload

  object WebHookPushPayload {
    def apply(git: Git, pusher: Account, refName: String, repositoryInfo: RepositoryInfo,
              commits: List[CommitInfo], repositoryOwner: Account): WebHookPushPayload =
      WebHookPushPayload(
        ApiUser(pusher),
        refName,
        commits.map{ commit => ApiCommit(git, RepositoryName(repositoryInfo), commit) },
        ApiRepository(
          repositoryInfo,
          owner= ApiUser(repositoryOwner)
        )
      )
  }

  // https://developer.github.com/v3/activity/events/types/#issuesevent
  case class WebHookIssuesPayload(
    action: String,
    number: Int,
    repository: ApiRepository,
    issue: ApiIssue,
    sender: ApiUser) extends WebHookPayload

  // https://developer.github.com/v3/activity/events/types/#pullrequestevent
  case class WebHookPullRequestPayload(
    action: String,
    number: Int,
    repository: ApiRepository,
    pull_request: ApiPullRequest,
    sender: ApiUser
  ) extends WebHookPayload

  object WebHookPullRequestPayload{
    def apply(action: String,
        issue: Issue,
        issueUser: Account,
        pullRequest: PullRequest,
        headRepository: RepositoryInfo,
        headOwner: Account,
        baseRepository: RepositoryInfo,
        baseOwner: Account,
        sender: Account): WebHookPullRequestPayload = {
      val headRepoPayload = ApiRepository(headRepository, headOwner)
      val baseRepoPayload = ApiRepository(baseRepository, baseOwner)
      val senderPayload = ApiUser(sender)
      val pr = ApiPullRequest(issue, pullRequest, headRepoPayload, baseRepoPayload, ApiUser(issueUser))
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
    repository: ApiRepository,
    issue: ApiIssue,
    comment: ApiComment,
    sender: ApiUser
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
        repository   = ApiRepository(repository, repositoryUser),
        issue        = ApiIssue(issue, RepositoryName(repository), ApiUser(issueUser)),
        comment      = ApiComment(comment, RepositoryName(repository), issue.issueId, ApiUser(commentUser)),
        sender       = ApiUser(sender))
  }
}
