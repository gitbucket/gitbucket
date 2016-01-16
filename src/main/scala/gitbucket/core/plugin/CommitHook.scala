package gitbucket.core.plugin

import gitbucket.core.model.Profile._
import org.eclipse.jgit.transport.ReceiveCommand
import profile.simple._

trait CommitHook {

  def hook(owner: String, repository: String, isAllowNonFastForwards: Boolean, command: ReceiveCommand, pusher: String)
          (implicit session: Session): Option[String]

}
