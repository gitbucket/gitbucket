package model

import scala.slick.driver.H2Driver.simple._

object Collaborators extends Table[Collaborator]("COLLABORATOR") {
  def userName = column[String]("USER_NAME", O PrimaryKey)
  def repositoryName = column[String]("REPOSITORY_NAME")
  def collaboratorName = column[String]("COLLABORATOR_NAME")
  def * = userName ~ repositoryName ~ collaboratorName <> (Collaborator, Collaborator.unapply _)
}

case class Collaborator(
  userName: String,
  repositoryName: String,
  collaboratorName: String
)
