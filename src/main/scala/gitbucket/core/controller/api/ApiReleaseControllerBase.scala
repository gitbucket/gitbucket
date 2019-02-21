package gitbucket.core.controller.api
import java.io.{ByteArrayInputStream, File}

import gitbucket.core.api._
import gitbucket.core.controller.ControllerBase
import gitbucket.core.service.{AccountService, ReleaseService}
import gitbucket.core.util.Directory.getReleaseFilesDir
import gitbucket.core.util.{FileUtil, ReferrerAuthenticator, RepositoryName, WritableUsersAuthenticator}
import gitbucket.core.util.Implicits._
import gitbucket.core.util.SyntaxSugars.defining
import org.apache.commons.io.FileUtils
import org.scalatra.{Created, NoContent}

trait ApiReleaseControllerBase extends ControllerBase {
  self: AccountService with ReleaseService with ReferrerAuthenticator with WritableUsersAuthenticator =>

  /**
   * i. List releases for a repository
   * https://developer.github.com/v3/repos/releases/#list-releases-for-a-repository
   */
  get("/api/v3/repos/:owner/:repository/releases")(referrersOnly { repository =>
    val releases = getReleases(repository.owner, repository.name)
    JsonFormat(releases.map { rel =>
      val assets = getReleaseAssets(repository.owner, repository.name, rel.tag)
      ApiRelease(rel, assets, getAccountByUserName(rel.author).get, RepositoryName(repository))
    })
  })

  /**
   * ii. Get a single release
   * https://developer.github.com/v3/repos/releases/#get-a-single-release
   * GitBucket doesn't have release id
   */
  /**
   * iii. Get the latest release
   * https://developer.github.com/v3/repos/releases/#get-the-latest-release
   */
  get("/api/v3/repos/:owner/:repository/releases/latest")(referrersOnly { repository =>
    getReleases(repository.owner, repository.name).lastOption
      .map { release =>
        val assets = getReleaseAssets(repository.owner, repository.name, release.tag)
        JsonFormat(ApiRelease(release, assets, getAccountByUserName(release.author).get, RepositoryName(repository)))
      }
      .getOrElse {
        NotFound()
      }
  })

  /**
   * iv. Get a release by tag name
   * https://developer.github.com/v3/repos/releases/#get-a-release-by-tag-name
   */
  get("/api/v3/repos/:owner/:repository/releases/tags/:tag")(referrersOnly { repository =>
    val tag = params("tag")
    getRelease(repository.owner, repository.name, tag)
      .map { release =>
        val assets = getReleaseAssets(repository.owner, repository.name, tag)
        JsonFormat(ApiRelease(release, assets, getAccountByUserName(release.author).get, RepositoryName(repository)))
      }
      .getOrElse {
        NotFound()
      }
  })

  /**
   * v. Create a release
   * https://developer.github.com/v3/repos/releases/#create-a-release
   */
  post("/api/v3/repos/:owner/:repository/releases")(writableUsersOnly { repository =>
    (for {
      data <- extractFromJsonBody[CreateARelease]
    } yield {
      createRelease(
        repository.owner,
        repository.name,
        data.name.getOrElse(data.tag_name),
        data.body,
        data.tag_name,
        context.loginAccount.get
      )
      val release = getRelease(repository.owner, repository.name, data.tag_name).get
      val assets = getReleaseAssets(repository.owner, repository.name, data.tag_name)
      JsonFormat(ApiRelease(release, assets, context.loginAccount.get, RepositoryName(repository)))
    })
  })

  /**
   * vi. Edit a release
   * https://developer.github.com/v3/repos/releases/#edit-a-release
   * Incompatiblity info: GitHub API requires :release_id, but GitBucket API requires :tag_name
   */
  patch("/api/v3/repos/:owner/:repository/releases/:tag")(writableUsersOnly { repository =>
    (for {
      data <- extractFromJsonBody[CreateARelease]
    } yield {
      val tag = params("tag")
      updateRelease(repository.owner, repository.name, tag, data.name.getOrElse(data.tag_name), data.body)
      val release = getRelease(repository.owner, repository.name, data.tag_name).get
      val assets = getReleaseAssets(repository.owner, repository.name, data.tag_name)
      JsonFormat(ApiRelease(release, assets, context.loginAccount.get, RepositoryName(repository)))
    })
  })

  /**
   * vii. Delete a release
   * https://developer.github.com/v3/repos/releases/#delete-a-release
   * Incompatiblity info: GitHub API requires :release_id, but GitBucket API requires :tag_name
   */
  delete("/api/v3/repos/:owner/:repository/releases/:tag")(writableUsersOnly { repository =>
    val tag = params("tag")
    deleteRelease(repository.owner, repository.name, tag)
    NoContent()
  })

  /**
   * viii. List assets for a release
   * https://developer.github.com/v3/repos/releases/#list-assets-for-a-release
   */
  /**
   * ix. Upload a release asset
   * https://developer.github.com/v3/repos/releases/#upload-a-release-asset
   */
  post("/api/v3/repos/:owner/:repository/releases/:tag/assets")(writableUsersOnly { repository =>
    val name = params("name")
    val tag = params("tag")
    getRelease(repository.owner, repository.name, tag)
      .map {
        release =>
          defining(FileUtil.generateFileId) { fileId =>
            val buf = new Array[Byte](request.inputStream.available())
            request.inputStream.read(buf)
            FileUtils.writeByteArrayToFile(
              new File(
                getReleaseFilesDir(repository.owner, repository.name),
                FileUtil.checkFilename(tag + "/" + fileId)
              ),
              buf
            )
            createReleaseAsset(
              repository.owner,
              repository.name,
              tag,
              fileId,
              name,
              request.contentLength.getOrElse(0),
              context.loginAccount.get
            )
            getReleaseAsset(repository.owner, repository.name, tag, fileId)
              .map { asset =>
                JsonFormat(ApiReleaseAsset(asset, RepositoryName(repository)))
              }
              .getOrElse {
                ApiError("Unknown error")
              }
          }
      }
      .getOrElse(NotFound())
  })

  /**
   * x. Get a single release asset
   * https://developer.github.com/v3/repos/releases/#get-a-single-release-asset
   * Incompatibility info: GitHub requires only asset_id, but GitBucket requires tag and fileId(file_id).
   */
  get("/api/v3/repos/:owner/:repository/releases/:tag/assets/:fileId")(referrersOnly { repository =>
    val tag = params("tag")
    val fileId = params("fileId")
    getReleaseAsset(repository.owner, repository.name, tag, fileId)
      .map { asset =>
        JsonFormat(ApiReleaseAsset(asset, RepositoryName(repository)))
      }
      .getOrElse(NotFound())
  })

  /*
   * xi. Edit a release asset
   * https://developer.github.com/v3/repos/releases/#edit-a-release-asset
   */

  /*
 * xii. Delete a release asset
 * https://developer.github.com/v3/repos/releases/#edit-a-release-asset
 */
}
