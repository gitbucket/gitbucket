package model

import scala.slick.driver.H2Driver.simple._
import model.{BaseTable => Table}

object Labels extends Table[Label]("LABEL") {
  def labelId = column[Int]("LABEL_ID", O AutoInc)
  def labelName = column[String]("LABEL_NAME")
  def color = column[String]("COLOR")
  def * = base ~ labelId ~ labelName ~ color <> (Label, Label.unapply _)
  def ins = base ~ labelName ~ color
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