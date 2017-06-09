package gitbucket.core.plugin

import gitbucket.core.model.Profile._
import profile.api._

trait AccountHook {

  def deleted(userName: String)(implicit session: Session): Unit = ()

}
