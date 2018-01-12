package gitbucket.core.service

import gitbucket.core.controller.Context
import gitbucket.core.model.{Account, Release, ReleaseAsset}
import gitbucket.core.util.StringUtil._
import gitbucket.core.model.Profile.profile.blockingApi._
import gitbucket.core.model.Profile._
import gitbucket.core.model.Profile.dateColumnType

trait ReleaseService {
  self: AccountService with RepositoryService =>

  def createReleaseAsset(owner: String, repository: String, releaseId: Int, fileName: String, label: String, size: Long, loginAccount: Account)(implicit s: Session): Unit = {
    ReleaseAssets insert ReleaseAsset(
      userName       = owner,
      repositoryName = repository,
      releaseId      = releaseId,
      fileName       = fileName,
      label          = label,
      size           = size,
      uploader       = loginAccount.userName,
      registeredDate = currentDate,
      updatedDate    = currentDate
    )
  }

  def getReleaseAssets(owner: String, repository: String, releaseId: Int)(implicit s: Session): Seq[ReleaseAsset] = {
    ReleaseAssets.filter(x => x.byRelease(owner, repository, releaseId)).list
  }

  def getReleaseAssets(owner: String, repository: String, releaseId: String)(implicit s: Session): Seq[ReleaseAsset] = {
    if (isInteger(releaseId))
      getReleaseAssets(owner, repository, releaseId.toInt)
    else
      Seq.empty
  }

  def getReleaseAssetsMap(owner: String, repository: String)(implicit s: Session): Map[Release, Seq[ReleaseAsset]] = {
    val releases = getReleases(owner, repository)
    releases.map(rel => (rel -> getReleaseAssets(owner, repository, rel.releaseId))).toMap
  }

  def getReleaseAsset(owner: String, repository: String, releaseId: String, fileId: String)(implicit s: Session): Option[ReleaseAsset] = {
    if (isInteger(releaseId))
      ReleaseAssets.filter(x => x.byPrimaryKey(owner, repository, releaseId.toInt, fileId)) firstOption
    else None
  }

  def deleteReleaseAssets(owner: String, repository: String, releaseId: Int)(implicit s: Session): Unit = {
    ReleaseAssets.filter(x => x.byRelease(owner, repository, releaseId)) delete
  }

  def createRelease(owner: String, repository: String, name: String, content: Option[String], tag: String,
                    loginAccount: Account)(implicit context: Context, s: Session): Release = {
    Releases insert Release(
      userName       = owner,
      repositoryName = repository,
      name           = name,
      tag            = tag,
      author         = loginAccount.userName,
      content        = content,
      registeredDate = currentDate,
      updatedDate    = currentDate
    )
    getReleaseByTag(owner, repository, tag).get
  }

  def getReleases(owner: String, repository: String)(implicit s: Session): Seq[Release] = {
    Releases.filter(x => x.byRepository(owner, repository)).list
  }

  def getRelease(owner: String, repository: String, releaseId: Int)(implicit s: Session): Option[Release] = {
    Releases filter (_.byPrimaryKey(owner, repository, releaseId)) firstOption
  }

  def getReleaseByTag(owner: String, repository: String, tag: String)(implicit s: Session): Option[Release] = {
    Releases filter (_.byTag(owner, repository, tag)) firstOption
  }

  def getRelease(owner: String, repository: String, releaseId: String)(implicit s: Session): Option[Release] = {
    if (isInteger(releaseId))
      getRelease(owner, repository, releaseId.toInt)
    else None
  }

  def updateRelease(owner: String, repository: String, releaseId: Int, title: String, content: Option[String])(implicit s: Session): Int = {
    Releases
      .filter (_.byPrimaryKey(owner, repository, releaseId))
      .map { t => (t.name, t.content, t.updatedDate) }
      .update (title, content, currentDate)
  }

  def deleteRelease(owner: String, repository: String, releaseId: String)(implicit s: Session): Unit = {
    if (isInteger(releaseId)){
      val relId = releaseId.toInt
      deleteReleaseAssets(owner, repository, relId)
      Releases filter (_.byPrimaryKey(owner, repository, relId)) delete
    }
  }
}
