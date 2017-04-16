package gitbucket.core.model

import java.util.Date

trait ReleaseAssetComponent extends TemplateComponent {
  self: Profile =>

  import profile.api._
  import self._

  lazy val ReleaseAssets = TableQuery[ReleaseAssets]

  class ReleaseAssets(tag : Tag) extends Table[ReleaseAsset](tag, "RELEASE_ASSET") with ReleaseTemplate {
    val fileName = column[String]("FILE_NAME")
    val label = column[String]("LABEL")
    val size = column[Long]("SIZE")
    val uploader = column[String]("UPLOADER")
    val registeredDate = column[Date]("REGISTERED_DATE")
    val updatedDate = column[Date]("UPDATED_DATE")

    def * = (userName, repositoryName, releaseId, fileName, label, size, uploader, registeredDate, updatedDate) <> (ReleaseAsset.tupled, ReleaseAsset.unapply)

    def byPrimaryKey(owner: String, repository: String, releaseId: Int, fileName: String) = byRelease(owner, repository, releaseId) && (this.fileName === fileName.bind)
  }

}

case class ReleaseAsset(
  userName: String,
  repositoryName: String,
  releaseId: Int,
  fileName: String,
  label: String,
  size: Long,
  uploader: String,
  registeredDate: Date,
  updatedDate: Date
)
