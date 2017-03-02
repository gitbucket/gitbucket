package gitbucket.core.model

trait DeployKeyComponent { self: Profile =>
  import profile.api._

  lazy val DeployKeys = TableQuery[DeployKeys]

  class DeployKeys(tag: Tag) extends Table[DeployKey](tag, "DEPLOY_KEY") {
    val userName = column[String]("USER_NAME")
    val repositoryName = column[String]("REPOSITORY_NAME")
    val deployKeyId = column[Int]("DEPLOY_KEY_ID", O AutoInc)
    val title = column[String]("TITLE")
    val publicKey = column[String]("PUBLIC_KEY")
    def * = (userName, repositoryName, deployKeyId, title, publicKey) <> (DeployKey.tupled, DeployKey.unapply)

    def byPrimaryKey(userName: String, repositoryName: String, deployKeyId: Int) =
      (this.userName === userName.bind) && (this.repositoryName === repositoryName.bind) && (this.deployKeyId === deployKeyId.bind)
  }
}

case class DeployKey(
  userName: String,
  repositoryName: String,
  deployKeyId: Int = 0,
  title: String,
  publicKey: String
)
