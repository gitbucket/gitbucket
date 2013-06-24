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
  color: String)