package model

trait IssueLabelComponent extends TemplateComponent { self: Profile =>
  import profile.simple._

  lazy val IssueLabels = TableQuery[IssueLabels]

  class IssueLabels(tag: Tag) extends Table[IssueLabel](tag, "ISSUE_LABEL") with IssueTemplate with LabelTemplate {
    def * = (userName, repositoryName, issueId, labelId) <> (IssueLabel.tupled, IssueLabel.unapply)
    def byPrimaryKey(owner: String, repository: String, issueId: Int, labelId: Int) =
      byIssue(owner, repository, issueId) && (this.labelId === labelId.bind)
  }
}

case class IssueLabel(
  userName: String,
  repositoryName: String,
  issueId: Int,
  labelId: Int
)
