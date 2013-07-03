package service

import scala.slick.driver.H2Driver.simple._
import Database.threadLocalSession

import model._

trait LabelsService {

  def getLabels(owner: String, repository: String): List[Label] =
    Query(Labels).filter(_.byRepository(owner, repository)).sortBy(_.labelName asc).list

  def getLabel(owner: String, repository: String, labelId: Int): Option[Label] =
    Query(Labels).filter(_.byPrimaryKey(owner, repository, labelId)).firstOption

  def createLabel(owner: String, repository: String, labelName: String, color: String): Unit =
    Labels.ins insert (owner, repository, labelName, color)

  def updateLabel(owner: String, repository: String, labelId: Int, labelName: String, color: String): Unit =
    Labels.filter(_.byPrimaryKey(owner, repository, labelId)).map(t => t.labelName ~ t.color)
      .update(labelName, color)

  def deleteLabel(owner: String, repository: String, labelId: Int): Unit = {
    IssueLabels.filter(_.byLabel(owner, repository, labelId)).delete
    Labels.filter(_.byPrimaryKey(owner, repository, labelId)).delete
  }

}
