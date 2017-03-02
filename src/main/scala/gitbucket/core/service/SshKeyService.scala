package gitbucket.core.service

import gitbucket.core.model.SshKey
import gitbucket.core.model.Profile._
import gitbucket.core.model.Profile.profile.blockingApi._

trait SshKeyService {

  def addPublicKey(userName: String, title: String, publicKey: String)(implicit s: Session): Unit =
    SshKeys.insert(SshKey(userName = userName, title = title, publicKey = publicKey))

  def getPublicKeys(userName: String)(implicit s: Session): List[SshKey] =
    SshKeys.filter(_.userName === userName.bind).sortBy(_.sshKeyId).list

  def getAllKeys()(implicit s: Session): List[SshKey] =
    SshKeys.filter(_.publicKey.trim =!= "").list

  def deletePublicKey(userName: String, sshKeyId: Int)(implicit s: Session): Unit =
    SshKeys.filter(_.byPrimaryKey(userName, sshKeyId)).delete

}
