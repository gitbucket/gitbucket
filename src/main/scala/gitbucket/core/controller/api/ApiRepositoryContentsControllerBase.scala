package gitbucket.core.controller.api
import gitbucket.core.api.{ApiContents, ApiError, CreateAFile, JsonFormat}
import gitbucket.core.controller.ControllerBase
import gitbucket.core.plugin.PluginRegistry
import gitbucket.core.service.{RepositoryCommitFileService, RepositoryService}
import gitbucket.core.util.Directory.getRepositoryDir
import gitbucket.core.util.JGitUtil.{FileInfo, getContentFromId, getFileList}
import gitbucket.core.util._
import gitbucket.core.view.helpers.{isRenderable, renderMarkup}
import gitbucket.core.util.Implicits._
import org.eclipse.jgit.api.Git

import scala.util.Using

trait ApiRepositoryContentsControllerBase extends ControllerBase {
  self: ReferrerAuthenticator with WritableUsersAuthenticator with RepositoryCommitFileService =>

  /**
   * i. Get a repository README
   * https://docs.github.com/en/rest/reference/repos#get-a-repository-readme
   */
  get("/api/v3/repos/:owner/:repository/readme")(referrersOnly { repository =>
    Using.resource(Git.open(getRepositoryDir(params("owner"), params("repository")))) {
      git =>
        val refStr = params.getOrElse("ref", repository.repository.defaultBranch)
        val files = getFileList(git, refStr, ".", maxFiles = context.settings.repositoryViewer.maxFiles)
        files // files should be sorted alphabetically.
          .find { file =>
            !file.isDirectory && RepositoryService.readmeFiles.contains(file.name.toLowerCase)
          } match {
          case Some(x) => getContents(repository = repository, path = x.name, refStr = refStr, ignoreCase = true)
          case _       => NotFound()
        }
    }
  })

  /**
   * ii. Get contents
   * https://developer.github.com/v3/repos/contents/#get-contents
   */
  get("/api/v3/repos/:owner/:repository/contents")(referrersOnly { repository =>
    getContents(repository, ".", params.getOrElse("ref", repository.repository.defaultBranch))
  })

  /**
   * ii. Get contents
   * https://developer.github.com/v3/repos/contents/#get-contents
   */
  get("/api/v3/repos/:owner/:repository/contents/*")(referrersOnly { repository =>
    getContents(repository, multiParams("splat").head, params.getOrElse("ref", repository.repository.defaultBranch))
  })

  private def getContents(
    repository: RepositoryService.RepositoryInfo,
    path: String,
    refStr: String,
    ignoreCase: Boolean = false
  ) = {
    def getFileInfo(git: Git, revision: String, pathStr: String, ignoreCase: Boolean): Option[FileInfo] = {
      val (dirName, fileName) = pathStr.lastIndexOf('/') match {
        case -1 =>
          (".", pathStr)
        case n =>
          (pathStr.take(n), pathStr.drop(n + 1))
      }
      if (ignoreCase) {
        getFileList(git, revision, dirName, maxFiles = context.settings.repositoryViewer.maxFiles)
          .find(_.name.toLowerCase.equals(fileName.toLowerCase))
      } else {
        getFileList(git, revision, dirName, maxFiles = context.settings.repositoryViewer.maxFiles)
          .find(_.name.equals(fileName))
      }
    }

    Using.resource(Git.open(getRepositoryDir(params("owner"), params("repository")))) { git =>
      val fileList = getFileList(git, refStr, path, maxFiles = context.settings.repositoryViewer.maxFiles)
      if (fileList.isEmpty) { // file or NotFound
        getFileInfo(git, refStr, path, ignoreCase)
          .flatMap { f =>
            val largeFile = params.get("large_file").exists(s => s.equals("true"))
            val content = getContentFromId(git, f.id, largeFile)
            request.getHeader("Accept") match {
              case "application/vnd.github.v3.raw" => {
                contentType = "application/vnd.github.v3.raw"
                content
              }
              case "application/vnd.github.v3.html" if isRenderable(f.name) => {
                contentType = "application/vnd.github.v3.html"
                content.map { c =>
                  List(
                    "<div data-path=\"",
                    path,
                    "\" id=\"file\">",
                    "<article>",
                    renderMarkup(path.split("/").toList, new String(c), refStr, repository, false, false, true).body,
                    "</article>",
                    "</div>"
                  ).mkString
                }
              }
              case "application/vnd.github.v3.html" => {
                contentType = "application/vnd.github.v3.html"
                content.map { c =>
                  List(
                    "<div data-path=\"",
                    path,
                    "\" id=\"file\">",
                    "<div class=\"plain\">",
                    "<pre>",
                    play.twirl.api.HtmlFormat.escape(new String(c)).body,
                    "</pre>",
                    "</div>",
                    "</div>"
                  ).mkString
                }
              }
              case _ =>
                Some(JsonFormat(ApiContents(f, RepositoryName(repository), content)))
            }
          }
          .getOrElse(NotFound())

      } else { // directory
        JsonFormat(fileList.map { f =>
          ApiContents(f, RepositoryName(repository), None)
        })
      }
    }
  }
  /*
   * iii. Create a file or iv. Update a file
   * https://developer.github.com/v3/repos/contents/#create-a-file
   * https://developer.github.com/v3/repos/contents/#update-a-file
   * if sha is presented, update a file else create a file.
   * requested #2112
   */

  put("/api/v3/repos/:owner/:repository/contents/*")(writableUsersOnly { repository =>
    JsonFormat(for {
      data <- extractFromJsonBody[CreateAFile]
    } yield {
      val branch = data.branch.getOrElse(repository.repository.defaultBranch)
      val commit = Using.resource(Git.open(getRepositoryDir(repository.owner, repository.name))) { git =>
        val revCommit = JGitUtil.getRevCommitFromId(git, git.getRepository.resolve(branch))
        revCommit.name
      }
      val paths = multiParams("splat").head.split("/")
      val path = paths.take(paths.size - 1).toList.mkString("/")
      if (data.sha.isDefined && data.sha.get != commit) {
        ApiError("The blob SHA is not matched.", Some("https://developer.github.com/v3/repos/contents/#update-a-file"))
      } else {
        val objectId = commitFile(
          repository,
          branch,
          path,
          Some(paths.last),
          data.sha.map(_ => paths.last),
          StringUtil.base64Decode(data.content),
          data.message,
          commit,
          context.loginAccount.get,
          data.committer.map(_.name).getOrElse(context.loginAccount.get.fullName),
          data.committer.map(_.email).getOrElse(context.loginAccount.get.mailAddress),
          context.settings
        )
        ApiContents("file", paths.last, path, objectId.name, None, None)(RepositoryName(repository))
      }
    })
  })

  /*
   * v. Delete a file
   * https://developer.github.com/v3/repos/contents/#delete-a-file
   * should be implemented
   */

  /*
 * vi. Get archive link
 * https://developer.github.com/v3/repos/contents/#get-archive-link
 */

}
