package model

import scala.slick.driver.H2Driver.simple._

object Repositories extends Table[Repository]("REPOSITORY") with Functions {
  def repositoryName= column[String]("REPOSITORY_NAME", O PrimaryKey)
  def userName = column[String]("USER_NAME", O PrimaryKey)
  def isPrivate = column[Boolean]("PRIVATE")
  def description = column[String]("DESCRIPTION")
  def defaultBranch = column[String]("DEFAULT_BRANCH")
  def registeredDate = column[java.util.Date]("REGISTERED_DATE")
  def updatedDate = column[java.util.Date]("UPDATED_DATE")
  def lastActivityDate = column[java.util.Date]("LAST_ACTIVITY_DATE")
  def * = repositoryName ~ userName ~ isPrivate ~ description.? ~ defaultBranch ~ registeredDate ~ updatedDate ~ lastActivityDate <> (Repository, Repository.unapply _)
}

case class Repository(
  repositoryName: String,
  userName: String,
  isPrivate: Boolean,
  description: Option[String],
  defaultBranch: String,
  registeredDate: java.util.Date,
  updatedDate: java.util.Date,
  lastActivityDate: java.util.Date
)
