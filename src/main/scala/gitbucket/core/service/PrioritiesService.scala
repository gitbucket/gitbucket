package gitbucket.core.service

import gitbucket.core.model.Priority
import gitbucket.core.model.Profile._
import gitbucket.core.model.Profile.profile.blockingApi._
import gitbucket.core.util.StringUtil

trait PrioritiesService {

  def getPriorities(owner: String, repository: String)(implicit s: Session): List[Priority] =
    Priorities.filter(_.byRepository(owner, repository)).sortBy(_.ordering asc).list

  def getPriority(owner: String, repository: String, priorityId: Int)(implicit s: Session): Option[Priority] =
    Priorities.filter(_.byPrimaryKey(owner, repository, priorityId)).firstOption

  def getPriority(owner: String, repository: String, priorityName: String)(implicit s: Session): Option[Priority] =
    Priorities.filter(_.byPriority(owner, repository, priorityName)).firstOption

  def createPriority(
    owner: String,
    repository: String,
    priorityName: String,
    description: Option[String],
    color: String
  )(implicit s: Session): Int = {
    val ordering = Priorities
      .filter(_.byRepository(owner, repository))
      .list
      .map(p => p.ordering)
      .reduceOption(_ max _)
      .map(m => m + 1)
      .getOrElse(0)

    Priorities returning Priorities.map(_.priorityId) insert Priority(
      userName = owner,
      repositoryName = repository,
      priorityName = priorityName,
      description = description,
      isDefault = false,
      ordering = ordering,
      color = color
    )
  }

  def updatePriority(
    owner: String,
    repository: String,
    priorityId: Int,
    priorityName: String,
    description: Option[String],
    color: String
  )(implicit s: Session): Unit =
    Priorities
      .filter(_.byPrimaryKey(owner, repository, priorityId))
      .map(t => (t.priorityName, t.description.?, t.color))
      .update(priorityName, description, color)

  def reorderPriorities(owner: String, repository: String, order: Map[Int, Int])(implicit s: Session): Unit = {

    Priorities
      .filter(_.byRepository(owner, repository))
      .list
      .foreach(
        p =>
          Priorities
            .filter(_.byPrimaryKey(owner, repository, p.priorityId))
            .map(_.ordering)
            .update(order.get(p.priorityId).get)
      )
  }

  def deletePriority(owner: String, repository: String, priorityId: Int)(implicit s: Session): Unit = {
    Issues
      .filter(_.byRepository(owner, repository))
      .filter(_.priorityId === priorityId)
      .map(_.priorityId ?)
      .update(None)

    Priorities.filter(_.byPrimaryKey(owner, repository, priorityId)).delete
  }

  def getDefaultPriority(owner: String, repository: String)(implicit s: Session): Option[Priority] = {
    Priorities
      .filter(_.byRepository(owner, repository))
      .filter(_.isDefault)
      .list
      .headOption
  }

  def setDefaultPriority(owner: String, repository: String, priorityId: Option[Int])(implicit s: Session): Unit = {
    Priorities
      .filter(_.byRepository(owner, repository))
      .filter(_.isDefault)
      .map(_.isDefault)
      .update(false)

    priorityId.foreach(
      id =>
        Priorities
          .filter(_.byPrimaryKey(owner, repository, id))
          .map(_.isDefault)
          .update(true)
    )
  }
}
