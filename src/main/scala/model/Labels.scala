package model

import scala.slick.driver.H2Driver.simple._

object Labels extends Table[Label]("LABEL") with Functions {
  def userName = column[String]("USER_NAME", O PrimaryKey)
  def repositoryName = column[String]("REPOSITORY_NAME", O PrimaryKey)
  def labelId = column[Int]("LABEL_ID", O PrimaryKey, O AutoInc)
  def labelName = column[String]("LABEL_NAME")
  def color = column[String]("COLOR")
  def * = userName ~ repositoryName ~ labelId ~ labelName ~ color <> (Label, Label.unapply _)
  def ins = userName ~ repositoryName ~ labelName ~ color
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