package service

import scala.slick.driver.H2Driver.simple._
import Database.threadLocalSession

import model._
import org.slf4j.LoggerFactory

trait WebHookService {
  import WebHookService._

  private val logger = LoggerFactory.getLogger(classOf[WebHookService])

  def getWebHookURLs(owner: String, repository: String): List[WebHook] =
    Query(WebHooks).filter(_.byRepository(owner, repository)).sortBy(_.url).list

  def addWebHookURL(owner: String, repository: String, url :String): Unit =
    WebHooks.insert(WebHook(owner, repository, url))

  def deleteWebHookURL(owner: String, repository: String, url :String): Unit =
    Query(WebHooks).filter(_.byPrimaryKey(owner, repository, url)).delete

  def callWebHook(owner: String, repository: String, payload: WebHookPayload): Unit = {
    import org.json4s._
    import org.json4s.jackson.Serialization
    import org.json4s.jackson.Serialization.{read, write}
    import org.apache.http.client.methods.HttpPost
    import org.apache.http.impl.client.DefaultHttpClient
    import scala.concurrent._
    import ExecutionContext.Implicits.global

    implicit val formats = Serialization.formats(NoTypeHints)

    val webHookURLs = getWebHookURLs(owner, repository)

    if(webHookURLs.nonEmpty){
      val json = write(payload)
      val httpClient = new DefaultHttpClient()

      webHookURLs.foreach { webHookUrl =>
        val f = future {
          val httpPost = new HttpPost(webHookUrl.url)
          httpPost.getParams.setParameter("payload", json)
          httpClient.execute(httpPost)
          httpPost.releaseConnection()
        }
        f.onSuccess {
          case s => logger.debug(s"Success: web hook request to ${webHookUrl.url}")
        }
        f.onFailure {
          case t => logger.error(s"Failed: web hook request to ${webHookUrl.url}", t)
        }
      }
    }
  }

}

object WebHookService {

  case class WebHookPayload(
    ref: String,
    commits: List[WebHookCommit],
    repository: WebHookRepository)

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
