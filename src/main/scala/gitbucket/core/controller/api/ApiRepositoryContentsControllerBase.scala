package gitbucket.core.controller.api
import gitbucket.core.api.{ApiContents, JsonFormat}
import gitbucket.core.controller.ControllerBase
import gitbucket.core.service.{AccountService, ProtectedBranchService, RepositoryService}
import gitbucket.core.util.Directory.getRepositoryDir
import gitbucket.core.util.JGitUtil.{FileInfo, getContentFromId, getFileList}
import gitbucket.core.util._
import gitbucket.core.util.SyntaxSugars.using
import gitbucket.core.view.helpers.{isRenderable, renderMarkup}
import gitbucket.core.util.Implicits._
import org.eclipse.jgit.api.Git

trait ApiRepositoryContentsControllerBase extends ControllerBase {
  self: ReferrerAuthenticator =>

  /*
   * i. Get the README
   * https://developer.github.com/v3/repos/contents/#get-the-readme
   */

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

  private def getContents(repository: RepositoryService.RepositoryInfo, path: String, refStr: String) = {
    def getFileInfo(git: Git, revision: String, pathStr: String): Option[FileInfo] = {
      val (dirName, fileName) = pathStr.lastIndexOf('/') match {
        case -1 =>
          (".", pathStr)
        case n =>
          (pathStr.take(n), pathStr.drop(n + 1))
      }
      getFileList(git, revision, dirName).find(f => f.name.equals(fileName))
    }

    using(Git.open(getRepositoryDir(params("owner"), params("repository")))) { git =>
      val fileList = getFileList(git, refStr, path)
      if (fileList.isEmpty) { // file or NotFound
        getFileInfo(git, refStr, path)
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
   * iii. Create a file
   * https://developer.github.com/v3/repos/contents/#create-a-file
   */

  /*
   * iv. Update a file
   * https://developer.github.com/v3/repos/contents/#update-a-file
   */

  /*
   * v. Delete a file
   * https://developer.github.com/v3/repos/contents/#delete-a-file
   */

  /*
 * vi. Get archive link
 * https://developer.github.com/v3/repos/contents/#get-archive-link
 */

}
