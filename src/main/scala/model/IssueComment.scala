package model

trait IssueCommentComponent extends TemplateComponent { self: Profile =>
  import profile.simple._
  import self._

  lazy val IssueComments = new TableQuery(tag => new IssueComments(tag)){
    def autoInc = this returning this.map(_.commentId)
  }

  class IssueComments(tag: Tag) extends Table[IssueComment](tag, "ISSUE_COMMENT") with IssueTemplate {
    val commentId = column[Int]("COMMENT_ID", O AutoInc)
    val action = column[String]("ACTION")
    val commentedUserName = column[String]("COMMENTED_USER_NAME")
    val content = column[String]("CONTENT")
    val registeredDate = column[java.util.Date]("REGISTERED_DATE")
    val updatedDate = column[java.util.Date]("UPDATED_DATE")
    def * = (userName, repositoryName, issueId, commentId, action, commentedUserName, content, registeredDate, updatedDate) <> (IssueComment.tupled, IssueComment.unapply)

    def byPrimaryKey(commentId: Int) = this.commentId === commentId.bind
  }
}

case class IssueComment(
  userName: String,
  repositoryName: String,
  issueId: Int,
  commentId: Int = 0,
  action: String,
  commentedUserName: String,
  content: String,
  registeredDate: java.util.Date,
  updatedDate: java.util.Date
)
