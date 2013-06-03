package model

import scala.slick.driver.H2Driver.simple._

object Repositories extends Table[Repository]("REPOSITORY") {
  def repositoryName= column[String]("REPOSITORY_NAME", O PrimaryKey)
  def userName = column[String]("USER_NAME", O PrimaryKey)
  def repositoryType = column[Int]("REPOSITORY_TYPE") // TODO should be sealed?
  def description = column[String]("DESCRIPTION")
  def defaultBranch = column[String]("DEFAULT_BRANCH")
  def registeredDate = column[java.sql.Date]("REGISTERED_DATE")	// TODO convert java.util.Date later
  def updatedDate = column[java.sql.Date]("UPDATED_DATE")
  def lastActivityDate = column[java.sql.Date]("LAST_ACTIVITY_DATE")
  def * = repositoryName ~ userName ~ repositoryType ~ description.? ~ defaultBranch ~ registeredDate ~ updatedDate ~ lastActivityDate <> (Repository, Repository.unapply _)
//  def ins = repositoryName ~ userName ~ repositoryType ~ description.? ~ defaultBranch ~ registeredDate ~ updatedDate ~ lastActivityDate <> ({ t => Project(None, t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8)}, { (o: Project) => Some((o.projectName, o.userId, o.projectType, o.description, o.defaultBranch, o.registeredDate, o.updatedDate, o.lastActivityDate))})
}

case class Repository(
  repositoryName: String,
  userName: String,
  repositoryType: Int,
  description: Option[String],
  defaultBranch: String,
  registeredDate: java.sql.Date,
  updatedDate: java.sql.Date,
  lastActivityDate: java.sql.Date
)
