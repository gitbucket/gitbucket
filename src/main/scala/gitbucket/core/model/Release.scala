package gitbucket.core.model

trait ReleaseComponent extends TemplateComponent {
  self: Profile =>

  import profile.api._
  import self._

  lazy val Releases = TableQuery[Releases]

  class Releases(tag_ : Tag) extends Table[Release](tag_, "RELEASE") with BasicTemplate {
    val name = column[String]("NAME")
    val tag = column[String]("TAG")
    val author = column[String]("AUTHOR")
    val content = column[Option[String]]("CONTENT")
    val registeredDate = column[java.util.Date]("REGISTERED_DATE")
    val updatedDate = column[java.util.Date]("UPDATED_DATE")

    def * = (userName, repositoryName, name, tag, author, content, registeredDate, updatedDate) <> (Release.tupled, Release.unapply)
    def byPrimaryKey(owner: String, repository: String, tag: String) = byTag(owner, repository, tag)
    def byTag(owner: String, repository: String, tag: String) = byRepository(owner, repository) && (this.tag === tag.bind)
  }
}

case class Release(
  userName: String,
  repositoryName: String,
  name: String,
  tag: String,
  author: String,
  content: Option[String],
  registeredDate: java.util.Date,
  updatedDate: java.util.Date
)
