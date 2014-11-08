package model

trait LabelComponent extends TemplateComponent { self: Profile =>
  import profile.simple._

  lazy val Labels = TableQuery[Labels]

  class Labels(tag: Tag) extends Table[Label](tag, "LABEL") with LabelTemplate {
    override val labelId = column[Int]("LABEL_ID", O AutoInc)
    val labelName = column[String]("LABEL_NAME")
    val color = column[String]("COLOR")
    def * = (userName, repositoryName, labelId, labelName, color) <> (Label.tupled, Label.unapply)

    def byPrimaryKey(owner: String, repository: String, labelId: Int) = byLabel(owner, repository, labelId)
    def byPrimaryKey(userName: Column[String], repositoryName: Column[String], labelId: Column[Int]) = byLabel(userName, repositoryName, labelId)
  }
}

case class Label(
  userName: String,
  repositoryName: String,
  labelId: Int = 0,
  labelName: String,
  color: String){

  val fontColor = {
    val r = color.substring(0, 2)
    val g = color.substring(2, 4)
    val b = color.substring(4, 6)

    if(Integer.parseInt(r, 16) + Integer.parseInt(g, 16) + Integer.parseInt(b, 16) > 408){
      "000000"
    } else {
      "ffffff"
    }
  }
}
