package gitbucket.core.service

import gitbucket.core.controller.Context
import gitbucket.core.model.{Account, Release, ReleaseAsset}
import gitbucket.core.util.StringUtil._
import gitbucket.core.util.Implicits._
import gitbucket.core.model.Profile.profile.blockingApi._
import gitbucket.core.model.Profile._
import gitbucket.core.model.Profile.profile._
import gitbucket.core.model.Profile.dateColumnType
import gitbucket.core.service.RepositoryService.RepositoryInfo

trait ReleaseService {
  self: AccountService with RepositoryService =>

  def createReleaseAsset(owner: String, repository: String, releaseId: Int, fileName: String, label: String, size: Long, loginAccount: Account)(implicit s: Session): Unit = {
    ReleaseAssets insert ReleaseAsset(
      userName = owner,
      repositoryName = repository,
      releaseId = releaseId,
      fileName = fileName,
      label = label,
      size = size,
      uploader = loginAccount.userName,
      registeredDate = currentDate,
      updatedDate = currentDate
    )
  }

  def getReleaseAssets(owner: String, repository: String, releaseId: Int)(implicit s: Session): List[ReleaseAsset] = {
    ReleaseAssets.filter(x => x.byRelease(owner, repository, releaseId)).list
  }

  def getReleaseAssets(owner: String, repository: String, releaseId: String)(implicit s: Session): List[ReleaseAsset] = {
    if (isInteger(releaseId))
      getReleaseAssets(owner, repository, releaseId.toInt)
    else
      List.empty
  }

  def getReleaseAssetsMap(owner: String, repository: String)(implicit s: Session): Map[Release, List[ReleaseAsset]] = {
    val releases = getReleases(owner, repository)
    releases.map(rel => (rel -> getReleaseAssets(owner, repository, rel.releaseId))).toMap
  }

  def getReleaseAsset(owner: String, repository: String, releaseId: String, fileId: String)(implicit s: Session): Option[ReleaseAsset] = {
    if (isInteger(releaseId))
      ReleaseAssets.filter(x => x.byPrimaryKey(owner, repository, releaseId.toInt, fileId)) firstOption
    else None
  }

  def deleteReleaseAssets(owner: String, repository: String, releaseId: Int)(implicit s:Session): Unit = {
    ReleaseAssets.filter(x => x.byRelease(owner, repository, releaseId)) delete
  }

  def deleteReleaseAsset(owner: String, repository: String, releaseId: Int, fileId: String)(implicit s: Session): Unit = {
    ReleaseAssets.filter(x => x.byPrimaryKey(owner, repository, releaseId, fileId)) delete
  }

  def createRelease(repository: RepositoryInfo, name: String, content:Option[String], tag: String,
    isDraft: Boolean, isPrerelease: Boolean, loginAccount: Account)(implicit context: Context, s: Session): Release = {
    Releases insert Release(
      userName = repository.owner,
      repositoryName = repository.name,
      name = name,
      tag = tag,
      author = loginAccount.userName,
      content = content,
      isDraft = isDraft,
      isPrerelease = isPrerelease,
      registeredDate = currentDate,
      updatedDate = currentDate
    )
    getReleaseByTag(repository.owner, repository.name, tag).get
  }

  def getReleases(owner: String, repository: String)(implicit s: Session): List[Release] = {
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

  def getReleaseTagMap(owner: String, repository: String)(implicit s: Session): Map[String, Release] = {
    val releases = getReleases(owner, repository)
    releases.map(rel => (rel.tag -> rel)).toMap
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
