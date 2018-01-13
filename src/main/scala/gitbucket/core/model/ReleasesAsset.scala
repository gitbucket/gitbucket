package gitbucket.core.model

import java.util.Date

trait ReleaseAssetComponent extends TemplateComponent {
  self: Profile =>

  import profile.api._
  import self._

  lazy val ReleaseAssets = TableQuery[ReleaseAssets]

  class ReleaseAssets(tag : Tag) extends Table[ReleaseAsset](tag, "RELEASE_ASSET") with BasicTemplate {
    val releaseId = column[Int]("RELEASE_ID")
    val releaseAssetId = column[Int]("RELEASE_ASSET_ID", O AutoInc)
    val fileName = column[String]("FILE_NAME")
    val label = column[String]("LABEL")
    val size = column[Long]("SIZE")
    val uploader = column[String]("UPLOADER")
    val registeredDate = column[Date]("REGISTERED_DATE")
    val updatedDate = column[Date]("UPDATED_DATE")

    def * = (userName, repositoryName, releaseId, releaseAssetId, fileName, label, size, uploader, registeredDate, updatedDate) <> (ReleaseAsset.tupled, ReleaseAsset.unapply)

    def byPrimaryKey(owner: String, repository: String, releaseId: Int, fileName: String) = byRelease(owner, repository, releaseId) && (this.fileName === fileName.bind)

    def byRelease(owner: String, repository: String, releaseId: Int) =
      byRepository(owner, repository) && (this.releaseId === releaseId.bind)

    def byRelease(userName: Rep[String], repositoryName: Rep[String], releaseId: Rep[Int]) =
      byRepository(userName, repositoryName) && (this.releaseId === releaseId)
  }

}

case class ReleaseAsset(
  userName: String,
  repositoryName: String,
  releaseId: Int,
  releaseAssetId: Int = 0,
  fileName: String,
  label: String,
  size: Long,
  uploader: String,
  registeredDate: Date,
  updatedDate: Date
)
