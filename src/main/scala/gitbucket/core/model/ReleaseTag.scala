package gitbucket.core.model

trait ReleaseTagComponent extends TemplateComponent {
  self: Profile =>

  import profile.api._
  import self._

  lazy val ReleaseTags = TableQuery[ReleaseTags]

  class ReleaseTags(tag_ : Tag) extends Table[ReleaseTag](tag_, "RELEASE_TAG") with BasicTemplate {
    val name = column[String]("NAME")
    val tag = column[String]("TAG")
    val author = column[String]("AUTHOR")
    val content = column[Option[String]]("CONTENT")
    val registeredDate = column[java.util.Date]("REGISTERED_DATE")
    val updatedDate = column[java.util.Date]("UPDATED_DATE")

    def * =
      (userName, repositoryName, name, tag, author, content, registeredDate, updatedDate)
        .<>(ReleaseTag.tupled, ReleaseTag.unapply)
    def byPrimaryKey(owner: String, repository: String, tag: String) = byTag(owner, repository, tag)
    def byTag(owner: String, repository: String, tag: String) =
      byRepository(owner, repository) && (this.tag === tag.bind)
  }
}

case class ReleaseTag(
  userName: String,
  repositoryName: String,
  name: String,
  tag: String,
  author: String,
  content: Option[String],
  registeredDate: java.util.Date,
  updatedDate: java.util.Date
)
