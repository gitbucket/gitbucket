package model

trait IssueComponent extends IssueTemplateComponent with MilestoneTemplateComponent { self: Profile =>
  import profile.simple._

  object IssueId extends Table[(String, String, Int)]("ISSUE_ID") with IssueTemplate {
    def * = userName ~ repositoryName ~ issueId
    def byPrimaryKey(owner: String, repository: String) = byRepository(owner, repository)
  }

  object IssueOutline extends Table[(String, String, Int, Int)]("ISSUE_OUTLINE_VIEW") with IssueTemplate {
    def commentCount = column[Int]("COMMENT_COUNT")
    def * = userName ~ repositoryName ~ issueId ~ commentCount
  }

  object Issues extends Table[Issue]("ISSUE") with IssueTemplate with MilestoneTemplate {
    def openedUserName = column[String]("OPENED_USER_NAME")
    def assignedUserName = column[String]("ASSIGNED_USER_NAME")
    def title = column[String]("TITLE")
    def content = column[String]("CONTENT")
    def closed = column[Boolean]("CLOSED")
    def registeredDate = column[java.util.Date]("REGISTERED_DATE")
    def updatedDate = column[java.util.Date]("UPDATED_DATE")
    def pullRequest = column[Boolean]("PULL_REQUEST")
    def * = userName ~ repositoryName ~ issueId ~ openedUserName ~ milestoneId.? ~ assignedUserName.? ~ title ~ content.? ~ closed ~ registeredDate ~ updatedDate ~ pullRequest <> (Issue, Issue.unapply _)

    def byPrimaryKey(owner: String, repository: String, issueId: Int) = byIssue(owner, repository, issueId)
  }
}

case class Issue(
    userName: String,
    repositoryName: String,
    issueId: Int,
    openedUserName: String,
    milestoneId: Option[Int],
    assignedUserName: Option[String],
    title: String,
    content: Option[String],
    closed: Boolean,
    registeredDate: java.util.Date,
    updatedDate: java.util.Date,
    isPullRequest: Boolean)