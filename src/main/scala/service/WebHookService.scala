package service

import scala.slick.driver.H2Driver.simple._
import Database.threadLocalSession

import model._

trait WebHookService {

  def getWebHookURLs(owner: String, repository: String): List[WebHook] =
    Query(WebHooks).filter(_.byRepository(owner, repository)).sortBy(_.url).list

  def addWebHookURL(owner: String, repository: String, url :String): Unit =
    WebHooks.insert(WebHook(owner, repository, url))

  def deleteWebHookURL(owner: String, repository: String, url :String): Unit =
    Query(WebHooks).filter(_.byPrimaryKey(owner, repository, url)).delete

}

object WebHookService {

  case class WebHookPayload(
    before: String,
    after: String,
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
    pledgie: String,
    description: String,
    homepage: String,
    watchers: Int,
    forks: Int,
    `private`: Boolean,
    owner: WebHookUser)

  case class WebHookUser(
    name: String,
    email: String)

}
