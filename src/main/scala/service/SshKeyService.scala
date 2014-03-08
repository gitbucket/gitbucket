package service

import model._
import scala.slick.driver.H2Driver.simple._
import Database.threadLocalSession

trait SshKeyService {

  def addPublicKey(userName: String, title: String, publicKey: String): Unit =
    SshKeys.ins insert (userName, title, publicKey)

  def getPublicKeys(userName: String): List[SshKey] =
    Query(SshKeys).filter(_.userName is userName.bind).sortBy(_.sshKeyId).list

  def deletePublicKey(userName: String, sshKeyId: Int): Unit =
    SshKeys filter (_.byPrimaryKey(userName, sshKeyId)) delete


}
