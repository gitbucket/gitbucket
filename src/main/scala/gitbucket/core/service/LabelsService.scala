package gitbucket.core.service

import gitbucket.core.model.Label
import gitbucket.core.model.Profile._
import profile._
import profile.api._

import scala.concurrent.Await
import scala.concurrent.duration.Duration

trait LabelsService {

  def getLabels(owner: String, repository: String)(implicit s: Session): List[Label] =
    Labels.filter(_.byRepository(owner, repository)).sortBy(_.labelName asc).list

  def getLabel(owner: String, repository: String, labelId: Int)(implicit s: Session): Option[Label] =
    Labels.filter(_.byPrimaryKey(owner, repository, labelId)).firstOption

  def getLabel(owner: String, repository: String, labelName: String)(implicit s: Session): Option[Label] =
    Labels.filter(_.byLabel(owner, repository, labelName)).firstOption

  def createLabel(owner: String, repository: String, labelName: String, color: String)(implicit s: Session): Int = {
    // TODO [Slick3]Provide blocking method for returning
    val f = s.database.run(
      Labels returning Labels.map(_.labelId) += Label(
        userName       = owner,
        repositoryName = repository,
        labelName      = labelName,
        color          = color
      )
    )
    Await.result(f, Duration.Inf)
  }

  def updateLabel(owner: String, repository: String, labelId: Int, labelName: String, color: String)
                 (implicit s: Session): Unit =
    Labels.filter(_.byPrimaryKey(owner, repository, labelId))
          .map(t => t.labelName -> t.color)
          .update(labelName, color)

  def deleteLabel(owner: String, repository: String, labelId: Int)(implicit s: Session): Unit = {
    IssueLabels.filter(_.byLabel(owner, repository, labelId)).unsafeDelete
    Labels.filter(_.byPrimaryKey(owner, repository, labelId)).unsafeDelete
  }

}
