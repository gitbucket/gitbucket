package model

trait LabelComponent extends LabelTemplateComponent { self: Profile =>
  import profile.simple._

  object Labels extends Table[Label]("LABEL") with LabelTemplate {
    def labelName = column[String]("LABEL_NAME")
    def color = column[String]("COLOR")
    def * = userName ~ repositoryName ~ labelId ~ labelName ~ color <> (Label, Label.unapply _)

    def ins = userName ~ repositoryName ~ labelName ~ color
    def byPrimaryKey(owner: String, repository: String, labelId: Int) = byLabel(owner, repository, labelId)
    def byPrimaryKey(userName: Column[String], repositoryName: Column[String], labelId: Column[Int]) = byLabel(userName, repositoryName, labelId)
  }
}

case class Label(
  userName: String,
  repositoryName: String,
  labelId: Int,
  labelName: String,
  color: String){

  val fontColor = {
    val r = color.substring(0, 2)
    val g = color.substring(2, 4)
    val b = color.substring(4, 6)

    if(Integer.parseInt(r, 16) + Integer.parseInt(g, 16) + Integer.parseInt(b, 16) > 408){
      "000000"
    } else {
      "FFFFFF"
    }
  }

}