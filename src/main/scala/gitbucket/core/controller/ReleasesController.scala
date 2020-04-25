package gitbucket.core.controller

import java.io.File

import gitbucket.core.service.{AccountService, ActivityService, PaginationHelper, ReleaseService, RepositoryService}
import gitbucket.core.util._
import gitbucket.core.util.Directory._
import gitbucket.core.util.Implicits._
import org.scalatra.forms._
import gitbucket.core.releases.html
import org.apache.commons.io.FileUtils
import org.eclipse.jgit.api.Git

import scala.util.Using

class ReleaseController
    extends ReleaseControllerBase
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
    "name" -> trim(text(required)),
    "content" -> trim(optional(text()))
  )(ReleaseForm.apply)

  get("/:owner/:repository/releases")(referrersOnly { repository =>
    val page = PaginationHelper.page(params.get("page"))

    html.list(
      repository,
      fetchReleases(repository, page),
      hasDeveloperRole(repository.owner, repository.name, context.loginAccount),
      page,
      repository.tags.size
    )
  })

  get("/:owner/:repository/releases/:tag")(referrersOnly { repository =>
    val tagName = params("tag")
    getRelease(repository.owner, repository.name, tagName)
      .map { release =>
        html.release(
          release,
          getReleaseAssets(repository.owner, repository.name, tagName),
          hasDeveloperRole(repository.owner, repository.name, context.loginAccount),
          repository
        )
      }
      .getOrElse(NotFound())
  })

  get("/:owner/:repository/releases/:tag/assets/:fileId")(referrersOnly { repository =>
    val tagName = params("tag")
    val fileId = params("fileId")
    (for {
      _ <- repository.tags.find(_.name == tagName)
      _ <- getRelease(repository.owner, repository.name, tagName)
      asset <- getReleaseAsset(repository.owner, repository.name, tagName, fileId)
    } yield {
      response.setHeader("Content-Disposition", s"attachment; filename=${asset.label}")
      RawData(
        FileUtil.getSafeMimeType(asset.label),
        new File(getReleaseFilesDir(repository.owner, repository.name), FileUtil.checkFilename(tagName + "/" + fileId))
      )
    }).getOrElse(NotFound())
  })

  get("/:owner/:repository/releases/:tag/create")(writableUsersOnly { repository =>
    val tagName = params("tag")
    val previousTags = repository.tags.takeWhile(_.name != tagName).reverse

    repository.tags
      .find(_.name == tagName)
      .map { tag =>
        html.form(repository, tag, previousTags.map(_.name), tag.message, None)
      }
      .getOrElse(NotFound())
  })

  post("/:owner/:repository/releases/:tag/create", releaseForm)(writableUsersOnly { (form, repository) =>
    val tagName = params("tag")
    val loginAccount = context.loginAccount.get

    // Insert into RELEASE
    createRelease(repository.owner, repository.name, form.name, form.content, tagName, loginAccount)

    // Insert into RELEASE_ASSET
    val files = params.toMap.collect {
      case (name, value) if name.startsWith("file:") =>
        val Array(_, fileId) = name.split(":")
        (fileId, value)
    }
    files.foreach {
      case (fileId, fileName) =>
        val size =
          new File(
            getReleaseFilesDir(repository.owner, repository.name),
            FileUtil.checkFilename(tagName + "/" + fileId)
          ).length
        createReleaseAsset(repository.owner, repository.name, tagName, fileId, fileName, size, loginAccount)
    }

    recordReleaseActivity(repository.owner, repository.name, loginAccount.userName, form.name, tagName)

    redirect(s"/${repository.owner}/${repository.name}/releases/${tagName}")
  })

  get("/:owner/:repository/changelog/*...*")(writableUsersOnly { repository =>
    val Seq(previousTag, currentTag) = multiParams("splat")
    val previousTagId = repository.tags.collectFirst { case x if x.name == previousTag => x.id }.getOrElse("")

    val commitLog = Using.resource(Git.open(getRepositoryDir(repository.owner, repository.name))) { git =>
      val commits = JGitUtil.getCommitLog(git, previousTagId, currentTag).reverse
      commits
        .map { commit =>
          s"- ${commit.shortMessage} ${commit.id}"
        }
        .mkString("\n")
    }

    commitLog
  })

  get("/:owner/:repository/releases/:tag/edit")(writableUsersOnly { repository =>
    val tagName = params("tag")
    val previousTags = repository.tags.takeWhile(_.name != tagName).reverse

    (for {
      release <- getRelease(repository.owner, repository.name, tagName)
      tag <- repository.tags.find(_.name == tagName)
    } yield {
      html.form(
        repository,
        tag,
        previousTags.map(_.name),
        release.content.getOrElse(""),
        Some(release, getReleaseAssets(repository.owner, repository.name, tagName))
      )
    }).getOrElse(NotFound())
  })

  post("/:owner/:repository/releases/:tag/edit", releaseForm)(writableUsersOnly {
    (form, repository) =>
      val tagName = params("tag")
      val loginAccount = context.loginAccount.get

      getRelease(repository.owner, repository.name, tagName)
        .map { release =>
          // Update RELEASE
          updateRelease(repository.owner, repository.name, tagName, form.name, form.content)

          // Delete and Insert RELEASE_ASSET
          val assets = getReleaseAssets(repository.owner, repository.name, tagName)
          deleteReleaseAssets(repository.owner, repository.name, tagName)

          val files = params.toMap.collect {
            case (name, value) if name.startsWith("file:") =>
              val Array(_, fileId) = name.split(":")
              (fileId, value)
          }
          files.foreach {
            case (fileId, fileName) =>
              val size =
                new File(
                  getReleaseFilesDir(repository.owner, repository.name),
                  FileUtil.checkFilename(tagName + "/" + fileId)
                ).length
              createReleaseAsset(repository.owner, repository.name, tagName, fileId, fileName, size, loginAccount)
          }

          assets.foreach { asset =>
            if (!files.exists { case (fileId, _) => fileId == asset.fileName }) {
              val file = new File(
                getReleaseFilesDir(repository.owner, repository.name),
                FileUtil.checkFilename(release.tag + "/" + asset.fileName)
              )
              FileUtils.forceDelete(file)
            }
          }

          redirect(s"/${release.userName}/${release.repositoryName}/releases/${tagName}")
        }
        .getOrElse(NotFound())
  })

  post("/:owner/:repository/releases/:tag/delete")(writableUsersOnly { repository =>
    val tagName = params("tag")
    getRelease(repository.owner, repository.name, tagName).foreach { release =>
      FileUtils.deleteDirectory(
        new File(getReleaseFilesDir(repository.owner, repository.name), FileUtil.checkFilename(release.tag))
      )
    }
    deleteRelease(repository.owner, repository.name, tagName)
    redirect(s"/${repository.owner}/${repository.name}/releases")
  })

  private def fetchReleases(repository: RepositoryService.RepositoryInfo, page: Int) = {

    import gitbucket.core.service.ReleaseService._

    val (offset, limit) = ((page - 1) * ReleaseLimit, ReleaseLimit)
    val tagsToDisplay = repository.tags.reverse.slice(offset, offset + limit)

    val releases = getReleases(repository.owner, repository.name, tagsToDisplay)
    val assets = getReleaseAssetsMap(repository.owner, repository.name, releases)

    val tagsWithReleases = tagsToDisplay.map { tag =>
      (tag, releases.find(_.tag == tag.name).map { release =>
        (release, assets(release))
      })
    }
    tagsWithReleases
  }
}
