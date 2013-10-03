package app

import util.Directory._
import util.Implicits._
import util.ControlUtil._
import _root_.util.{ReferrerAuthenticator, JGitUtil, FileUtil, StringUtil}
import service._
import org.scalatra._
import java.io.File
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib._
import org.apache.commons.io.FileUtils
import org.eclipse.jgit.treewalk._
import org.eclipse.jgit.api.errors.RefNotFoundException

class RepositoryViewerController extends RepositoryViewerControllerBase 
  with RepositoryService with AccountService with ReferrerAuthenticator

/**
 * The repository viewer.
 */
trait RepositoryViewerControllerBase extends ControllerBase { 
  self: RepositoryService with AccountService with ReferrerAuthenticator =>

  /**
   * Returns converted HTML from Markdown for preview.
   */
  post("/:owner/:repository/_preview")(referrersOnly { repository =>
    contentType = "text/html"
    view.helpers.markdown(params("content"), repository,
      params("enableWikiLink").toBoolean,
      params("enableRefsLink").toBoolean)
  })

  /**
   * Displays the file list of the repository root and the default branch.
   */
  get("/:owner/:repository")(referrersOnly {
    fileList(_)
  })
  
  /**
   * Displays the file list of the specified path and branch.
   */
  get("/:owner/:repository/tree/*")(referrersOnly { repository =>
    val (id, path) = splitPath(repository, multiParams("splat").head)
    if(path.isEmpty){
      fileList(repository, id)
    } else {
      fileList(repository, id, path)
    }
  })
  
  /**
   * Displays the commit list of the specified resource.
   */
  get("/:owner/:repository/commits/*")(referrersOnly { repository =>
    val (branchName, path) = splitPath(repository, multiParams("splat").head)
    val page = params.getOrElse("page", "1").toInt

    using(Git.open(getRepositoryDir(repository.owner, repository.name))){ git =>
      JGitUtil.getCommitLog(git, branchName, page, 30, path) match {
        case Right((logs, hasNext)) =>
          repo.html.commits(if(path.isEmpty) Nil else path.split("/").toList, branchName, repository,
            logs.splitWith{ (commit1, commit2) =>
              view.helpers.date(commit1.time) == view.helpers.date(commit2.time)
            }, page, hasNext)
        case Left(_) => NotFound
      }
    }
  })

  /**
   * Displays the file content of the specified branch or commit.
   */
  get("/:owner/:repository/blob/*")(referrersOnly { repository =>
    val (id, path) = splitPath(repository, multiParams("splat").head)
    val raw = params.get("raw").getOrElse("false").toBoolean

    using(Git.open(getRepositoryDir(repository.owner, repository.name))){ git =>
      val revCommit = JGitUtil.getRevCommitFromId(git, git.getRepository.resolve(id))

      @scala.annotation.tailrec
      def getPathObjectId(path: String, walk: TreeWalk): ObjectId = walk.next match {
        case true if(walk.getPathString == path) => walk.getObjectId(0)
        case true => getPathObjectId(path, walk)
      }

      val objectId = using(new TreeWalk(git.getRepository)){ treeWalk =>
        treeWalk.addTree(revCommit.getTree)
        treeWalk.setRecursive(true)
        getPathObjectId(path, treeWalk)
      }

      if(raw){
        // Download
        defining(JGitUtil.getContent(git, objectId, false).get){ bytes =>
          contentType = FileUtil.getContentType(path, bytes)
          bytes
        }
      } else {
        // Viewer
        val large  = FileUtil.isLarge(git.getRepository.getObjectDatabase.open(objectId).getSize)
        val viewer = if(FileUtil.isImage(path)) "image" else if(large) "large" else "other"
        val bytes  = if(viewer == "other") JGitUtil.getContent(git, objectId, false) else None

        val content = if(viewer == "other"){
          if(bytes.isDefined && FileUtil.isText(bytes.get)){
            // text
            JGitUtil.ContentInfo("text", bytes.map(StringUtil.convertFromByteArray))
          } else {
            // binary
            JGitUtil.ContentInfo("binary", None)
          }
        } else {
          // image or large
          JGitUtil.ContentInfo(viewer, None)
        }

        repo.html.blob(id, repository, path.split("/").toList, content, new JGitUtil.CommitInfo(revCommit))
      }
    }
  })
  
  /**
   * Displays details of the specified commit.
   */
  get("/:owner/:repository/commit/:id")(referrersOnly { repository =>
    val id = params("id")

    using(Git.open(getRepositoryDir(repository.owner, repository.name))){ git =>
      defining(JGitUtil.getRevCommitFromId(git, git.getRepository.resolve(id))){ revCommit =>
        JGitUtil.getDiffs(git, id) match { case (diffs, oldCommitId) =>
          repo.html.commit(id, new JGitUtil.CommitInfo(revCommit),
            JGitUtil.getBranchesOfCommit(git, revCommit.getName),
            JGitUtil.getTagsOfCommit(git, revCommit.getName),
            repository, diffs, oldCommitId)
        }
      }
    }
  })
  
  /**
   * Displays branches.
   */
  get("/:owner/:repository/branches")(referrersOnly { repository =>
    using(Git.open(getRepositoryDir(repository.owner, repository.name))){ git =>
      // retrieve latest update date of each branch
      val branchInfo = repository.branchList.map { branchName =>
        val revCommit = git.log.add(git.getRepository.resolve(branchName)).setMaxCount(1).call.iterator.next
        (branchName, revCommit.getCommitterIdent.getWhen)
      }
      repo.html.branches(branchInfo, repository)
    }
  })

  /**
   * Displays tags.
   */
  get("/:owner/:repository/tags")(referrersOnly {
    repo.html.tags(_)
  })

  /**
   * Download repository contents as an archive.
   */
  get("/:owner/:repository/archive/:name")(referrersOnly { repository =>
    val name = params("name")
    
    if(name.endsWith(".zip")){
      val revision = name.replaceFirst("\\.zip$", "")
      val workDir = getDownloadWorkDir(repository.owner, repository.name, session.getId)
      if(workDir.exists){
        FileUtils.deleteDirectory(workDir)
      }
      workDir.mkdirs
      
      // clone the repository
      val cloneDir = new File(workDir, revision)
      using(Git.cloneRepository
          .setURI(getRepositoryDir(repository.owner, repository.name).toURI.toString)
          .setDirectory(cloneDir)
          .setBranch(revision)
          .call){ git =>

        // checkout the specified revision
        git.checkout.setName(revision).call
      }
      
      // remove .git
      FileUtils.deleteDirectory(new File(cloneDir, ".git"))
      
      // create zip file
      val zipFile = new File(workDir, (if(revision.length == 40) revision.substring(0, 10) else revision) + ".zip")
      FileUtil.createZipFile(zipFile, cloneDir)
      
      contentType = "application/octet-stream"
      zipFile
    } else {
      BadRequest
    }
  })

  get("/:owner/:repository/network/members")(referrersOnly { repository =>
    repo.html.forked(
      getRepository(
        repository.repository.originUserName.getOrElse(repository.owner),
        repository.repository.originRepositoryName.getOrElse(repository.name),
        baseUrl),
      getForkedRepositories(
        repository.repository.originUserName.getOrElse(repository.owner),
        repository.repository.originRepositoryName.getOrElse(repository.name)),
      repository)
  })
  
  private def splitPath(repository: service.RepositoryService.RepositoryInfo, path: String): (String, String) = {
    val id = repository.branchList.collectFirst {
      case branch if(path == branch || path.startsWith(branch + "/")) => branch
    } orElse repository.tags.collectFirst {
      case tag if(path == tag.name || path.startsWith(tag.name + "/")) => tag.name
    } orElse Some(path.split("/")(0)) get

    (id, path.substring(id.length).replaceFirst("^/", ""))
  }

  /**
   * Provides HTML of the file list.
   * 
   * @param repository the repository information
   * @param revstr the branch name or commit id(optional)
   * @param path the directory path (optional)
   * @return HTML of the file list
   */
  private def fileList(repository: RepositoryService.RepositoryInfo, revstr: String = "", path: String = ".") = {
    if(repository.commitCount == 0){
      repo.html.guide(repository)
    } else {
      using(Git.open(getRepositoryDir(repository.owner, repository.name))){ git =>
        val revisions = Seq(if(revstr.isEmpty) repository.repository.defaultBranch else revstr, repository.branchList.head)
        // get specified commit
        JGitUtil.getDefaultBranch(git, repository, revstr).map { case (objectId, revision) =>
          defining(JGitUtil.getRevCommitFromId(git, objectId)){ revCommit =>
          // get files
            val files = JGitUtil.getFileList(git, revision, path)
            // process README.md
            val readme = files.find(_.name == "README.md").map { file =>
              StringUtil.convertFromByteArray(JGitUtil.getContent(Git.open(getRepositoryDir(repository.owner, repository.name)), file.id, true).get)
            }

            repo.html.files(revision, repository,
              if(path == ".") Nil else path.split("/").toList, // current path
              new JGitUtil.CommitInfo(revCommit), // latest commit
              files, readme)
          }
        } getOrElse NotFound
      }
    }
  }
  
}