package model

import scala.slick.driver.H2Driver.simple._

object SshKeys extends Table[SshKey]("SSH_KEY") {
  def userName = column[String]("USER_NAME")
  def sshKeyId = column[Int]("SSH_KEY")
  def publicKey = column[String]("PUBLIC_KEY")
  def * = userName ~ sshKeyId ~ publicKey <> (SshKey, SshKey.unapply _)

  def byPrimaryKey(userName: String, sshKeyId: Int) = (this.userName is userName.bind) && (this.sshKeyId is sshKeyId.bind)
}

case class SshKey(
  userName: String,
  sshKeyId: Int,
  publicKey: String
)
