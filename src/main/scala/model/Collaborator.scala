package model

trait CollaboratorComponent extends TemplateComponent { self: Profile =>
  import profile.simple._

  lazy val Collaborators = TableQuery[Collaborators]

  class Collaborators(tag: Tag) extends Table[Collaborator](tag, "COLLABORATOR") with BasicTemplate {
    val collaboratorName = column[String]("COLLABORATOR_NAME")
    def * = (userName, repositoryName, collaboratorName) <> (Collaborator.tupled, Collaborator.unapply)

    def byPrimaryKey(owner: String, repository: String, collaborator: String) =
      byRepository(owner, repository) && (collaboratorName === collaborator.bind)
  }
}

case class Collaborator(
  userName: String,
  repositoryName: String,
  collaboratorName: String
)
