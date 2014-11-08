package service

import model.Profile._
import profile.simple._
import model.Label

trait LabelsService {

  def getLabels(owner: String, repository: String)(implicit s: Session): List[Label] =
    Labels.filter(_.byRepository(owner, repository)).sortBy(_.labelName asc).list

  def getLabel(owner: String, repository: String, labelId: Int)(implicit s: Session): Option[Label] =
    Labels.filter(_.byPrimaryKey(owner, repository, labelId)).firstOption

  def createLabel(owner: String, repository: String, labelName: String, color: String)(implicit s: Session): Int =
    Labels returning Labels.map(_.labelId) += Label(
      userName       = owner,
      repositoryName = repository,
      labelName      = labelName,
      color          = color
    )

  def updateLabel(owner: String, repository: String, labelId: Int, labelName: String, color: String)
                 (implicit s: Session): Unit =
    Labels.filter(_.byPrimaryKey(owner, repository, labelId))
          .map(t => t.labelName -> t.color)
          .update(labelName, color)

  def deleteLabel(owner: String, repository: String, labelId: Int)(implicit s: Session): Unit = {
    IssueLabels.filter(_.byLabel(owner, repository, labelId)).delete
    Labels.filter(_.byPrimaryKey(owner, repository, labelId)).delete
  }

}
