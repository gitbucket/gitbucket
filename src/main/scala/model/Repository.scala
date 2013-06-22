package model

import scala.slick.driver.H2Driver.simple._

object Repositories extends Table[Repository]("REPOSITORY") {
  def repositoryName= column[String]("REPOSITORY_NAME", O PrimaryKey)
  def userName = column[String]("USER_NAME", O PrimaryKey)
  def isPrivate = column[Boolean]("PRIVATE")
  def description = column[String]("DESCRIPTION")
  def defaultBranch = column[String]("DEFAULT_BRANCH")
  def registeredDate = column[java.sql.Timestamp]("REGISTERED_DATE")	// TODO convert java.util.Date later
  def updatedDate = column[java.sql.Timestamp]("UPDATED_DATE")
  def lastActivityDate = column[java.sql.Timestamp]("LAST_ACTIVITY_DATE")
  def * = repositoryName ~ userName ~ isPrivate ~ description.? ~ defaultBranch ~ registeredDate ~ updatedDate ~ lastActivityDate <> (Repository, Repository.unapply _)
}

case class Repository(
  repositoryName: String,
  userName: String,
  isPrivate: Boolean,
  description: Option[String],
  defaultBranch: String,
  registeredDate: java.sql.Timestamp,
  updatedDate: java.sql.Timestamp,
  lastActivityDate: java.sql.Timestamp
)
