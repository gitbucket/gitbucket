package gitbucket.core.service

import gitbucket.core.model.DeployKey
import gitbucket.core.model.Profile._
import gitbucket.core.model.Profile.profile.blockingApi._

trait DeployKeyService {

  def addDeployKey(userName: String, repositoryName: String, title: String, publicKey: String, allowWrite: Boolean)(
    implicit s: Session
  ): Unit =
    DeployKeys.insert(
      DeployKey(
        userName = userName,
        repositoryName = repositoryName,
        title = title,
        publicKey = publicKey,
        allowWrite = allowWrite
      )
    )

  def getDeployKeys(userName: String, repositoryName: String)(implicit s: Session): List[DeployKey] =
    DeployKeys
      .filter(x => (x.userName === userName.bind) && (x.repositoryName === repositoryName.bind))
      .sortBy(_.deployKeyId)
      .list

  def getAllDeployKeys()(implicit s: Session): List[DeployKey] =
    DeployKeys.filter(_.publicKey.trim =!= "").list

  def deleteDeployKey(userName: String, repositoryName: String, deployKeyId: Int)(implicit s: Session): Unit =
    DeployKeys.filter(_.byPrimaryKey(userName, repositoryName, deployKeyId)).delete

}
