package gitbucket.core.model

trait IssueComponent extends TemplateComponent { self: Profile =>
  import profile.simple._
  import self._

  lazy val IssueId = TableQuery[IssueId]
  lazy val IssueOutline = TableQuery[IssueOutline]
  lazy val Issues = TableQuery[Issues]

  class IssueId(tag: Tag) extends Table[(String, String, Int)](tag, "ISSUE_ID") with IssueTemplate {
    def * = (userName, repositoryName, issueId)
    def byPrimaryKey(owner: String, repository: String) = byRepository(owner, repository)
  }

  class IssueOutline(tag: Tag) extends Table[(String, String, Int, Int)](tag, "ISSUE_OUTLINE_VIEW") with IssueTemplate {
    val commentCount = column[Int]("COMMENT_COUNT")
    def * = (userName, repositoryName, issueId, commentCount)
  }

  class Issues(tag: Tag) extends Table[Issue](tag, "ISSUE") with IssueTemplate with MilestoneTemplate {
    val openedUserName = column[String]("OPENED_USER_NAME")
    val assignedUserName = column[String]("ASSIGNED_USER_NAME")
    val title = column[String]("TITLE")
    val content = column[String]("CONTENT")
    val closed = column[Boolean]("CLOSED")
    val registeredDate = column[java.util.Date]("REGISTERED_DATE")
    val updatedDate = column[java.util.Date]("UPDATED_DATE")
    val pullRequest = column[Boolean]("PULL_REQUEST")
    def * = (userName, repositoryName, issueId, openedUserName, milestoneId.?, assignedUserName.?, title, content.?, closed, registeredDate, updatedDate, pullRequest) <> (Issue.tupled, Issue.unapply)

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
  isPullRequest: Boolean
)
