package gitbucket.core.model

trait PriorityComponent extends TemplateComponent { self: Profile =>
  import profile.api._

  lazy val Priorities = TableQuery[Priorities]

  class Priorities(tag: Tag) extends Table[Priority](tag, "PRIORITY") with PriorityTemplate {
    override val priorityId = column[Int]("PRIORITY_ID", O AutoInc)
    override val priorityName = column[String]("PRIORITY_NAME")
    val description = column[String]("DESCRIPTION")
    val ordering = column[Int]("ORDERING")
    val isDefault = column[Boolean]("IS_DEFAULT")
    val color = column[String]("COLOR")
    def * =
      (userName, repositoryName, priorityId, priorityName, description.?, isDefault, ordering, color)
        .<>(Priority.tupled, Priority.unapply)

    def byPrimaryKey(owner: String, repository: String, priorityId: Int) = byPriority(owner, repository, priorityId)
    def byPrimaryKey(userName: Rep[String], repositoryName: Rep[String], priorityId: Rep[Int]) =
      byPriority(userName, repositoryName, priorityId)
  }
}

case class Priority(
  userName: String,
  repositoryName: String,
  priorityId: Int = 0,
  priorityName: String,
  description: Option[String],
  isDefault: Boolean,
  ordering: Int = 0,
  color: String
) {

  val fontColor = {
    val r = color.substring(0, 2)
    val g = color.substring(2, 4)
    val b = color.substring(4, 6)

    if (Integer.parseInt(r, 16) + Integer.parseInt(g, 16) + Integer.parseInt(b, 16) > 408) {
      "000000"
    } else {
      "ffffff"
    }
  }
}
