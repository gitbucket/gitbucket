package service

import model.Profile._
import profile.simple._
import model.SshKey

trait SshKeyService {

  def addPublicKey(userName: String, title: String, publicKey: String)(implicit s: Session): Unit =
    SshKeys insert SshKey(userName = userName, title = title, publicKey = publicKey)

  def getPublicKeys(userName: String)(implicit s: Session): List[SshKey] =
    SshKeys.filter(_.userName === userName.bind).sortBy(_.sshKeyId).list

  def deletePublicKey(userName: String, sshKeyId: Int)(implicit s: Session): Unit =
    SshKeys filter (_.byPrimaryKey(userName, sshKeyId)) delete

}
