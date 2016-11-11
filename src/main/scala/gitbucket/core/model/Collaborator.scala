package gitbucket.core.model

trait CollaboratorComponent extends TemplateComponent { self: Profile =>
  import profile.api._

  lazy val Collaborators = TableQuery[Collaborators]

  class Collaborators(tag: Tag) extends Table[Collaborator](tag, "COLLABORATOR") with BasicTemplate {
    val collaboratorName = column[String]("COLLABORATOR_NAME")
    val permission = column[String]("PERMISSION")
    def * = (userName, repositoryName, collaboratorName, permission) <> (Collaborator.tupled, Collaborator.unapply)

    def byPrimaryKey(owner: String, repository: String, collaborator: String) =
      byRepository(owner, repository) && (collaboratorName === collaborator.bind)
  }
}

case class Collaborator(
  userName: String,
  repositoryName: String,
  collaboratorName: String,
  permission: String
)

sealed abstract class Permission(val name: String)

object Permission {
  object ADMIN extends Permission("ADMIN")
  object WRITE extends Permission("WRITE")
  object READ  extends Permission("READ")

//  val values: Vector[Permission] = Vector(ADMIN, WRITE, READ)
//
//  private val map: Map[String, Permission] = values.map(enum => enum.name -> enum).toMap
//
//  def apply(name: String): Permission = map(name)
//
//  def valueOf(name: String): Option[Permission] = map.get(name)

}
