package gitbucket.core.service

import gitbucket.core.controller.Context
import gitbucket.core.model.{Account, Release, ReleaseAsset}
import gitbucket.core.model.Profile.profile.blockingApi._
import gitbucket.core.model.Profile._
import gitbucket.core.model.Profile.dateColumnType

trait ReleaseService {
  self: AccountService with RepositoryService =>

  def createReleaseAsset(owner: String, repository: String, tag: String, fileName: String, label: String, size: Long, loginAccount: Account)(implicit s: Session): Unit = {
    ReleaseAssets insert ReleaseAsset(
      userName       = owner,
      repositoryName = repository,
      tag            = tag,
      fileName       = fileName,
      label          = label,
      size           = size,
      uploader       = loginAccount.userName,
      registeredDate = currentDate,
      updatedDate    = currentDate
    )
  }

  def getReleaseAssets(owner: String, repository: String, tag: String)(implicit s: Session): Seq[ReleaseAsset] = {
    ReleaseAssets.filter(x => x.byTag(owner, repository, tag)).list
  }

  def getReleaseAssetsMap(owner: String, repository: String)(implicit s: Session): Map[Release, Seq[ReleaseAsset]] = {
    val releases = getReleases(owner, repository)
    releases.map(rel => (rel -> getReleaseAssets(owner, repository, rel.tag))).toMap
  }

  def getReleaseAsset(owner: String, repository: String, tag: String, fileId: String)(implicit s: Session): Option[ReleaseAsset] = {
    ReleaseAssets.filter(x => x.byPrimaryKey(owner, repository, tag, fileId)) firstOption
  }

  def deleteReleaseAssets(owner: String, repository: String, tag: String)(implicit s: Session): Unit = {
    ReleaseAssets.filter(x => x.byTag(owner, repository, tag)) delete
  }

  def createRelease(owner: String, repository: String, name: String, content: Option[String], tag: String,
                    loginAccount: Account)(implicit context: Context, s: Session): Int = {
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
  }

  def getReleases(owner: String, repository: String)(implicit s: Session): Seq[Release] = {
    Releases.filter(x => x.byRepository(owner, repository)).list
  }

  def getRelease(owner: String, repository: String, tag: String)(implicit s: Session): Option[Release] = {
    //Releases filter (_.byPrimaryKey(owner, repository, releaseId)) firstOption
    Releases filter (_.byTag(owner, repository, tag)) firstOption
  }

//  def getReleaseByTag(owner: String, repository: String, tag: String)(implicit s: Session): Option[Release] = {
//    Releases filter (_.byTag(owner, repository, tag)) firstOption
//  }
//
//  def getRelease(owner: String, repository: String, releaseId: String)(implicit s: Session): Option[Release] = {
//    if (isInteger(releaseId))
//      getRelease(owner, repository, releaseId.toInt)
//    else None
//  }

  def updateRelease(owner: String, repository: String, tag: String, title: String, content: Option[String])(implicit s: Session): Int = {
    Releases
      .filter (_.byPrimaryKey(owner, repository, tag))
      .map { t => (t.name, t.content, t.updatedDate) }
      .update (title, content, currentDate)
  }

  def deleteRelease(owner: String, repository: String, tag: String)(implicit s: Session): Unit = {
    deleteReleaseAssets(owner, repository, tag)
    Releases filter (_.byPrimaryKey(owner, repository, tag)) delete
  }
}
