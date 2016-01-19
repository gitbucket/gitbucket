package gitbucket.core.plugin

import gitbucket.core.model.Profile._
import org.eclipse.jgit.transport.{ReceivePack, ReceiveCommand}
import profile.simple._

trait ReceiveHook {

  def preReceive(owner: String, repository: String, receivePack: ReceivePack, command: ReceiveCommand, pusher: String)
                (implicit session: Session): Option[String] = None

  def postReceive(owner: String, repository: String, receivePack: ReceivePack, command: ReceiveCommand, pusher: String)
                 (implicit session: Session): Unit = ()

}
