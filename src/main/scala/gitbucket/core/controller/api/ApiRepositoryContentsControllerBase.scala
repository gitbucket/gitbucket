package gitbucket.core.controller.api
import java.util.Base64

import gitbucket.core.api.{ApiContents, ApiError, CreateAFile, JsonFormat}
import gitbucket.core.controller.ControllerBase
import gitbucket.core.service.{RepositoryCommitFileService, RepositoryService}
import gitbucket.core.util.Directory.getRepositoryDir
import gitbucket.core.util.JGitUtil.getContentFromId
import gitbucket.core.util._
import gitbucket.core.view.helpers.isRenderable
import gitbucket.core.util.Implicits._
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.FileMode
import org.eclipse.jgit.revwalk.{RevCommit, RevWalk}
import org.eclipse.jgit.treewalk.TreeWalk

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
          .find {
            case (_, name, _, _) =>
              RepositoryService.readmeFiles.contains(name.toLowerCase)
          } match {
          case Some((_, name, _, _)) =>
            getContents(repository = repository, path = name, refStr = refStr)
          case _ => NotFound()
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
    refStr: String
  ) = {
    println("getContents: " + path)
    Using.resource(Git.open(getRepositoryDir(params("owner"), params("repository")))) { git =>
      val fileName = path.split("/").last
      val revCommit = JGitUtil.getRevCommitFromId(git, git.getRepository.resolve(refStr))
      getPathObjectId(git, path, revCommit)
        .flatMap { objectId =>
          println(objectId)
          val largeFile = params.get("large_file").exists(s => s.equals("true"))
          val content = getContentFromId(git, objectId, largeFile)
          request.getHeader("Accept") match {
            case "application/vnd.github.v3.raw" => {
              contentType = "application/vnd.github.v3.raw"
              content
            }
            case "application/vnd.github.v3.html" if isRenderable(fileName) => {
              contentType = "application/vnd.github.v3.html"
              content.map { c =>
                gitbucket.core.api.html.contents_html_renderable(path, refStr, c, repository).body
              }
            }
            case "application/vnd.github.v3.html" => {
              contentType = "application/vnd.github.v3.html"
              content.map { c =>
                gitbucket.core.api.html.contents_html(path, c).body
              }
            }
            case _ =>
              Some(
                JsonFormat(
                  ApiContents(
                    "file",
                    fileName,
                    path,
                    revCommit.getName,
                    content.map(Base64.getEncoder.encodeToString),
                    Some("base64")
                  )(RepositoryName(repository))
                )
              )
          }
        }
        .getOrElse {
          val fileList = getFileList(git, refStr, path, maxFiles = context.settings.repositoryViewer.maxFiles)
          if (fileList.isEmpty) {
            NotFound()
          } else {
            JsonFormat(fileList.map {
              case (mode, name, path, commit) =>
                val fileType = if (mode == FileMode.TREE) {
                  "dir"
                } else {
                  "file"
                }
                ApiContents(fileType, name, path, commit.getName, None, None)(RepositoryName(repository))
            })
          }
        }
    }
  }

  private def getFileList(
    git: Git,
    revision: String,
    path: String = ".",
    maxFiles: Int = 100
  ): List[(FileMode, String, String, RevCommit)] = {
    Using.resource(new RevWalk(git.getRepository)) { revWalk =>
      val objectId = git.getRepository.resolve(revision)
      if (objectId == null) return Nil
      val revCommit = revWalk.parseCommit(objectId)

      def useTreeWalk(rev: RevCommit)(f: TreeWalk => Any): Unit =
        if (path == ".") {
          val treeWalk = new TreeWalk(git.getRepository)
          treeWalk.addTree(rev.getTree)
          Using.resource(treeWalk)(f)
        } else {
          val treeWalk = TreeWalk.forPath(git.getRepository, path, rev.getTree)
          if (treeWalk != null) {
            treeWalk.enterSubtree
            Using.resource(treeWalk)(f)
          }
        }

      def getCommit(path: String): RevCommit = {
        git
          .log()
          .addPath(path)
          .add(revCommit)
          .setMaxCount(1)
          .call()
          .iterator()
          .next()
      }

      var fileList: List[(FileMode, String, String, RevCommit)] = Nil
      useTreeWalk(revCommit) { treeWalk =>
        while (treeWalk.next()) {
          fileList +:= (
            treeWalk.getFileMode(0),
            treeWalk.getNameString,
            treeWalk.getPathString,
            getCommit(path)
          )
        }
      }

      fileList
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
