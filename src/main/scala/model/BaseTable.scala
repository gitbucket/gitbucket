package model

import scala.slick.driver.H2Driver.simple._

protected[model] abstract class BaseTable[T](_tableName: String) extends Table[T](_tableName) {
  def userName = column[String]("USER_NAME")
  def repositoryName = column[String]("REPOSITORY_NAME")
  def base = userName ~ repositoryName

  def repository(owner: String, repository: String) =
    (userName is owner.bind) && (repositoryName is repository.bind)

  def repository(other: BaseTable[T]) =
    (userName is other.userName) && (repositoryName is other.repositoryName)
}
