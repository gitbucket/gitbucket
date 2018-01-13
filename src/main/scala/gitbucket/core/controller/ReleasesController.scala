package gitbucket.core.controller

import java.io.File

import gitbucket.core.service.{AccountService, ActivityService, ReleaseService, RepositoryService}
import gitbucket.core.util.{FileUtil, ReadableUsersAuthenticator, ReferrerAuthenticator, WritableUsersAuthenticator}
import gitbucket.core.util.Directory._
import gitbucket.core.util.Implicits._
import org.scalatra.forms._
import gitbucket.core.releases.html
import org.apache.commons.io.FileUtils
import scala.collection.JavaConverters._

class ReleaseController extends ReleaseControllerBase
  with RepositoryService
  with AccountService
  with ReleaseService
  with ActivityService
  with ReadableUsersAuthenticator
  with ReferrerAuthenticator
  with WritableUsersAuthenticator

trait ReleaseControllerBase extends ControllerBase {
  self: RepositoryService
    with AccountService
    with ReleaseService
    with ReadableUsersAuthenticator
    with ReferrerAuthenticator
    with WritableUsersAuthenticator
    with ActivityService =>

  case class ReleaseForm(
    name: String,
    content: Option[String]
  )

  val releaseForm = mapping(
    "name"    -> trim(text(required)),
    "content" -> trim(optional(text()))
  )(ReleaseForm.apply)

  get("/:owner/:repository/releases")(referrersOnly {repository =>
    val releases = getReleases(repository.owner, repository.name)
    val assets = getReleaseAssetsMap(repository.owner, repository.name)

    html.list(
      repository,
      repository.tags.reverse.map { tag =>
        (tag, releases.find(_.tag == tag.name).map { release => (release, assets(release)) })
      },
      hasDeveloperRole(repository.owner, repository.name, context.loginAccount))
  })

  get("/:owner/:repository/releases/:tag")(referrersOnly { repository =>
    val tag = params("tag")
    getRelease(repository.owner, repository.name, tag).map { release =>
      html.release(release, getReleaseAssets(repository.owner, repository.name, tag), hasDeveloperRole(repository.owner, repository.name, context.loginAccount), repository)
    }.getOrElse(NotFound())
  })

  get("/:owner/:repository/releases/:tag/assets/:fileId")(referrersOnly {repository =>
    val tag = params("tag")
    val fileId = params("fileId")
    (for {
      _     <- repository.tags.find(_.name == tag)
      _     <- getRelease(repository.owner, repository.name, tag)
      asset <- getReleaseAsset(repository.owner, repository.name, tag, fileId)
    } yield {
      response.setHeader("Content-Disposition", s"attachment; filename=${asset.label}")
      RawData(
        FileUtil.getMimeType(asset.label),
        new File(getReleaseFilesDir(repository.owner, repository.name), tag + "/" + fileId)
      )
    }).getOrElse(NotFound())
  })

  get("/:owner/:repository/releases/:tag/create")(writableUsersOnly {repository =>
    html.form(repository, params("tag"), None)
  })

  post("/:owner/:repository/releases/:tag/create", releaseForm)(writableUsersOnly { (form, repository) =>
    val tag = params("tag")
    val loginAccount = context.loginAccount.get

    // Insert into RELEASE
    createRelease(repository.owner, repository.name, form.name, form.content, tag, loginAccount)

    // Insert into RELEASE_ASSET
    request.getParameterNames.asScala.filter(_.startsWith("file:")).foreach { paramName =>
      val Array(_, fileId) = paramName.split(":")
      val fileName = params(paramName)
      val size = new java.io.File(getReleaseFilesDir(repository.owner, repository.name), tag + "/" + fileId).length

      createReleaseAsset(repository.owner, repository.name, tag, fileId, fileName, size, loginAccount)
    }

    recordReleaseActivity(repository.owner, repository.name, loginAccount.userName, form.name)

    redirect(s"/${repository.owner}/${repository.name}/releases/${tag}")
  })

  get("/:owner/:repository/releases/:tag/edit")(writableUsersOnly {repository =>
    val tag = params("tag")

    getRelease(repository.owner, repository.name, tag).map { release =>
      html.form(repository, release.tag, Some(release, getReleaseAssets(repository.owner, repository.name, tag)))
    }.getOrElse(NotFound())
  })

  post("/:owner/:repository/releases/:tag/edit", releaseForm)(writableUsersOnly { (form, repository) =>
    val tag = params("tag")
    val loginAccount = context.loginAccount.get

    getRelease(repository.owner, repository.name, tag).map { release =>
      // Update RELEASE
      updateRelease(repository.owner, repository.name, tag, form.name, form.content)

      // Delete and Insert RELEASE_ASSET
      deleteReleaseAssets(repository.owner, repository.name, tag)

      request.getParameterNames.asScala.filter(_.startsWith("file:")).foreach { paramName =>
        val Array(_, fileId) = paramName.split(":")
        val fileName = params(paramName)
        val size = new java.io.File(getReleaseFilesDir(repository.owner, repository.name), release.tag + "/" + fileId).length

        createReleaseAsset(repository.owner, repository.name, tag, fileId, fileName, size, loginAccount)
      }

      redirect(s"/${release.userName}/${release.repositoryName}/releases/${tag}")
    }.getOrElse(NotFound())
  })

  post("/:owner/:repository/releases/:tag/delete")(writableUsersOnly { repository =>
    val tag = params("tag")
    getRelease(repository.owner, repository.name, tag).foreach { release =>
      FileUtils.deleteDirectory(new File(getReleaseFilesDir(repository.owner, repository.name), release.tag))
    }
    deleteRelease(repository.owner, repository.name, tag)
    redirect(s"/${repository.owner}/${repository.name}/releases")
  })

}
