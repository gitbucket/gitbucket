package model

trait IssueCommentComponent extends IssueTemplateComponent { self: Profile =>
  import profile.simple._

  object IssueComments extends Table[IssueComment]("ISSUE_COMMENT") with IssueTemplate {
    def commentId = column[Int]("COMMENT_ID", O AutoInc)
    def action = column[String]("ACTION")
    def commentedUserName = column[String]("COMMENTED_USER_NAME")
    def content = column[String]("CONTENT")
    def registeredDate = column[java.util.Date]("REGISTERED_DATE")
    def updatedDate = column[java.util.Date]("UPDATED_DATE")
    def * = userName ~ repositoryName ~ issueId ~ commentId ~ action ~ commentedUserName ~ content ~ registeredDate ~ updatedDate <> (IssueComment, IssueComment.unapply _)

    def autoInc = userName ~ repositoryName ~ issueId ~ action ~ commentedUserName ~ content ~ registeredDate ~ updatedDate returning commentId
    def byPrimaryKey(commentId: Int) = this.commentId is commentId.bind
  }
}

case class IssueComment(
    userName: String,
    repositoryName: String,
    issueId: Int,
    commentId: Int,
    action: String,
    commentedUserName: String,
    content: String,
    registeredDate: java.util.Date,
    updatedDate: java.util.Date
)