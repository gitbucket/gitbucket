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
    val tagName = params("tag")
    getRelease(repository.owner, repository.name, tagName).map { release =>
      html.release(release, getReleaseAssets(repository.owner, repository.name, tagName),
        hasDeveloperRole(repository.owner, repository.name, context.loginAccount), repository)
    }.getOrElse(NotFound())
  })

  get("/:owner/:repository/releases/:tag/assets/:fileId")(referrersOnly {repository =>
    val tagName = params("tag")
    val fileId = params("fileId")
    (for {
      _     <- repository.tags.find(_.name == tagName)
      _     <- getRelease(repository.owner, repository.name, tagName)
      asset <- getReleaseAsset(repository.owner, repository.name, tagName, fileId)
    } yield {
      response.setHeader("Content-Disposition", s"attachment; filename=${asset.label}")
      RawData(
        FileUtil.getMimeType(asset.label),
        new File(getReleaseFilesDir(repository.owner, repository.name), tagName + "/" + fileId)
      )
    }).getOrElse(NotFound())
  })

  get("/:owner/:repository/releases/:tag/create")(writableUsersOnly {repository =>
    val tagName = params("tag")
    repository.tags.find(_.name == tagName).map { tag =>
      html.form(repository, tag, None)
    }.getOrElse(NotFound())
  })

  post("/:owner/:repository/releases/:tag/create", releaseForm)(writableUsersOnly { (form, repository) =>
    val tagName = params("tag")
    val loginAccount = context.loginAccount.get

    // Insert into RELEASE
    createRelease(repository.owner, repository.name, form.name, form.content, tagName, loginAccount)

    // Insert into RELEASE_ASSET
    val files = params.collect { case (name, value) if name.startsWith("file:") =>
      val Array(_, fileId) = name.split(":")
      (fileId, value)
    }
    files.foreach { case (fileId, fileName) =>
      val size = new java.io.File(getReleaseFilesDir(repository.owner, repository.name), tagName + "/" + fileId).length
      createReleaseAsset(repository.owner, repository.name, tagName, fileId, fileName, size, loginAccount)
    }

    recordReleaseActivity(repository.owner, repository.name, loginAccount.userName, form.name)

    redirect(s"/${repository.owner}/${repository.name}/releases/${tagName}")
  })

  get("/:owner/:repository/releases/:tag/edit")(writableUsersOnly {repository =>
    val tagName = params("tag")

    (for {
      release <- getRelease(repository.owner, repository.name, tagName)
      tag     <- repository.tags.find(_.name == tagName)
    } yield {
      html.form(repository, tag, Some(release, getReleaseAssets(repository.owner, repository.name, tagName)))
    }).getOrElse(NotFound())
  })

  post("/:owner/:repository/releases/:tag/edit", releaseForm)(writableUsersOnly { (form, repository) =>
    val tagName = params("tag")
    val loginAccount = context.loginAccount.get

    getRelease(repository.owner, repository.name, tagName).map { release =>
      // Update RELEASE
      updateRelease(repository.owner, repository.name, tagName, form.name, form.content)

      // Delete and Insert RELEASE_ASSET
      val assets = getReleaseAssets(repository.owner, repository.name, tagName)
      deleteReleaseAssets(repository.owner, repository.name, tagName)

      val files = params.collect { case (name, value) if name.startsWith("file:") =>
        val Array(_, fileId) = name.split(":")
        (fileId, value)
      }
      files.foreach { case (fileId, fileName) =>
        val size = new java.io.File(getReleaseFilesDir(repository.owner, repository.name), tagName + "/" + fileId).length
        createReleaseAsset(repository.owner, repository.name, tagName, fileId, fileName, size, loginAccount)
      }

      assets.foreach { asset =>
        if(!files.exists { case (fileId, _) => fileId == asset.fileName }){
          val file = new java.io.File(getReleaseFilesDir(repository.owner, repository.name), release.tag + "/" + asset.fileName)
          FileUtils.forceDelete(file)
        }
      }

      redirect(s"/${release.userName}/${release.repositoryName}/releases/${tagName}")
    }.getOrElse(NotFound())
  })

  post("/:owner/:repository/releases/:tag/delete")(writableUsersOnly { repository =>
    val tagName = params("tag")
    getRelease(repository.owner, repository.name, tagName).foreach { release =>
      FileUtils.deleteDirectory(new File(getReleaseFilesDir(repository.owner, repository.name), release.tag))
    }
    deleteRelease(repository.owner, repository.name, tagName)
    redirect(s"/${repository.owner}/${repository.name}/releases")
  })

}
