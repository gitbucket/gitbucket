package model

import scala.slick.driver.H2Driver.simple._
import model.{BaseTable => Table}

object Collaborators extends Table[Collaborator]("COLLABORATOR") {
  def collaboratorName = column[String]("COLLABORATOR_NAME")
  def * = base ~ collaboratorName <> (Collaborator, Collaborator.unapply _)
}

case class Collaborator(
  userName: String,
  repositoryName: String,
  collaboratorName: String
)
