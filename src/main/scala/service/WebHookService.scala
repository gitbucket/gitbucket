package service

import scala.slick.driver.H2Driver.simple._
import Database.threadLocalSession

import model._
import org.slf4j.LoggerFactory
import service.RepositoryService.RepositoryInfo
import util.JGitUtil
import org.eclipse.jgit.diff.DiffEntry
import util.JGitUtil.CommitInfo
import org.eclipse.jgit.api.Git
import org.apache.http.message.BasicNameValuePair
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.protocol.HTTP
import org.apache.http.NameValuePair

trait WebHookService {
  import WebHookService._

  private val logger = LoggerFactory.getLogger(classOf[WebHookService])

  def getWebHookURLs(owner: String, repository: String): List[WebHook] =
    Query(WebHooks).filter(_.byRepository(owner, repository)).sortBy(_.url).list

  def addWebHookURL(owner: String, repository: String, url :String): Unit =
    WebHooks.insert(WebHook(owner, repository, url))

  def deleteWebHookURL(owner: String, repository: String, url :String): Unit =
    Query(WebHooks).filter(_.byPrimaryKey(owner, repository, url)).delete

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
        val f = future {
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
          val commitUrl = repositoryInfo.url.replaceFirst("/git/", "/").replaceFirst("\\.git$", "") + "/commit/" + commit.id

          WebHookCommit(
            id        = commit.id,
            message   = commit.fullMessage,
            timestamp = commit.time.toString,
            url       = commitUrl,
            added     = diffs._1.collect { case x if(x.changeType == DiffEntry.ChangeType.ADD)    => x.newPath },
            removed   = diffs._1.collect { case x if(x.changeType == DiffEntry.ChangeType.DELETE) => x.oldPath },
            modified  = diffs._1.collect { case x if(x.changeType != DiffEntry.ChangeType.ADD &&
              x.changeType != DiffEntry.ChangeType.DELETE) => x.newPath },
            author    = WebHookUser(
              name  = commit.committer,
              email = commit.mailAddress
            )
          )
        }.toList,
        WebHookRepository(
          name        = repositoryInfo.name,
          url         = repositoryInfo.url,
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
