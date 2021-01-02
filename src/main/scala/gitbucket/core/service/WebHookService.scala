package gitbucket.core.service

import fr.brouillard.oss.security.xhub.XHub
import fr.brouillard.oss.security.xhub.XHub.{XHubConverter, XHubDigest}
import gitbucket.core.api._
import gitbucket.core.controller.Context
import gitbucket.core.model.{
  Account,
  AccountWebHook,
  AccountWebHookEvent,
  CommitComment,
  Issue,
  IssueComment,
  Label,
  PullRequest,
  RepositoryWebHook,
  RepositoryWebHookEvent,
  WebHook
}
import gitbucket.core.model.Profile._
import gitbucket.core.model.Profile.profile.blockingApi._
import org.apache.http.client.utils.URLEncodedUtils
import gitbucket.core.util.JGitUtil.CommitInfo
import gitbucket.core.util.Implicits._
import gitbucket.core.util.{HttpClientUtil, RepositoryName, StringUtil}
import gitbucket.core.service.RepositoryService.RepositoryInfo
import org.apache.http.NameValuePair
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.message.BasicNameValuePair
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ObjectId
import org.slf4j.LoggerFactory

import scala.concurrent._
import scala.util.{Failure, Success}
import org.apache.http.HttpRequest
import org.apache.http.HttpResponse
import gitbucket.core.model.WebHookContentType
import gitbucket.core.service.SystemSettingsService.SystemSettings
import org.apache.http.client.entity.EntityBuilder
import org.apache.http.entity.ContentType

trait WebHookService {
  import WebHookService._

  private val logger = LoggerFactory.getLogger(classOf[WebHookService])

  /** get All WebHook informations of repository */
  def getWebHooks(owner: String, repository: String)(
    implicit s: Session
  ): List[(RepositoryWebHook, Set[WebHook.Event])] =
    RepositoryWebHooks
      .filter(_.byRepository(owner, repository))
      .join(RepositoryWebHookEvents)
      .on { (w, t) =>
        t.byRepositoryWebHook(w)
      }
      .map { case (w, t) => w -> t.event }
      .list
      .groupBy(_._1)
      .view
      .mapValues(_.map(_._2).toSet)
      .toList
      .sortBy(_._1.url)

  /** get All WebHook informations of repository event */
  def getWebHooksByEvent(owner: String, repository: String, event: WebHook.Event)(
    implicit s: Session
  ): List[RepositoryWebHook] =
    RepositoryWebHooks
      .filter(_.byRepository(owner, repository))
      .join(RepositoryWebHookEvents)
      .on { (wh, whe) =>
        whe.byRepositoryWebHook(wh)
      }
      .filter { case (wh, whe) => whe.event === event.bind }
      .map { case (wh, whe) => wh }
      .list
      .distinct

  /** get All WebHook information from repository to url */
  def getWebHook(owner: String, repository: String, url: String)(
    implicit s: Session
  ): Option[(RepositoryWebHook, Set[WebHook.Event])] =
    RepositoryWebHooks
      .filter(_.byRepositoryUrl(owner, repository, url))
      .join(RepositoryWebHookEvents)
      .on { (w, t) =>
        t.byRepositoryWebHook(w)
      }
      .map { case (w, t) => w -> t.event }
      .list
      .groupBy(_._1)
      .view
      .mapValues(_.map(_._2).toSet)
      .headOption

  /** get All WebHook informations of repository */
  def getWebHookById(id: Int)(
    implicit s: Session
  ): Option[(RepositoryWebHook, Set[WebHook.Event])] =
    RepositoryWebHooks
      .filter(_.byId(id))
      .join(RepositoryWebHookEvents)
      .on { (w, t) =>
        t.byRepositoryWebHook(w)
      }
      .map { case (w, t) => w -> t.event }
      .list
      .groupBy(_._1)
      .view
      .mapValues(_.map(_._2).toSet)
      .toList
      .headOption

  def addWebHook(
    owner: String,
    repository: String,
    url: String,
    events: Set[WebHook.Event],
    ctype: WebHookContentType,
    token: Option[String]
  )(implicit s: Session): Unit = {
    RepositoryWebHooks insert RepositoryWebHook(
      userName = owner,
      repositoryName = repository,
      url = url,
      ctype = ctype,
      token = token
    )
    events.map { event: WebHook.Event =>
      RepositoryWebHookEvents insert RepositoryWebHookEvent(owner, repository, url, event)
    }
  }

  def updateWebHook(
    owner: String,
    repository: String,
    url: String,
    events: Set[WebHook.Event],
    ctype: WebHookContentType,
    token: Option[String]
  )(implicit s: Session): Unit = {
    RepositoryWebHooks
      .filter(_.byRepositoryUrl(owner, repository, url))
      .map(w => (w.ctype, w.token))
      .update((ctype, token))
    RepositoryWebHookEvents.filter(_.byRepositoryWebHook(owner, repository, url)).delete
    events.map { event: WebHook.Event =>
      RepositoryWebHookEvents insert RepositoryWebHookEvent(owner, repository, url, event)
    }
  }

  def updateWebHookByApi(
    id: Int,
    owner: String,
    repository: String,
    url: String,
    events: Set[WebHook.Event],
    ctype: WebHookContentType,
    token: Option[String]
  )(implicit s: Session): Unit = {
    RepositoryWebHooks
      .filter(_.byId(id))
      .map(w => (w.url, w.ctype, w.token))
      .update((url, ctype, token))
    RepositoryWebHookEvents.filter(_.byRepositoryWebHook(owner, repository, url)).delete
    events.map { event: WebHook.Event =>
      RepositoryWebHookEvents insert RepositoryWebHookEvent(owner, repository, url, event)
    }
  }

  // Records in WEB_HOOK_EVENT will be deleted automatically by cascaded constraint
  def deleteWebHook(owner: String, repository: String, url: String)(implicit s: Session): Unit =
    RepositoryWebHooks.filter(_.byRepositoryUrl(owner, repository, url)).delete

  // Records in WEB_HOOK_EVENT will be deleted automatically by cascaded constraint
  def deleteWebHookById(id: Int)(implicit s: Session): Unit =
    RepositoryWebHooks.filter(_.byId(id)).delete

  /** get All AccountWebHook informations of user */
  def getAccountWebHooks(owner: String)(implicit s: Session): List[(AccountWebHook, Set[WebHook.Event])] =
    AccountWebHooks
      .filter(_.byAccount(owner))
      .join(AccountWebHookEvents)
      .on { (w, t) =>
        t.byAccountWebHook(w)
      }
      .map { case (w, t) => w -> t.event }
      .list
      .groupBy(_._1)
      .view
      .mapValues(_.map(_._2).toSet)
      .toList
      .sortBy(_._1.url)

  /** get All AccountWebHook informations of repository event */
  def getAccountWebHooksByEvent(owner: String, event: WebHook.Event)(implicit s: Session): List[AccountWebHook] =
    AccountWebHooks
      .filter(_.byAccount(owner))
      .join(AccountWebHookEvents)
      .on { (wh, whe) =>
        whe.byAccountWebHook(wh)
      }
      .filter { case (wh, whe) => whe.event === event.bind }
      .map { case (wh, whe) => wh }
      .list
      .distinct

  /** get All AccountWebHook information from repository to url */
  def getAccountWebHook(owner: String, url: String)(implicit s: Session): Option[(AccountWebHook, Set[WebHook.Event])] =
    AccountWebHooks
      .filter(_.byPrimaryKey(owner, url))
      .join(AccountWebHookEvents)
      .on { (w, t) =>
        t.byAccountWebHook(w)
      }
      .map { case (w, t) => w -> t.event }
      .list
      .groupBy(_._1)
      .view
      .mapValues(_.map(_._2).toSet)
      .headOption

  def addAccountWebHook(
    owner: String,
    url: String,
    events: Set[WebHook.Event],
    ctype: WebHookContentType,
    token: Option[String]
  )(implicit s: Session): Unit = {
    AccountWebHooks insert AccountWebHook(owner, url, ctype, token)
    events.map { event: WebHook.Event =>
      AccountWebHookEvents insert AccountWebHookEvent(owner, url, event)
    }
  }

  def updateAccountWebHook(
    owner: String,
    url: String,
    events: Set[WebHook.Event],
    ctype: WebHookContentType,
    token: Option[String]
  )(implicit s: Session): Unit = {
    AccountWebHooks.filter(_.byPrimaryKey(owner, url)).map(w => (w.ctype, w.token)).update((ctype, token))
    AccountWebHookEvents.filter(_.byAccountWebHook(owner, url)).delete
    events.map { event: WebHook.Event =>
      AccountWebHookEvents insert AccountWebHookEvent(owner, url, event)
    }
  }

  def deleteAccountWebHook(owner: String, url: String)(implicit s: Session): Unit =
    AccountWebHooks.filter(_.byPrimaryKey(owner, url)).delete

  def callWebHookOf(owner: String, repository: String, event: WebHook.Event, settings: SystemSettings)(
    makePayload: => Option[WebHookPayload]
  )(implicit s: Session, c: JsonFormat.Context): Unit = {
    val webHooks = getWebHooksByEvent(owner, repository, event)
    if (webHooks.nonEmpty) {
      makePayload.foreach(callWebHook(event, webHooks, _, settings))
    }
    val accountWebHooks = getAccountWebHooksByEvent(owner, event)
    if (accountWebHooks.nonEmpty) {
      makePayload.foreach(callWebHook(event, accountWebHooks, _, settings))
    }
  }

  private def validateTargetAddress(settings: SystemSettings, url: String): Boolean = {
    val host = new java.net.URL(url).getHost

    !settings.webHook.blockPrivateAddress ||
    !HttpClientUtil.isPrivateAddress(host) ||
    settings.webHook.whitelist.exists(range => HttpClientUtil.inIpRange(range, host))
  }

  def callWebHook(event: WebHook.Event, webHooks: List[WebHook], payload: WebHookPayload, settings: SystemSettings)(
    implicit c: JsonFormat.Context
  ): List[(WebHook, String, Future[HttpRequest], Future[HttpResponse])] = {
    import org.apache.http.impl.client.HttpClientBuilder
    import ExecutionContext.Implicits.global // TODO Shouldn't use the default execution context
    import org.apache.http.protocol.HttpContext
    import org.apache.http.client.methods.HttpPost

    if (webHooks.nonEmpty) {
      val json = JsonFormat(payload)

      webHooks.map { webHook =>
        val reqPromise = Promise[HttpRequest]()
        val f = Future {
          val itcp = new org.apache.http.HttpRequestInterceptor {
            def process(res: HttpRequest, ctx: HttpContext): Unit = {
              reqPromise.success(res)
            }
          }
          try {
            if (!validateTargetAddress(settings, webHook.url)) {
              throw new IllegalArgumentException(s"Illegal address: ${webHook.url}")
            }
            val httpClient = HttpClientBuilder.create.useSystemProperties.addInterceptorLast(itcp).build
            logger.debug(s"start web hook invocation for ${webHook.url}")
            val httpPost = new HttpPost(webHook.url)
            logger.debug(s"Content-Type: ${webHook.ctype.ctype}")
            httpPost.addHeader("Content-Type", webHook.ctype.ctype)
            httpPost.addHeader("X-Github-Event", event.name)
            httpPost.addHeader("X-Github-Delivery", java.util.UUID.randomUUID().toString)

            webHook.ctype match {
              case WebHookContentType.FORM => {
                val params: java.util.List[NameValuePair] = new java.util.ArrayList()
                params.add(new BasicNameValuePair("payload", json))
                def postContent = new UrlEncodedFormEntity(params, "UTF-8")
                httpPost.setEntity(postContent)
                if (webHook.token.exists(_.trim.nonEmpty)) {
                  // TODO find a better way and see how to extract content from postContent
                  val contentAsBytes = URLEncodedUtils.format(params, "UTF-8").getBytes("UTF-8")
                  httpPost.addHeader(
                    "X-Hub-Signature",
                    XHub.generateHeaderXHubToken(
                      XHubConverter.HEXA_LOWERCASE,
                      XHubDigest.SHA1,
                      webHook.token.get,
                      contentAsBytes
                    )
                  )
                }
              }
              case WebHookContentType.JSON => {
                httpPost.setEntity(
                  EntityBuilder.create().setContentType(ContentType.APPLICATION_JSON).setText(json).build()
                )
                if (webHook.token.exists(_.trim.nonEmpty)) {
                  httpPost.addHeader(
                    "X-Hub-Signature",
                    XHub.generateHeaderXHubToken(
                      XHubConverter.HEXA_LOWERCASE,
                      XHubDigest.SHA1,
                      webHook.token.orNull,
                      json.getBytes("UTF-8")
                    )
                  )
                }
              }
            }

            val res = httpClient.execute(httpPost)
            httpPost.releaseConnection()
            logger.debug(s"end web hook invocation for ${webHook}")
            res
          } catch {
            case e: Throwable => {
              if (!reqPromise.isCompleted) {
                reqPromise.failure(e)
              }
              throw e
            }
          }
        }
        f.onComplete {
          case Success(_) => logger.debug(s"Success: web hook request to ${webHook.url}")
          case Failure(t) => logger.error(s"Failed: web hook request to ${webHook.url}", t)
        }
        (webHook, json, reqPromise.future, f)
      }
    } else {
      Nil
    }
  }
}

trait WebHookPullRequestService extends WebHookService {
  self: AccountService with RepositoryService with PullRequestService with IssuesService =>

  import WebHookService._
  // https://developer.github.com/v3/activity/events/types/#issuesevent
  def callIssuesWebHook(
    action: String,
    repository: RepositoryService.RepositoryInfo,
    issue: Issue,
    sender: Account,
    settings: SystemSettings
  )(implicit s: Session, context: JsonFormat.Context): Unit = {
    callWebHookOf(repository.owner, repository.name, WebHook.Issues, settings) {
      val users =
        getAccountsByUserNames(Set(repository.owner, issue.openedUserName) ++ issue.assignedUserName, Set(sender))
      for {
        repoOwner <- users.get(repository.owner)
        issueUser <- users.get(issue.openedUserName)
      } yield {
        WebHookIssuesPayload(
          action = action,
          number = issue.issueId,
          repository = ApiRepository(repository, ApiUser(repoOwner)),
          issue = ApiIssue(
            issue,
            RepositoryName(repository),
            ApiUser(issueUser),
            issue.assignedUserName.flatMap(users.get(_)).map(ApiUser(_)),
            getIssueLabels(repository.owner, repository.name, issue.issueId)
              .map(ApiLabel(_, RepositoryName(repository)))
          ),
          sender = ApiUser(sender)
        )
      }
    }
  }

  def callPullRequestWebHook(
    action: String,
    repository: RepositoryService.RepositoryInfo,
    issueId: Int,
    sender: Account,
    settings: SystemSettings
  )(implicit s: Session, c: JsonFormat.Context): Unit = {
    import WebHookService._
    callWebHookOf(repository.owner, repository.name, WebHook.PullRequest, settings) {
      for {
        (issue, pullRequest) <- getPullRequest(repository.owner, repository.name, issueId)
        users = getAccountsByUserNames(
          Set(repository.owner, pullRequest.requestUserName, issue.openedUserName),
          Set(sender)
        )
        baseOwner <- users.get(repository.owner)
        headOwner <- users.get(pullRequest.requestUserName)
        issueUser <- users.get(issue.openedUserName)
        assignee = issue.assignedUserName.flatMap { userName =>
          getAccountByUserName(userName, false)
        }
        headRepo <- getRepository(pullRequest.requestUserName, pullRequest.requestRepositoryName)
        labels = getIssueLabels(repository.owner, repository.name, issue.issueId)
          .map(ApiLabel(_, RepositoryName(repository)))
      } yield {
        WebHookPullRequestPayload(
          action = action,
          issue = issue,
          issueUser = issueUser,
          assignee = assignee,
          pullRequest = pullRequest,
          headRepository = headRepo,
          headOwner = headOwner,
          baseRepository = repository,
          baseOwner = baseOwner,
          labels = labels,
          sender = sender,
          mergedComment = getMergedComment(repository.owner, repository.name, issueId)
        )
      }
    }
  }

  /** @return Map[(issue, issueUser, pullRequest, baseOwner, headOwner), webHooks] */
  def getPullRequestsByRequestForWebhook(userName: String, repositoryName: String, branch: String)(
    implicit s: Session
  ): Map[(Issue, Account, PullRequest, Account, Account), List[RepositoryWebHook]] =
    (for {
      is <- Issues if is.closed === false.bind
      pr <- PullRequests if pr.byPrimaryKey(is.userName, is.repositoryName, is.issueId)
      if pr.requestUserName === userName.bind
      if pr.requestRepositoryName === repositoryName.bind
      if pr.requestBranch === branch.bind
      bu <- Accounts if bu.userName === pr.userName
      ru <- Accounts if ru.userName === pr.requestUserName
      iu <- Accounts if iu.userName === is.openedUserName
      wh <- RepositoryWebHooks if wh.byRepository(is.userName, is.repositoryName)
      wht <- RepositoryWebHookEvents
      if wht.event === WebHook.PullRequest.asInstanceOf[WebHook.Event].bind && wht.byRepositoryWebHook(wh)
    } yield {
      ((is, iu, pr, bu, ru), wh)
    }).list.groupBy(_._1).map { case (k, v) => (k, v.map(_._2)) }

  def callPullRequestWebHookByRequestBranch(
    action: String,
    requestRepository: RepositoryService.RepositoryInfo,
    requestBranch: String,
    sender: Account,
    settings: SystemSettings
  )(implicit s: Session, c: JsonFormat.Context): Unit = {
    import WebHookService._
    for {
      ((issue, issueUser, pullRequest, baseOwner, headOwner), webHooks) <- getPullRequestsByRequestForWebhook(
        requestRepository.owner,
        requestRepository.name,
        requestBranch
      )
      assignee = issue.assignedUserName.flatMap { userName =>
        getAccountByUserName(userName, false)
      }
      baseRepo <- getRepository(pullRequest.userName, pullRequest.repositoryName)
      labels = getIssueLabels(pullRequest.userName, pullRequest.repositoryName, issue.issueId)
        .map(ApiLabel(_, RepositoryName(pullRequest.userName, pullRequest.repositoryName)))
    } yield {
      val payload = WebHookPullRequestPayload(
        action = action,
        issue = issue,
        issueUser = issueUser,
        assignee = assignee,
        pullRequest = pullRequest,
        headRepository = requestRepository,
        headOwner = headOwner,
        baseRepository = baseRepo,
        baseOwner = baseOwner,
        labels = labels,
        sender = sender,
        mergedComment = getMergedComment(baseRepo.owner, baseRepo.name, issue.issueId)
      )

      callWebHook(WebHook.PullRequest, webHooks, payload, settings)
    }
  }

}

trait WebHookPullRequestReviewCommentService extends WebHookService {
  self: AccountService with RepositoryService with PullRequestService with IssuesService with CommitsService =>
  def callPullRequestReviewCommentWebHook(
    action: String,
    comment: CommitComment,
    repository: RepositoryService.RepositoryInfo,
    issue: Issue,
    pullRequest: PullRequest,
    sender: Account,
    settings: SystemSettings
  )(implicit s: Session, c: JsonFormat.Context): Unit = {
    import WebHookService._
    callWebHookOf(repository.owner, repository.name, WebHook.PullRequestReviewComment, settings) {
      val users =
        getAccountsByUserNames(Set(repository.owner, pullRequest.requestUserName, issue.openedUserName), Set(sender))
      for {
        baseOwner <- users.get(repository.owner)
        headOwner <- users.get(pullRequest.requestUserName)
        issueUser <- users.get(issue.openedUserName)
        assignee = issue.assignedUserName.flatMap { userName =>
          getAccountByUserName(userName, false)
        }
        headRepo <- getRepository(pullRequest.requestUserName, pullRequest.requestRepositoryName)
        labels = getIssueLabels(pullRequest.userName, pullRequest.repositoryName, issue.issueId)
          .map(ApiLabel(_, RepositoryName(pullRequest.userName, pullRequest.repositoryName)))
      } yield {
        WebHookPullRequestReviewCommentPayload(
          action = action,
          comment = comment,
          issue = issue,
          issueUser = issueUser,
          assignee = assignee,
          pullRequest = pullRequest,
          headRepository = headRepo,
          headOwner = headOwner,
          baseRepository = repository,
          baseOwner = baseOwner,
          labels = labels,
          sender = sender,
          mergedComment = getMergedComment(repository.owner, repository.name, issue.issueId)
        )
      }
    }
  }
}

trait WebHookIssueCommentService extends WebHookPullRequestService {
  self: AccountService with RepositoryService with PullRequestService with IssuesService =>

  import WebHookService._
  def callIssueCommentWebHook(
    repository: RepositoryService.RepositoryInfo,
    issue: Issue,
    issueCommentId: Int,
    sender: Account,
    settings: SystemSettings
  )(implicit s: Session, c: JsonFormat.Context): Unit = {
    callWebHookOf(repository.owner, repository.name, WebHook.IssueComment, settings) {
      for {
        issueComment <- getComment(repository.owner, repository.name, issueCommentId.toString())
        users = getAccountsByUserNames(
          Set(issue.openedUserName, repository.owner, issueComment.commentedUserName) ++ issue.assignedUserName,
          Set(sender)
        )
        issueUser <- users.get(issue.openedUserName)
        repoOwner <- users.get(repository.owner)
        commenter <- users.get(issueComment.commentedUserName)
        assignedUser = issue.assignedUserName.flatMap(users.get(_))
        labels = getIssueLabels(repository.owner, repository.name, issue.issueId)
      } yield {
        WebHookIssueCommentPayload(
          issue = issue,
          issueUser = issueUser,
          comment = issueComment,
          commentUser = commenter,
          repository = repository,
          repositoryUser = repoOwner,
          assignedUser = assignedUser,
          sender = sender,
          labels = labels
        )
      }
    }
  }
}

object WebHookService {
  trait WebHookPayload

  // https://developer.github.com/v3/activity/events/types/#createevent
  case class WebHookCreatePayload(
    sender: ApiUser,
    description: String,
    ref: String,
    ref_type: String,
    master_branch: String,
    repository: ApiRepository
  ) extends FieldSerializable
      with WebHookPayload {
    val pusher_type = "user"
  }

  object WebHookCreatePayload {

    def apply(
      sender: Account,
      repositoryInfo: RepositoryInfo,
      repositoryOwner: Account,
      ref: String,
      refType: String
    ): WebHookCreatePayload =
      WebHookCreatePayload(
        sender = ApiUser(sender),
        ref = ref,
        ref_type = refType,
        description = repositoryInfo.repository.description.getOrElse(""),
        master_branch = repositoryInfo.repository.defaultBranch,
        repository = ApiRepository(repositoryInfo, repositoryOwner)
      )
  }

  // https://developer.github.com/v3/activity/events/types/#pushevent
  case class WebHookPushPayload(
    pusher: ApiPusher,
    sender: ApiUser,
    ref: String,
    before: String,
    after: String,
    commits: List[ApiCommit],
    repository: ApiRepository
  ) extends FieldSerializable
      with WebHookPayload {
    val compare = commits.size match {
      case 0                            => ApiPath(s"/${repository.full_name}") // maybe test hook on un-initialized repository
      case 1                            => ApiPath(s"/${repository.full_name}/commit/${after}")
      case _ if before.forall(_ == '0') => ApiPath(s"/${repository.full_name}/compare/${commits.head.id}^...${after}")
      case _                            => ApiPath(s"/${repository.full_name}/compare/${before}...${after}")
    }
    val head_commit = commits.lastOption
  }

  object WebHookPushPayload {
    def apply(
      git: Git,
      sender: Account,
      refName: String,
      repositoryInfo: RepositoryInfo,
      commits: List[CommitInfo],
      repositoryOwner: Account,
      newId: ObjectId,
      oldId: ObjectId
    ): WebHookPushPayload =
      WebHookPushPayload(
        pusher = ApiPusher(sender),
        sender = ApiUser(sender),
        ref = refName,
        before = ObjectId.toString(oldId),
        after = ObjectId.toString(newId),
        commits = commits.map { commit =>
          ApiCommit(git, RepositoryName(repositoryInfo), commit)
        },
        repository = ApiRepository(repositoryInfo, repositoryOwner)
      )

    def createDummyPayload(sender: Account): WebHookPushPayload =
      WebHookPushPayload(
        pusher = ApiPusher(sender),
        sender = ApiUser(sender),
        ref = "refs/heads/master",
        before = "adc83b19e793491b1c6ea0fd8b46cd9f32e592fc",
        after = "adc83b19e793491b1c6ea0fd8b46cd9f32e592fc",
        commits = List.empty,
        repository = ApiRepository.forDummyPayload(ApiUser(sender))
      )
  }

  // https://developer.github.com/v3/activity/events/types/#issuesevent
  case class WebHookIssuesPayload(
    action: String,
    number: Int,
    repository: ApiRepository,
    issue: ApiIssue,
    sender: ApiUser
  ) extends WebHookPayload

  // https://developer.github.com/v3/activity/events/types/#pullrequestevent
  case class WebHookPullRequestPayload(
    action: String,
    number: Int,
    repository: ApiRepository,
    pull_request: ApiPullRequest,
    sender: ApiUser
  ) extends WebHookPayload

  object WebHookPullRequestPayload {
    def apply(
      action: String,
      issue: Issue,
      issueUser: Account,
      assignee: Option[Account],
      pullRequest: PullRequest,
      headRepository: RepositoryInfo,
      headOwner: Account,
      baseRepository: RepositoryInfo,
      baseOwner: Account,
      labels: List[ApiLabel],
      sender: Account,
      mergedComment: Option[(IssueComment, Account)]
    ): WebHookPullRequestPayload = {

      val headRepoPayload = ApiRepository(headRepository, headOwner)
      val baseRepoPayload = ApiRepository(baseRepository, baseOwner)
      val senderPayload = ApiUser(sender)
      val pr = ApiPullRequest(
        issue = issue,
        pullRequest = pullRequest,
        headRepo = headRepoPayload,
        baseRepo = baseRepoPayload,
        user = ApiUser(issueUser),
        labels = labels,
        assignee = assignee.map(ApiUser.apply),
        mergedComment = mergedComment
      )

      WebHookPullRequestPayload(
        action = action,
        number = issue.issueId,
        repository = pr.base.repo,
        pull_request = pr,
        sender = senderPayload
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

  object WebHookIssueCommentPayload {
    def apply(
      issue: Issue,
      issueUser: Account,
      comment: IssueComment,
      commentUser: Account,
      repository: RepositoryInfo,
      repositoryUser: Account,
      assignedUser: Option[Account],
      sender: Account,
      labels: List[Label]
    ): WebHookIssueCommentPayload =
      WebHookIssueCommentPayload(
        action = "created",
        repository = ApiRepository(repository, repositoryUser),
        issue = ApiIssue(
          issue,
          RepositoryName(repository),
          ApiUser(issueUser),
          assignedUser.map(ApiUser(_)),
          labels.map(ApiLabel(_, RepositoryName(repository)))
        ),
        comment =
          ApiComment(comment, RepositoryName(repository), issue.issueId, ApiUser(commentUser), issue.isPullRequest),
        sender = ApiUser(sender)
      )
  }

  // https://developer.github.com/v3/activity/events/types/#pullrequestreviewcommentevent
  case class WebHookPullRequestReviewCommentPayload(
    action: String,
    comment: ApiPullRequestReviewComment,
    pull_request: ApiPullRequest,
    repository: ApiRepository,
    sender: ApiUser
  ) extends WebHookPayload

  object WebHookPullRequestReviewCommentPayload {
    def apply(
      action: String,
      comment: CommitComment,
      issue: Issue,
      issueUser: Account,
      assignee: Option[Account],
      pullRequest: PullRequest,
      headRepository: RepositoryInfo,
      headOwner: Account,
      baseRepository: RepositoryInfo,
      baseOwner: Account,
      labels: List[ApiLabel],
      sender: Account,
      mergedComment: Option[(IssueComment, Account)]
    ): WebHookPullRequestReviewCommentPayload = {
      val headRepoPayload = ApiRepository(headRepository, headOwner)
      val baseRepoPayload = ApiRepository(baseRepository, baseOwner)
      val senderPayload = ApiUser(sender)

      WebHookPullRequestReviewCommentPayload(
        action = action,
        comment = ApiPullRequestReviewComment(
          comment = comment,
          commentedUser = senderPayload,
          repositoryName = RepositoryName(baseRepository),
          issueId = issue.issueId
        ),
        pull_request = ApiPullRequest(
          issue = issue,
          pullRequest = pullRequest,
          headRepo = headRepoPayload,
          baseRepo = baseRepoPayload,
          user = ApiUser(issueUser),
          labels = labels,
          assignee = assignee.map(ApiUser.apply),
          mergedComment = mergedComment
        ),
        repository = baseRepoPayload,
        sender = senderPayload
      )
    }
  }

  // https://developer.github.com/v3/activity/events/types/#gollumevent
  case class WebHookGollumPayload(
    pages: Seq[WebHookGollumPagePayload],
    repository: ApiRepository,
    sender: ApiUser
  ) extends WebHookPayload

  case class WebHookGollumPagePayload(
    page_name: String,
    title: String,
    summary: Option[String] = None,
    action: String, // created or edited
    sha: String, // SHA of the latest commit
    html_url: ApiPath
  )

  object WebHookGollumPayload {
    def apply(
      action: String,
      pageName: String,
      sha: String,
      repository: RepositoryInfo,
      repositoryUser: Account,
      sender: Account
    ): WebHookGollumPayload = apply(Seq((action, pageName, sha)), repository, repositoryUser, sender)

    def apply(
      pages: Seq[(String, String, String)],
      repository: RepositoryInfo,
      repositoryUser: Account,
      sender: Account
    ): WebHookGollumPayload = {
      WebHookGollumPayload(
        pages = pages.map {
          case (action, pageName, sha) =>
            WebHookGollumPagePayload(
              action = action,
              page_name = pageName,
              title = pageName,
              sha = sha,
              html_url = ApiPath(s"/${RepositoryName(repository).fullName}/wiki/${StringUtil.urlDecode(pageName)}")
            )
        },
        repository = ApiRepository(repository, repositoryUser),
        sender = ApiUser(sender)
      )
    }
  }

}
