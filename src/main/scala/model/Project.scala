package model

import scala.slick.driver.H2Driver.simple._

object Projects extends Table[Project]("PROJECT") {
  def projectId = column[Long]("PROJECT_ID", O AutoInc)
  def projectName= column[String]("PROJECT_NAME")
  def userId = column[Long]("USER_ID")
  def projectType = column[Int]("PROJECT_TYPE") // TODO should be sealed?
  def description = column[String]("DESCRIPTION")
  def defaultBranch = column[String]("DEFAULT_BRANCH")
  def registeredDate = column[java.sql.Date]("REGISTERED_DATE")	// TODO convert java.util.Date later
  def updatedDate = column[java.sql.Date]("UPDATED_DATE")
  def lastActivityDate = column[java.sql.Date]("LAST_LOGIN_DATE")
  def * = projectId.? ~ projectName ~ userId ~ projectType ~ description.? ~ defaultBranch ~ registeredDate ~ updatedDate ~ lastActivityDate <> (Project, Project.unapply _)
  def ins = projectName ~ userId ~ projectType ~ description.? ~ defaultBranch ~ registeredDate ~ updatedDate ~ lastActivityDate <> ({ t => Project(None, t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8)}, { (o: Project) => Some((o.projectName, o.userId, o.projectType, o.description, o.defaultBranch, o.registeredDate, o.updatedDate, o.lastActivityDate))})
}

case class Project(
  projectId: Option[Long],
  projectName: String,
  userId: Long,
  projectType: Int,
  description: Option[String],
  defaultBranch: String,
  registeredDate: java.sql.Date,
  updatedDate: java.sql.Date,
  lastActivityDate: java.sql.Date
)
