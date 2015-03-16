package gitbucket.core.model

trait RepositoryComponent extends TemplateComponent { self: Profile =>
  import profile.simple._
  import self._

  lazy val Repositories = TableQuery[Repositories]

  class Repositories(tag: Tag) extends Table[Repository](tag, "REPOSITORY") with BasicTemplate {
    val isPrivate = column[Boolean]("PRIVATE")
    val description = column[String]("DESCRIPTION")
    val defaultBranch = column[String]("DEFAULT_BRANCH")
    val registeredDate = column[java.util.Date]("REGISTERED_DATE")
    val updatedDate = column[java.util.Date]("UPDATED_DATE")
    val lastActivityDate = column[java.util.Date]("LAST_ACTIVITY_DATE")
    val originUserName = column[String]("ORIGIN_USER_NAME")
    val originRepositoryName = column[String]("ORIGIN_REPOSITORY_NAME")
    val parentUserName = column[String]("PARENT_USER_NAME")
    val parentRepositoryName = column[String]("PARENT_REPOSITORY_NAME")
    def * = (userName, repositoryName, isPrivate, description.?, defaultBranch, registeredDate, updatedDate, lastActivityDate, originUserName.?, originRepositoryName.?, parentUserName.?, parentRepositoryName.?) <> (Repository.tupled, Repository.unapply)

    def byPrimaryKey(owner: String, repository: String) = byRepository(owner, repository)
  }
}

case class Repository(
  userName: String,
  repositoryName: String,
  isPrivate: Boolean,
  description: Option[String],
  defaultBranch: String,
  registeredDate: java.util.Date,
  updatedDate: java.util.Date,
  lastActivityDate: java.util.Date,
  originUserName: Option[String],
  originRepositoryName: Option[String],
  parentUserName: Option[String],
  parentRepositoryName: Option[String]
)
