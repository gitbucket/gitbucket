package gitbucket.core.api
import gitbucket.core.model.{Account, ReleaseAsset, ReleaseTag}
import gitbucket.core.util.RepositoryName

case class ApiReleaseAsset(name: String, size: Long)(asset: ReleaseAsset, repositoryName: RepositoryName) {
  val label = name
  val file_id = asset.fileName
  val browser_download_url = ApiPath(
    s"/api/v3/repos/${repositoryName.fullName}/releases/${asset.tag}/assets/${asset.fileName}"
  )
}

object ApiReleaseAsset {
  def apply(asset: ReleaseAsset, repositoryName: RepositoryName): ApiReleaseAsset =
    ApiReleaseAsset(asset.label, asset.size)(asset, repositoryName)
}

case class ApiRelease(
  name: String,
  tag_name: String,
  body: Option[String],
  author: ApiUser,
  assets: Seq[ApiReleaseAsset]
)

object ApiRelease {
  def apply(
    release: ReleaseTag,
    assets: Seq[ReleaseAsset],
    author: Account,
    repositoryName: RepositoryName
  ): ApiRelease =
    ApiRelease(
      release.name,
      release.tag,
      release.content,
      ApiUser(author),
      assets.map { asset =>
        ApiReleaseAsset(asset, repositoryName)
      }
    )
}
