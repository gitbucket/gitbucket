package model

import scala.slick.driver.H2Driver.simple._
import model.{BaseTable => Table}

object IssueComments extends Table[IssueComment]("ISSUE_COMMENT") with Functions {
  def issueId = column[Int]("ISSUE_ID")
  def commentId = column[Int]("COMMENT_ID", O AutoInc)
  def commentedUserName = column[String]("COMMENTED_USER_NAME")
  def content = column[String]("CONTENT")
  def registeredDate = column[java.util.Date]("REGISTERED_DATE")
  def updatedDate = column[java.util.Date]("UPDATED_DATE")
  def * = base ~ issueId ~ commentId ~ commentedUserName ~ content ~ registeredDate ~ updatedDate <> (IssueComment, IssueComment.unapply _)

  def autoInc = base ~ issueId ~ commentedUserName ~ content ~ registeredDate ~ updatedDate returning commentId
}

case class IssueComment(
    userName: String,
    repositoryName: String,
    issueId: Int,
    commentId: Int,
    commentedUserName: String,
    content: String,
    registeredDate: java.util.Date,
    updatedDate: java.util.Date
)