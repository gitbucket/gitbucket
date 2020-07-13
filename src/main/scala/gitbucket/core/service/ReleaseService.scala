package gitbucket.core.service

import gitbucket.core.controller.Context
import gitbucket.core.model.{Account, ReleaseAsset, ReleaseTag}
import gitbucket.core.model.Profile.profile.blockingApi._
import gitbucket.core.model.Profile._
import gitbucket.core.model.Profile.dateColumnType
import gitbucket.core.util.JGitUtil

trait ReleaseService {
  self: AccountService with RepositoryService =>

  def createReleaseAsset(
    owner: String,
    repository: String,
    tag: String,
    fileName: String,
    label: String,
    size: Long,
    loginAccount: Account
  )(implicit s: Session): Unit = {
    ReleaseAssets insert ReleaseAsset(
      userName = owner,
      repositoryName = repository,
      tag = tag,
      fileName = fileName,
      label = label,
      size = size,
      uploader = loginAccount.userName,
      registeredDate = currentDate,
      updatedDate = currentDate
    )
  }

  def getReleaseAssets(owner: String, repository: String, tag: String)(implicit s: Session): Seq[ReleaseAsset] = {
    ReleaseAssets.filter(x => x.byTag(owner, repository, tag)).list
  }

  def getReleaseAssetsMap(owner: String, repository: String, releases: Seq[ReleaseTag])(
    implicit s: Session
  ): Map[ReleaseTag, Seq[ReleaseAsset]] = {
    releases.map(rel => (rel -> getReleaseAssets(owner, repository, rel.tag))).toMap
  }

  def getReleaseAsset(owner: String, repository: String, tag: String, fileId: String)(
    implicit s: Session
  ): Option[ReleaseAsset] = {
    ReleaseAssets.filter(x => x.byPrimaryKey(owner, repository, tag, fileId)) firstOption
  }

  def deleteReleaseAssets(owner: String, repository: String, tag: String)(implicit s: Session): Unit = {
    ReleaseAssets.filter(x => x.byTag(owner, repository, tag)) delete
  }

  def createRelease(
    owner: String,
    repository: String,
    name: String,
    content: Option[String],
    tag: String,
    loginAccount: Account
  )(implicit context: Context, s: Session): Int = {
    ReleaseTags insert ReleaseTag(
      userName = owner,
      repositoryName = repository,
      name = name,
      tag = tag,
      author = loginAccount.userName,
      content = content,
      registeredDate = currentDate,
      updatedDate = currentDate
    )
  }

  def getReleases(owner: String, repository: String)(implicit s: Session): Seq[ReleaseTag] = {
    ReleaseTags.filter(x => x.byRepository(owner, repository)).sortBy(x => x.updatedDate).list
  }

  def getReleases(owner: String, repository: String, tags: Seq[JGitUtil.TagInfo])(
    implicit s: Session
  ): Seq[ReleaseTag] = {
    ReleaseTags
      .filter(x => x.byRepository(owner, repository))
      .filter(x => x.tag inSetBind tags.map(_.name))
      .sortBy(x => x.updatedDate)
      .list
  }
  def getRelease(owner: String, repository: String, tag: String)(implicit s: Session): Option[ReleaseTag] = {
    ReleaseTags.filter(_.byTag(owner, repository, tag)).firstOption
  }

  def updateRelease(owner: String, repository: String, tag: String, title: String, content: Option[String])(
    implicit s: Session
  ): Int = {
    ReleaseTags
      .filter(_.byPrimaryKey(owner, repository, tag))
      .map { t =>
        (t.name, t.content, t.updatedDate)
      }
      .update(title, content, currentDate)
  }

  def deleteRelease(owner: String, repository: String, tag: String)(implicit s: Session): Unit = {
    deleteReleaseAssets(owner, repository, tag)
    ReleaseTags filter (_.byPrimaryKey(owner, repository, tag)) delete
  }
}

object ReleaseService {

  val ReleaseLimit = 10

}
