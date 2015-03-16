package gitbucket.core.model

trait Comment {
  val commentedUserName: String
  val registeredDate: java.util.Date
}

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

case class IssueComment (
  userName: String,
  repositoryName: String,
  issueId: Int,
  commentId: Int = 0,
  action: String,
  commentedUserName: String,
  content: String,
  registeredDate: java.util.Date,
  updatedDate: java.util.Date
) extends Comment

trait CommitCommentComponent extends TemplateComponent { self: Profile =>
  import profile.simple._
  import self._

  lazy val CommitComments = new TableQuery(tag => new CommitComments(tag)){
    def autoInc = this returning this.map(_.commentId)
  }

  class CommitComments(tag: Tag) extends Table[CommitComment](tag, "COMMIT_COMMENT") with CommitTemplate {
    val commentId = column[Int]("COMMENT_ID", O AutoInc)
    val commentedUserName = column[String]("COMMENTED_USER_NAME")
    val content = column[String]("CONTENT")
    val fileName = column[Option[String]]("FILE_NAME")
    val oldLine = column[Option[Int]]("OLD_LINE_NUMBER")
    val newLine = column[Option[Int]]("NEW_LINE_NUMBER")
    val registeredDate = column[java.util.Date]("REGISTERED_DATE")
    val updatedDate = column[java.util.Date]("UPDATED_DATE")
    val pullRequest = column[Boolean]("PULL_REQUEST")
    def * = (userName, repositoryName, commitId, commentId, commentedUserName, content, fileName, oldLine, newLine, registeredDate, updatedDate, pullRequest) <> (CommitComment.tupled, CommitComment.unapply)

    def byPrimaryKey(commentId: Int) = this.commentId === commentId.bind
  }
}

case class CommitComment(
  userName: String,
  repositoryName: String,
  commitId: String,
  commentId: Int = 0,
  commentedUserName: String,
  content: String,
  fileName: Option[String],
  oldLine: Option[Int],
  newLine: Option[Int],
  registeredDate: java.util.Date,
  updatedDate: java.util.Date,
  pullRequest: Boolean
 ) extends Comment
