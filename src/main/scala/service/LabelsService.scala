package service

import scala.slick.driver.H2Driver.simple._
import Database.threadLocalSession

import model._

trait LabelsService {

  def getLabels(owner: String, repository: String): List[Label] =
    Query(Labels)
      .filter(t => (t.userName is owner.bind) && (t.repositoryName is repository.bind))
      .sortBy(_.labelName asc)
      .list

  def getLabel(owner: String, repository: String, labelId: Int): Option[Label] =
    Query(Labels)
      .filter(t => (t.userName is owner.bind) && (t.repositoryName is repository.bind) && (t.labelId is labelId.bind))
      .firstOption

  def createLabel(owner: String, repository: String, labelName: String, color: String): Unit =
    Labels.ins insert (owner, repository, labelName, color)

  def updateLabel(owner: String, repository: String, labelId: Int, labelName: String, color: String): Unit =
    Query(Labels)
      .filter { t => (t.userName is owner.bind) && (t.repositoryName is repository.bind) && (t.labelId is labelId.bind)}
      .map    { t => t.labelName ~ t.color }
      .update (labelName, color)

  def deleteLabel(owner: String, repository: String, labelId: Int): Unit = {
    Query(IssueLabels)
      .filter { t => (t.userName is owner.bind) && (t.repositoryName is repository.bind) && (t.labelId is labelId.bind)}
      .delete

    Query(Labels)
      .filter { t => (t.userName is owner.bind) && (t.repositoryName is repository.bind) && (t.labelId is labelId.bind)}
      .delete
  }

}
