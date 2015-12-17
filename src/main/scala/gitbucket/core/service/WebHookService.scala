package gitbucket.core.service

import gitbucket.core.api._
import gitbucket.core.model.{WebHook, Account, Issue, PullRequest, IssueComment, WebHookEvent, CommitComment}
import gitbucket.core.model.Profile._
import profile.simple._
import gitbucket.core.util.JGitUtil.CommitInfo
import gitbucket.core.util.RepositoryName
import gitbucket.core.service.RepositoryService.RepositoryInfo

import org.apache.http.NameValuePair
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.message.BasicNameValuePair
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ObjectId
import org.slf4j.LoggerFactory
import scala.concurrent._
import org.apache.http.HttpRequest
import org.apache.http.HttpResponse


trait WebHookService {
  import WebHookService._

  private val logger = LoggerFactory.getLogger(classOf[WebHookService])

  /** get All WebHook informations of repository */
  def getWebHooks(owner: String, repository: String)(implicit s: Session): List[(WebHook, Set[WebHook.Event])] =
    WebHooks.filter(_.byRepository(owner, repository))
      .innerJoin(WebHookEvents).on { (w, t) => t.byWebHook(w) }
      .map{ case (w,t) => w -> t.event }
      .list.groupBy(_._1).mapValues(_.map(_._2).toSet).toList.sortBy(_._1.url)

  /** get All WebHook informations of repository event */
  def getWebHooksByEvent(owner: String, repository: String, event: WebHook.Event)(implicit s: Session): List[WebHook] =
    WebHookEvents.filter(t => t.byRepository(owner, repository) && t.event === event.bind)
      .list.map(t => WebHook(t.userName, t.repositoryName, t.url))

  /** get All WebHook information from repository to url */
  def getWebHook(owner: String, repository: String, url: String)(implicit s: Session): Option[(WebHook, Set[WebHook.Event])] =
    WebHooks
      .filter(_.byPrimaryKey(owner, repository, url))
      .innerJoin(WebHookEvents).on { (w, t) => t.byWebHook(w) }
      .map{ case (w,t) => w -> t.event }
      .list.groupBy(_._1).mapValues(_.map(_._2).toSet).headOption

  def addWebHook(owner: String, repository: String, url :String, events: Set[WebHook.Event])(implicit s: Session): Unit = {
    WebHooks insert WebHook(owner, repository, url)
    events.toSet.map{ event: WebHook.Event =>
      WebHookEvents insert WebHookEvent(owner, repository, url, event)
    }
  }

  def updateWebHook(owner: String, repository: String, url :String, events: Set[WebHook.Event])(implicit s: Session): Unit = {
    WebHookEvents.filter(_.byWebHook(owner, repository, url)).delete
    events.toSet.map{ event: WebHook.Event =>
      WebHookEvents insert WebHookEvent(owner, repository, url, event)
    }
  }

  def deleteWebHook(owner: String, repository: String, url :String)(implicit s: Session): Unit =
    WebHooks.filter(_.byPrimaryKey(owner, repository, url)).delete

  def callWebHookOf(owner: String, repository: String, event: WebHook.Event)(makePayload: => Option[WebHookPayload])(implicit s: Session, c: JsonFormat.Context): Unit = {
    val webHooks = getWebHooksByEvent(owner, repository, event)
    if(webHooks.nonEmpty){
      makePayload.map(callWebHook(event, webHooks, _))
    }
  }

  def callWebHook(event: WebHook.Event, webHookURLs: List[WebHook], payload: WebHookPayload)(implicit c: JsonFormat.Context): List[(WebHook, String, Future[HttpRequest], Future[HttpResponse])] = {
    import org.apache.http.impl.client.HttpClientBuilder
    import ExecutionContext.Implicits.global
    import org.apache.http.protocol.HttpContext
    import org.apache.http.client.methods.HttpPost

    if(webHookURLs.nonEmpty){
      val json = JsonFormat(payload)

      webHookURLs.map { webHookUrl =>
        val reqPromise = Promise[HttpRequest]
        val f = Future {
          val itcp = new org.apache.http.HttpRequestInterceptor{
            def process(res: HttpRequest, ctx: HttpContext): Unit = {
              reqPromise.success(res)
            }
          }
          try{
            val httpClient = HttpClientBuilder.create.addInterceptorLast(itcp).build
            logger.debug(s"start web hook invocation for ${webHookUrl.url}")
            val httpPost = new HttpPost(webHookUrl.url)
            httpPost.addHeader("Content-Type", "application/x-www-form-urlencoded")
            httpPost.addHeader("X-Github-Event", event.name)
            httpPost.addHeader("X-Github-Delivery", java.util.UUID.randomUUID().toString)

            val params: java.util.List[NameValuePair] = new java.util.ArrayList()
            params.add(new BasicNameValuePair("payload", json))
            httpPost.setEntity(new UrlEncodedFormEntity(params, "UTF-8"))

            val res = httpClient.execute(httpPost)
            httpPost.releaseConnection()
            logger.debug(s"end web hook invocation for ${webHookUrl}")
            res
          }catch{
            case e:Throwable => {
              if(!reqPromise.isCompleted){
                reqPromise.failure(e)
              }
              throw e
            }
          }
        }
        f.onSuccess {
          case s => logger.debug(s"Success: web hook request to ${webHookUrl.url}")
        }
        f.onFailure {
          case t => logger.error(s"Failed: web hook request to ${webHookUrl.url}", t)
        }
        (webHookUrl, json, reqPromise.future, f)
      }
    } else {
      Nil
    }
    // logger.debug("end callWebHook")
  }
}


trait WebHookPullRequestService extends WebHookService {
  self: AccountService with RepositoryService with PullRequestService with IssuesService =>

  import WebHookService._
  // https://developer.github.com/v3/activity/events/types/#issuesevent
  def callIssuesWebHook(action: String, repository: RepositoryService.RepositoryInfo, issue: Issue, baseUrl: String, sender: Account)(implicit s: Session, context:JsonFormat.Context): Unit = {
    callWebHookOf(repository.owner, repository.name, WebHook.Issues){
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
    callWebHookOf(repository.owner, repository.name, WebHook.PullRequest){
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
      wht <- WebHookEvents if wht.event === WebHook.PullRequest.asInstanceOf[WebHook.Event].bind && wht.byWebHook(wh)
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
      callWebHook(WebHook.PullRequest, webHooks, payload)
    }
  }
}

trait WebHookPullRequestReviewCommentService extends WebHookService {
  self: AccountService with RepositoryService with PullRequestService with IssuesService with CommitsService =>
  def callPullRequestReviewCommentWebHook(action: String, comment: CommitComment, repository: RepositoryService.RepositoryInfo, issueId: Int, baseUrl: String, sender: Account)(implicit s: Session, context:JsonFormat.Context): Unit = {
    import WebHookService._
    callWebHookOf(repository.owner, repository.name, WebHook.PullRequestReviewComment){
      for{
        (issue, pullRequest) <- getPullRequest(repository.owner, repository.name, issueId)
        users = getAccountsByUserNames(Set(repository.owner, pullRequest.requestUserName, issue.openedUserName), Set(sender))
        baseOwner <- users.get(repository.owner)
        headOwner <- users.get(pullRequest.requestUserName)
        issueUser <- users.get(issue.openedUserName)
        headRepo  <- getRepository(pullRequest.requestUserName, pullRequest.requestRepositoryName, baseUrl)
      } yield {
        WebHookPullRequestReviewCommentPayload(
          action         = action,
          comment        = comment,
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
}

trait WebHookIssueCommentService extends WebHookPullRequestService {
  self: AccountService with RepositoryService with PullRequestService with IssuesService =>

  import WebHookService._
  def callIssueCommentWebHook(repository: RepositoryService.RepositoryInfo, issue: Issue, issueCommentId: Int, sender: Account)(implicit s: Session, context:JsonFormat.Context): Unit = {
    callWebHookOf(repository.owner, repository.name, WebHook.IssueComment){
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
    pusher: ApiPusher,
    sender: ApiUser,
    ref: String,
    before: String,
    after: String,
    commits: List[ApiCommit],
    repository: ApiRepository
  ) extends FieldSerializable with WebHookPayload {
    val compare = commits.size match {
      case 0 => ApiPath(s"/${repository.full_name}") // maybe test hook on un-initalied repository
      case 1 => ApiPath(s"/${repository.full_name}/commit/${after}")
      case _ if before.filterNot(_=='0').isEmpty => ApiPath(s"/${repository.full_name}/compare/${commits.head.id}^...${after}")
      case _ => ApiPath(s"/${repository.full_name}/compare/${before}...${after}")
    }
    val head_commit = commits.lastOption
  }

  object WebHookPushPayload {
    def apply(git: Git, sender: Account, refName: String, repositoryInfo: RepositoryInfo,
              commits: List[CommitInfo], repositoryOwner: Account,
              newId: ObjectId, oldId: ObjectId): WebHookPushPayload =
      WebHookPushPayload(
        pusher     = ApiPusher(sender),
        sender     = ApiUser(sender),
        ref        = refName,
        before     = ObjectId.toString(oldId),
        after      = ObjectId.toString(newId),
        commits    = commits.map{ commit => ApiCommit.forPushPayload(git, RepositoryName(repositoryInfo), commit) },
        repository = ApiRepository.forPushPayload(
          repositoryInfo,
          owner= ApiUser(repositoryOwner))
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
        comment      = ApiComment(comment, RepositoryName(repository), issue.issueId, ApiUser(commentUser), issue.isPullRequest),
        sender       = ApiUser(sender))
  }

  // https://developer.github.com/v3/activity/events/types/#pullrequestreviewcommentevent
  case class WebHookPullRequestReviewCommentPayload(
    action: String,
    comment: ApiPullRequestReviewComment,
    pull_request: ApiPullRequest,
    repository: ApiRepository,
    sender: ApiUser
  ) extends WebHookPayload

  object WebHookPullRequestReviewCommentPayload{
    def apply(
        action: String,
        comment: CommitComment,
        issue: Issue,
        issueUser: Account,
        pullRequest: PullRequest,
        headRepository: RepositoryInfo,
        headOwner: Account,
        baseRepository: RepositoryInfo,
        baseOwner: Account,
        sender: Account
      ) : WebHookPullRequestReviewCommentPayload = {
      val headRepoPayload = ApiRepository(headRepository, headOwner)
      val baseRepoPayload = ApiRepository(baseRepository, baseOwner)
      val senderPayload = ApiUser(sender)
      WebHookPullRequestReviewCommentPayload(
        action = action,
        comment = ApiPullRequestReviewComment(comment, senderPayload, RepositoryName(baseRepository), issue.issueId),
        pull_request = ApiPullRequest(issue, pullRequest, headRepoPayload, baseRepoPayload, ApiUser(issueUser)),
        repository = baseRepoPayload,
        sender = senderPayload)
    }
  }
}
