package model

trait RepositoryComponent extends BasicTemplateComponent { self: Profile =>
  import profile.simple._

  object Repositories extends Table[Repository]("REPOSITORY") with BasicTemplate {
    def isPrivate = column[Boolean]("PRIVATE")
    def description = column[String]("DESCRIPTION")
    def defaultBranch = column[String]("DEFAULT_BRANCH")
    def registeredDate = column[java.util.Date]("REGISTERED_DATE")
    def updatedDate = column[java.util.Date]("UPDATED_DATE")
    def lastActivityDate = column[java.util.Date]("LAST_ACTIVITY_DATE")
    def originUserName = column[String]("ORIGIN_USER_NAME")
    def originRepositoryName = column[String]("ORIGIN_REPOSITORY_NAME")
    def parentUserName = column[String]("PARENT_USER_NAME")
    def parentRepositoryName = column[String]("PARENT_REPOSITORY_NAME")
    def * = userName ~ repositoryName ~ isPrivate ~ description.? ~ defaultBranch ~ registeredDate ~ updatedDate ~ lastActivityDate ~ originUserName.? ~ originRepositoryName.? ~ parentUserName.? ~ parentRepositoryName.? <> (Repository, Repository.unapply _)

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
