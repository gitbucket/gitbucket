package gitbucket.core.plugin

import gitbucket.core.model.Profile._
import profile.api._

trait RepositoryHook {

  def created(owner: String, repository: String)(implicit session: Session): Unit = ()
  def deleted(owner: String, repository: String)(implicit session: Session): Unit = ()
  def renamed(owner: String, repository: String, newRepository: String)(implicit session: Session): Unit = ()
  def transferred(owner: String, newOwner: String, repository: String)(implicit session: Session): Unit = ()
  def forked(owner: String, newOwner: String, repository: String)(implicit session: Session): Unit = ()

}
