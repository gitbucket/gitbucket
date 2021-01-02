package gitbucket.core.model

trait RepositoryComponent extends TemplateComponent { self: Profile =>
  import profile.api._
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
    val issuesOption = column[String]("ISSUES_OPTION")
    val externalIssuesUrl = column[String]("EXTERNAL_ISSUES_URL")
    val wikiOption = column[String]("WIKI_OPTION")
    val externalWikiUrl = column[String]("EXTERNAL_WIKI_URL")
    val allowFork = column[Boolean]("ALLOW_FORK")
    val mergeOptions = column[String]("MERGE_OPTIONS")
    val defaultMergeOption = column[String]("DEFAULT_MERGE_OPTION")

    def * =
      (
        (
          userName,
          repositoryName,
          isPrivate,
          description.?,
          defaultBranch,
          registeredDate,
          updatedDate,
          lastActivityDate,
          originUserName.?,
          originRepositoryName.?,
          parentUserName.?,
          parentRepositoryName.?
        ),
        (issuesOption, externalIssuesUrl.?, wikiOption, externalWikiUrl.?, allowFork, mergeOptions, defaultMergeOption)
      ).shaped.<>(
        {
          case (repository, options) =>
            Repository(
              repository._1,
              repository._2,
              repository._3,
              repository._4,
              repository._5,
              repository._6,
              repository._7,
              repository._8,
              repository._9,
              repository._10,
              repository._11,
              repository._12,
              RepositoryOptions.tupled.apply(options)
            )
        }, { (r: Repository) =>
          Some(
            (
              (
                r.userName,
                r.repositoryName,
                r.isPrivate,
                r.description,
                r.defaultBranch,
                r.registeredDate,
                r.updatedDate,
                r.lastActivityDate,
                r.originUserName,
                r.originRepositoryName,
                r.parentUserName,
                r.parentRepositoryName
              ),
              (
                RepositoryOptions.unapply(r.options).get
              )
            )
          )
        }
      )

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
  parentRepositoryName: Option[String],
  options: RepositoryOptions
)

case class RepositoryOptions(
  issuesOption: String,
  externalIssuesUrl: Option[String],
  wikiOption: String,
  externalWikiUrl: Option[String],
  allowFork: Boolean,
  mergeOptions: String,
  defaultMergeOption: String
)
