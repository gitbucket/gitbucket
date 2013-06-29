package model

import scala.slick.driver.H2Driver.simple._

object Collaborators extends Table[Collaborator]("COLLABORATOR") with BasicTemplate {
  def collaboratorName = column[String]("COLLABORATOR_NAME")
  def * = userName ~ repositoryName ~ collaboratorName <> (Collaborator, Collaborator.unapply _)

  def byPrimaryKey(owner: String, repository: String, collaborator: String) =
    byRepository(owner, repository) && (collaboratorName is collaborator.bind)
}

case class Collaborator(
  userName: String,
  repositoryName: String,
  collaboratorName: String
)
