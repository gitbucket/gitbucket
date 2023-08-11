package gitbucket.core.model

trait IssueCustomFieldComponent extends TemplateComponent { self: Profile =>
  import profile.api._
  import self._

  lazy val IssueCustomFields = TableQuery[IssueCustomFields]

  class IssueCustomFields(tag: Tag) extends Table[IssueCustomField](tag, "ISSUE_CUSTOM_FIELD") {
    val userName = column[String]("USER_NAME", O.PrimaryKey)
    val repositoryName = column[String]("REPOSITORY_NAME", O.PrimaryKey)
    val issueId = column[Int]("ISSUE_ID", O.PrimaryKey)
    val fieldId = column[Int]("FIELD_ID", O.PrimaryKey)
    val value = column[String]("VALUE")
    def * =
      (userName, repositoryName, issueId, fieldId, value).mapTo[IssueCustomField]

    def byPrimaryKey(owner: String, repository: String, issueId: Int, fieldId: Int) = {
      this.userName === owner.bind && this.repositoryName === repository.bind && this.issueId === issueId.bind && this.fieldId === fieldId.bind
    }
  }
}

case class IssueCustomField(
  userName: String,
  repositoryName: String,
  issueId: Int,
  fieldId: Int,
  value: String
)
