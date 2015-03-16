package gitbucket.core.model

trait PullRequestComponent extends TemplateComponent { self: Profile =>
  import profile.simple._

  lazy val PullRequests = TableQuery[PullRequests]

  class PullRequests(tag: Tag) extends Table[PullRequest](tag, "PULL_REQUEST") with IssueTemplate {
    val branch = column[String]("BRANCH")
    val requestUserName = column[String]("REQUEST_USER_NAME")
    val requestRepositoryName = column[String]("REQUEST_REPOSITORY_NAME")
    val requestBranch = column[String]("REQUEST_BRANCH")
    val commitIdFrom = column[String]("COMMIT_ID_FROM")
    val commitIdTo = column[String]("COMMIT_ID_TO")
    def * = (userName, repositoryName, issueId, branch, requestUserName, requestRepositoryName, requestBranch, commitIdFrom, commitIdTo) <> (PullRequest.tupled, PullRequest.unapply)

    def byPrimaryKey(userName: String, repositoryName: String, issueId: Int) = byIssue(userName, repositoryName, issueId)
    def byPrimaryKey(userName: Column[String], repositoryName: Column[String], issueId: Column[Int]) = byIssue(userName, repositoryName, issueId)
  }
}

case class PullRequest(
  userName: String,
  repositoryName: String,
  issueId: Int,
  branch: String,
  requestUserName: String,
  requestRepositoryName: String,
  requestBranch: String,
  commitIdFrom: String,
  commitIdTo: String
)
