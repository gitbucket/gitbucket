package gitbucket.core.plugin

import gitbucket.core.model.Profile._
import org.eclipse.jgit.transport.{ReceivePack, ReceiveCommand}
import profile.simple._

trait CommitHook {

  def preCommit(owner: String, repository: String, receivePack: ReceivePack, command: ReceiveCommand, pusher: String)
               (implicit session: Session): Option[String] = None

  def postCommit(owner: String, repository: String, receivePack: ReceivePack, command: ReceiveCommand, pusher: String)
                (implicit session: Session): Unit = ()

}
