package gitbucket.core.model

trait ReleaseComponent extends TemplateComponent {
  self: Profile =>

  import profile.api._
  import self._

  lazy val Releases = TableQuery[Releases]

  class Releases(tag_ : Tag) extends Table[Release](tag_, "RELEASE") with BasicTemplate {
    val releaseId = column[Int]("RELEASE_ID", O AutoInc)
    val name = column[String]("NAME")
    val tag = column[String]("TAG")
    val author = column[String]("AUTHOR")
    val content = column[Option[String]]("CONTENT")
    val isDraft = column[Boolean]("IS_DRAFT")
    val isPrerelease = column[Boolean]("IS_PRERELEASE")
    val registeredDate = column[java.util.Date]("REGISTERED_DATE")
    val updatedDate = column[java.util.Date]("UPDATED_DATE")

    def * = (userName, repositoryName, releaseId, name, tag, author, content, isDraft, isPrerelease, registeredDate, updatedDate) <> (Release.tupled, Release.unapply)

    def byPrimaryKey(owner: String, repository: String, releaseId: Int) = byRelease(owner, repository, releaseId)

    def byTag(owner: String, repository: String, tag: String) =
      byRepository(owner, repository) && (this.tag === tag.bind)

    def byTag(userName: Rep[String], repositoryName: Rep[String], tag: Rep[String]) =
      byRepository(userName, repositoryName) && (this.tag === tag)

    def byRelease(owner: String, repository: String, releaseId: Int) =
      byRepository(owner, repository) && (this.releaseId === releaseId.bind)

    def byRelease(userName: Rep[String], repositoryName: Rep[String], releaseId: Rep[Int]) =
      byRepository(userName, repositoryName) && (this.releaseId === releaseId)
  }

}

case class Release(
  userName: String,
  repositoryName: String,
  releaseId: Int = 0,
  name: String,
  tag: String,
  author: String,
  content: Option[String],
  isDraft: Boolean,
  isPrerelease: Boolean,
  registeredDate: java.util.Date,
  updatedDate: java.util.Date
)
