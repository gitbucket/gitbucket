package model

trait IssueLabelComponent extends IssueTemplateComponent with LabelTemplateComponent { self: Profile =>
  import profile.simple._

  object IssueLabels extends Table[IssueLabel]("ISSUE_LABEL") with IssueTemplate with LabelTemplate {
    def * = userName ~ repositoryName ~ issueId ~ labelId <> (IssueLabel, IssueLabel.unapply _)
    def byPrimaryKey(owner: String, repository: String, issueId: Int, labelId: Int) =
      byIssue(owner, repository, issueId) && (this.labelId is labelId.bind)
  }
}

case class IssueLabel(
  userName: String,
  repositoryName: String,
  issueId: Int,
  labelId: Int)