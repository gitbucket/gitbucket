package gitbucket.core.model

import java.util.Date

trait ReleaseAssetComponent extends TemplateComponent {
  self: Profile =>

  import profile.api._
  import self._

  lazy val ReleaseAssets = TableQuery[ReleaseAssets]

  class ReleaseAssets(tag_ : Tag) extends Table[ReleaseAsset](tag_, "RELEASE_ASSET") with BasicTemplate {
    val tag = column[String]("TAG")
    val releaseAssetId = column[Int]("RELEASE_ASSET_ID", O AutoInc)
    val fileName = column[String]("FILE_NAME")
    val label = column[String]("LABEL")
    val size = column[Long]("SIZE")
    val uploader = column[String]("UPLOADER")
    val registeredDate = column[Date]("REGISTERED_DATE")
    val updatedDate = column[Date]("UPDATED_DATE")

    def * =
      (userName, repositoryName, tag, releaseAssetId, fileName, label, size, uploader, registeredDate, updatedDate)
        .<>(ReleaseAsset.tupled, ReleaseAsset.unapply)
    def byPrimaryKey(owner: String, repository: String, tag: String, fileName: String) =
      byTag(owner, repository, tag) && (this.fileName === fileName.bind)
    def byTag(owner: String, repository: String, tag: String) =
      byRepository(owner, repository) && (this.tag === tag.bind)
  }
}

case class ReleaseAsset(
  userName: String,
  repositoryName: String,
  tag: String,
  releaseAssetId: Int = 0,
  fileName: String,
  label: String,
  size: Long,
  uploader: String,
  registeredDate: Date,
  updatedDate: Date
)
