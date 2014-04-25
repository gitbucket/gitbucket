package app

import _root_.util.JGitUtil.CommitInfo
import util.Directory._
import util.Implicits._
import _root_.util.ControlUtil._
import _root_.util._
import service._
import org.scalatra._
import java.io.File
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib._
import org.apache.commons.io.FileUtils
import org.eclipse.jgit.treewalk._
import java.util.zip.{ZipEntry, ZipOutputStream}
import jp.sf.amateras.scalatra.forms._
import org.eclipse.jgit.dircache.DirCache
import org.eclipse.jgit.revwalk.RevWalk

class RepositoryViewerController extends RepositoryViewerControllerBase
  with RepositoryService with AccountService with ActivityService with ReferrerAuthenticator with CollaboratorsAuthenticator

/**
 * The repository viewer.
 */
trait RepositoryViewerControllerBase extends ControllerBase {
  self: RepositoryService with AccountService with ActivityService with ReferrerAuthenticator with CollaboratorsAuthenticator =>

  case class EditorForm(content: String, message: Option[String], charset: String)

  val editorForm = mapping(
    "content" -> trim(label("Content", text())),
    "message" -> trim(label("Messgae", optional(text()))),
    "charset" -> trim(label("Charset", text()))
  )(EditorForm.apply)

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
    val page = params.get("page").flatMap(_.toIntOpt).getOrElse(1)

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
  get("/:owner/:repository/edit/*")(collaboratorsOnly { repository =>
    val (id, path) = splitPath(repository, multiParams("splat").head)

    using(Git.open(getRepositoryDir(repository.owner, repository.name))){ git =>
      val revCommit = JGitUtil.getRevCommitFromId(git, git.getRepository.resolve(id))

      @scala.annotation.tailrec
      def getPathObjectId(path: String, walk: TreeWalk): Option[ObjectId] = walk.next match {
        case true if(walk.getPathString == path) => Some(walk.getObjectId(0))
        case true  => getPathObjectId(path, walk)
        case false => None
      }

      using(new TreeWalk(git.getRepository)){ treeWalk =>
        treeWalk.addTree(revCommit.getTree)
        treeWalk.setRecursive(true)
        getPathObjectId(path, treeWalk)
      } map { objectId =>
        // Viewer
        val large  = FileUtil.isLarge(git.getRepository.getObjectDatabase.open(objectId).getSize)
        val viewer = if(FileUtil.isImage(path)) "image" else if(large) "large" else "other"
        val bytes  = if(viewer == "other") JGitUtil.getContentFromId(git, objectId, false) else None

        val content = if(viewer == "other"){
          if(bytes.isDefined && FileUtil.isText(bytes.get)){
            // text
            JGitUtil.ContentInfo("text", Some(StringUtil.convertFromByteArray(bytes.get)), Some(StringUtil.detectEncoding(bytes.get)))
          } else {
            // binary
            JGitUtil.ContentInfo("binary", None, None)
          }
        } else {
          // image or large
          JGitUtil.ContentInfo(viewer, None, None)
        }

        repo.html.editor(id, repository, path.split("/").toList, content, new JGitUtil.CommitInfo(revCommit))
      } getOrElse NotFound
    }
  })

  post("/:owner/:repository/edit/*", editorForm)(collaboratorsOnly { (form, repository) =>
    val (id, path) = splitPath(repository, multiParams("splat").head)

    LockUtil.lock(s"${repository.owner}/${repository.name}"){
      using(Git.open(getRepositoryDir(repository.owner, repository.name))){ git =>
        val loginAccount = context.loginAccount.get
        val builder  = DirCache.newInCore.builder()
        val inserter = git.getRepository.newObjectInserter()
        val headName = s"refs/heads/${id}"
        val headTip  = git.getRepository.resolve(s"refs/heads/${id}")

        JGitUtil.processTree(git, headTip){ (treePath, tree) =>
          if(treePath != path){
            builder.add(JGitUtil.createDirCacheEntry(treePath, tree.getEntryFileMode, tree.getEntryObjectId))
          }
        }

        builder.add(JGitUtil.createDirCacheEntry(path, FileMode.REGULAR_FILE,
          inserter.insert(Constants.OBJ_BLOB, form.content.getBytes(form.charset))))
        builder.finish()

        val commitId = JGitUtil.createNewCommit(git, inserter, headTip, builder.getDirCache.writeTree(inserter),
          loginAccount.fullName, loginAccount.mailAddress, form.message.getOrElse(s"Update ${path.split("/").last}"))

        inserter.flush()
        inserter.release()

        // update refs
        val refUpdate = git.getRepository.updateRef(headName)
        refUpdate.setNewObjectId(commitId)
        refUpdate.setForceUpdate(false)
        refUpdate.setRefLogIdent(new PersonIdent(loginAccount.fullName, loginAccount.mailAddress))
        //refUpdate.setRefLogMessage("merged", true)
        refUpdate.update()

        // record activity
        recordPushActivity(repository.owner, repository.name, loginAccount.userName, id,
          List(new CommitInfo(JGitUtil.getRevCommitFromId(git, commitId))))

        // TODO invoke hook

        redirect(s"/${repository.owner}/${repository.name}/blob/${id}/${path}")
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
      def getPathObjectId(path: String, walk: TreeWalk): Option[ObjectId] = walk.next match {
        case true if(walk.getPathString == path) => Some(walk.getObjectId(0))
        case true  => getPathObjectId(path, walk)
        case false => None
      }

      using(new TreeWalk(git.getRepository)){ treeWalk =>
        treeWalk.addTree(revCommit.getTree)
        treeWalk.setRecursive(true)
        getPathObjectId(path, treeWalk)
      } map { objectId =>
        if(raw){
          // Download
          defining(JGitUtil.getContentFromId(git, objectId, false).get){ bytes =>
            contentType = FileUtil.getContentType(path, bytes)
            bytes
          }
        } else {
          // Viewer
          val large  = FileUtil.isLarge(git.getRepository.getObjectDatabase.open(objectId).getSize)
          val viewer = if(FileUtil.isImage(path)) "image" else if(large) "large" else "other"
          val bytes  = if(viewer == "other") JGitUtil.getContentFromId(git, objectId, false) else None

          val content = if(viewer == "other"){
            if(bytes.isDefined && FileUtil.isText(bytes.get)){
              // text
              JGitUtil.ContentInfo("text", Some(StringUtil.convertFromByteArray(bytes.get)), Some(StringUtil.detectEncoding(bytes.get)))
            } else {
              // binary
              JGitUtil.ContentInfo("binary", None, None)
            }
          } else {
            // image or large
            JGitUtil.ContentInfo(viewer, None, None)
          }

          repo.html.blob(id, repository, path.split("/").toList, content, new JGitUtil.CommitInfo(revCommit),
            hasWritePermission(repository.owner, repository.name, context.loginAccount))
        }
      } getOrElse NotFound
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
      repo.html.branches(branchInfo, hasWritePermission(repository.owner, repository.name, context.loginAccount), repository)
    }
  })

  /**
   * Deletes branch.
   */
  get("/:owner/:repository/delete/*")(collaboratorsOnly { repository =>
    val branchName = multiParams("splat").head
    val userName   = context.loginAccount.get.userName
    if(repository.repository.defaultBranch != branchName){
      using(Git.open(getRepositoryDir(repository.owner, repository.name))){ git =>
        git.branchDelete().setBranchNames(branchName).call()
        recordDeleteBranchActivity(repository.owner, repository.name, userName, branchName)
      }
    }
    redirect(s"/${repository.owner}/${repository.name}/branches")
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
  get("/:owner/:repository/archive/*")(referrersOnly { repository =>
    val name = multiParams("splat").head

    if(name.endsWith(".zip")){
      val revision = name.replaceFirst("\\.zip$", "")
      val workDir = getDownloadWorkDir(repository.owner, repository.name, session.getId)
      if(workDir.exists){
        FileUtils.deleteDirectory(workDir)
      }
      workDir.mkdirs

      val zipFile = new File(workDir, repository.name + "-" +
        (if(revision.length == 40) revision.substring(0, 10) else revision).replace('/', '_') + ".zip")

      using(Git.open(getRepositoryDir(repository.owner, repository.name))){ git =>
        val revCommit = JGitUtil.getRevCommitFromId(git, git.getRepository.resolve(revision))
        using(new TreeWalk(git.getRepository)){ walk =>
          val reader   = walk.getObjectReader
          val objectId = new MutableObjectId

          using(new ZipOutputStream(new java.io.FileOutputStream(zipFile))){ out =>
            walk.addTree(revCommit.getTree)
            walk.setRecursive(true)

            while(walk.next){
              val name = walk.getPathString
              val mode = walk.getFileMode(0)
              if(mode == FileMode.REGULAR_FILE || mode == FileMode.EXECUTABLE_FILE){
                walk.getObjectId(objectId, 0)
                val entry = new ZipEntry(name)
                val loader = reader.open(objectId)
                entry.setSize(loader.getSize)
                out.putNextEntry(entry)
                loader.copyTo(out)
              }
            }
          }
        }
      }

      contentType = "application/octet-stream"
      response.setHeader("Content-Disposition", s"attachment; filename=${zipFile.getName}")
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


  private val readmeFiles = view.helpers.renderableSuffixes.map(suffix => s"readme${suffix}") ++ Seq("readme.txt", "readme")

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
      repo.html.guide(repository, hasWritePermission(repository.owner, repository.name, context.loginAccount))
    } else {
      using(Git.open(getRepositoryDir(repository.owner, repository.name))){ git =>
        //val revisions = Seq(if(revstr.isEmpty) repository.repository.defaultBranch else revstr, repository.branchList.head)
        // get specified commit
        JGitUtil.getDefaultBranch(git, repository, revstr).map { case (objectId, revision) =>
          defining(JGitUtil.getRevCommitFromId(git, objectId)) { revCommit =>
            // get files
            val files = JGitUtil.getFileList(git, revision, path)
            val parentPath = if (path == ".") Nil else path.split("/").toList
            // process README.md or README.markdown
            val readme = files.find { file =>
              readmeFiles.contains(file.name.toLowerCase)
            }.map { file =>
              val path = (file.name :: parentPath.reverse).reverse
              path -> StringUtil.convertFromByteArray(JGitUtil.getContentFromId(
                Git.open(getRepositoryDir(repository.owner, repository.name)), file.id, true).get)
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
