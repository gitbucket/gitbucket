package model

import scala.slick.driver.H2Driver.simple._

object WebHooks extends Table[WebHook]("WEB_HOOK") with BasicTemplate {
  def url = column[String]("URL")
  def * = userName ~ repositoryName ~ url <> (WebHook, WebHook.unapply _)

  def byPrimaryKey(owner: String, repository: String) = byRepository(owner, repository)
}

case class WebHook(
  userName: String,
  repositoryName: String,
  url: String
)
