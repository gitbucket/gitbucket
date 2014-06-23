package service

import model._
import profile.simple._

trait SshKeyService {

  def addPublicKey(userName: String, title: String, publicKey: String)(implicit s: Session): Unit =
    SshKeys insert SshKey(userName = userName, title = title, publicKey = publicKey)

  def getPublicKeys(userName: String)(implicit s: Session): List[SshKey] =
    SshKeys.filter(_.userName is userName.bind).sortBy(_.sshKeyId).list

  def deletePublicKey(userName: String, sshKeyId: Int)(implicit s: Session): Unit =
    SshKeys filter (_.byPrimaryKey(userName, sshKeyId)) delete

}
