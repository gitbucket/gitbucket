package gitbucket.core.model

trait SshKeyComponent { self: Profile =>
  import profile.simple._

  lazy val SshKeys = TableQuery[SshKeys]

  class SshKeys(tag: Tag) extends Table[SshKey](tag, "SSH_KEY") {
    val userName = column[String]("USER_NAME")
    val sshKeyId = column[Int]("SSH_KEY_ID", O AutoInc)
    val title = column[String]("TITLE")
    val publicKey = column[String]("PUBLIC_KEY")
    def * = (userName, sshKeyId, title, publicKey) <> (SshKey.tupled, SshKey.unapply)

    def byPrimaryKey(userName: String, sshKeyId: Int) = (this.userName === userName.bind) && (this.sshKeyId === sshKeyId.bind)
  }
}

case class SshKey(
  userName: String,
  sshKeyId: Int = 0,
  title: String,
  publicKey: String
)
