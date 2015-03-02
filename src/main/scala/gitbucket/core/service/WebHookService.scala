package gitbucket.core.service

import gitbucket.core.model.{WebHook, Account}
import gitbucket.core.model.Profile._
import gitbucket.core.service.RepositoryService.RepositoryInfo
import gitbucket.core.util.JGitUtil
import profile.simple._
import org.slf4j.LoggerFactory
import RepositoryService.RepositoryInfo
import org.eclipse.jgit.diff.DiffEntry
import JGitUtil.CommitInfo
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

  def callWebHook(owner: String, repository: String, webHookURLs: List[WebHook], payload: WebHookPayload): Unit = {
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

  case class WebHookPayload(
    pusher: WebHookUser,
    ref: String,
    commits: List[WebHookCommit],
    repository: WebHookRepository)

  object WebHookPayload {
    def apply(git: Git, pusher: Account, refName: String, repositoryInfo: RepositoryInfo,
              commits: List[CommitInfo], repositoryOwner: Account): WebHookPayload =
      WebHookPayload(
        WebHookUser(pusher.fullName, pusher.mailAddress),
        refName,
        commits.map { commit =>
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
            author    = WebHookUser(
              name  = commit.committerName,
              email = commit.committerEmailAddress
            )
          )
        },
        WebHookRepository(
          name        = repositoryInfo.name,
          url         = repositoryInfo.httpUrl,
          description = repositoryInfo.repository.description.getOrElse(""),
          watchers    = 0,
          forks       = repositoryInfo.forkedCount,
          `private`   = repositoryInfo.repository.isPrivate,
          owner = WebHookUser(
            name  = repositoryOwner.userName,
            email = repositoryOwner.mailAddress
          )
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
    author: WebHookUser)

  case class WebHookRepository(
    name: String,
    url: String,
    description: String,
    watchers: Int,
    forks: Int,
    `private`: Boolean,
    owner: WebHookUser)

  case class WebHookUser(
    name: String,
    email: String)

}
