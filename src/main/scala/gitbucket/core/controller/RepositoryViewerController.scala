package gitbucket.core.controller

import java.io.{File, FileInputStream, FileOutputStream}
import scala.util.Using
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import gitbucket.core.repo.html
import gitbucket.core.helper
import gitbucket.core.model.activity.DeleteBranchInfo
import gitbucket.core.service._
import gitbucket.core.service.RepositoryCommitFileService.CommitFile
import gitbucket.core.util._
import gitbucket.core.util.StringUtil._
import gitbucket.core.util.SyntaxSugars._
import gitbucket.core.util.Implicits._
import gitbucket.core.util.Directory._
import gitbucket.core.model.{Account, WebHook}
import gitbucket.core.service.RepositoryService.RepositoryInfo
import gitbucket.core.service.WebHookService.{WebHookCreatePayload, WebHookPushPayload}
import gitbucket.core.util.JGitUtil.CommitInfo
import gitbucket.core.view
import gitbucket.core.view.helpers
import org.apache.commons.compress.archivers.{ArchiveEntry, ArchiveOutputStream}
import org.apache.commons.compress.archivers.tar.{TarArchiveEntry, TarArchiveOutputStream}
import org.apache.commons.compress.archivers.zip.{ZipArchiveEntry, ZipArchiveOutputStream}
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import org.apache.commons.compress.utils.IOUtils
import org.apache.commons.io.FileUtils
import org.scalatra.forms._
import org.eclipse.jgit.api.{ArchiveCommand, Git}
import org.eclipse.jgit.archive.{TgzFormat, ZipFormat}
import org.eclipse.jgit.errors.MissingObjectException
import org.eclipse.jgit.lib._
import org.eclipse.jgit.treewalk.{TreeWalk, WorkingTreeOptions}
import org.eclipse.jgit.treewalk.TreeWalk.OperationType
import org.eclipse.jgit.treewalk.filter.PathFilter
import org.eclipse.jgit.util.io.EolStreamTypeUtil
import org.json4s.jackson.Serialization
import org.scalatra._
import org.scalatra.i18n.Messages

import scala.collection.JavaConverters.iterableAsScalaIterableConverter

class RepositoryViewerController
    extends RepositoryViewerControllerBase
    with RepositoryService
    with RepositoryCommitFileService
    with AccountService
    with ActivityService
    with IssuesService
    with WebHookService
    with CommitsService
    with LabelsService
    with MilestonesService
    with PrioritiesService
    with ReadableUsersAuthenticator
    with ReferrerAuthenticator
    with WritableUsersAuthenticator
    with MergeService
    with PullRequestService
    with CommitStatusService
    with WebHookPullRequestService
    with WebHookPullRequestReviewCommentService
    with ProtectedBranchService
    with RequestCache

/**
 * The repository viewer.
 */
trait RepositoryViewerControllerBase extends ControllerBase {
  self: RepositoryService
    with RepositoryCommitFileService
    with AccountService
    with ActivityService
    with IssuesService
    with WebHookService
    with CommitsService
    with ReadableUsersAuthenticator
    with ReferrerAuthenticator
    with WritableUsersAuthenticator
    with PullRequestService
    with CommitStatusService
    with WebHookPullRequestService
    with WebHookPullRequestReviewCommentService
    with ProtectedBranchService =>

  ArchiveCommand.registerFormat("zip", new ZipFormat)
  ArchiveCommand.registerFormat("tar.gz", new TgzFormat)

  case class UploadForm(
    branch: String,
    path: String,
    uploadFiles: String,
    message: Option[String],
    commit: String,
    newBranch: Boolean
  )

  case class EditorForm(
    branch: String,
    path: String,
    content: String,
    message: Option[String],
    charset: String,
    lineSeparator: String,
    newFileName: String,
    oldFileName: Option[String],
    commit: String,
    newBranch: Boolean
  )

  case class DeleteForm(
    branch: String,
    path: String,
    message: Option[String],
    fileName: String,
    commit: String,
    newBranch: Boolean
  )

  case class CommentForm(
    fileName: Option[String],
    oldLineNumber: Option[Int],
    newLineNumber: Option[Int],
    content: String,
    issueId: Option[Int],
    diff: Option[String]
  )

  case class TagForm(
    commitId: String,
    tagName: String,
    message: Option[String]
  )

  val uploadForm = mapping(
    "branch" -> trim(label("Branch", text(required))),
    "path" -> trim(label("Path", text())),
    "uploadFiles" -> trim(label("Upload files", text(required))),
    "message" -> trim(label("Message", optional(text()))),
    "commit" -> trim(label("Commit", text(required, conflict))),
    "newBranch" -> trim(label("New Branch", boolean()))
  )(UploadForm.apply)

  val editorForm = mapping(
    "branch" -> trim(label("Branch", text(required))),
    "path" -> trim(label("Path", text())),
    "content" -> trim(label("Content", text(required))),
    "message" -> trim(label("Message", optional(text()))),
    "charset" -> trim(label("Charset", text(required))),
    "lineSeparator" -> trim(label("Line Separator", text(required))),
    "newFileName" -> trim(label("Filename", text(required))),
    "oldFileName" -> trim(label("Old filename", optional(text()))),
    "commit" -> trim(label("Commit", text(required, conflict))),
    "newBranch" -> trim(label("New Branch", boolean()))
  )(EditorForm.apply)

  val deleteForm = mapping(
    "branch" -> trim(label("Branch", text(required))),
    "path" -> trim(label("Path", text())),
    "message" -> trim(label("Message", optional(text()))),
    "fileName" -> trim(label("Filename", text(required))),
    "commit" -> trim(label("Commit", text(required, conflict))),
    "newBranch" -> trim(label("New Branch", boolean()))
  )(DeleteForm.apply)

  val commentForm = mapping(
    "fileName" -> trim(label("Filename", optional(text()))),
    "oldLineNumber" -> trim(label("Old line number", optional(number()))),
    "newLineNumber" -> trim(label("New line number", optional(number()))),
    "content" -> trim(label("Content", text(required))),
    "issueId" -> trim(label("Issue Id", optional(number()))),
    "diff" -> optional(text())
  )(CommentForm.apply)

  val tagForm = mapping(
    "commitId" -> trim(label("Commit id", text(required))),
    "tagName" -> trim(label("Tag name", text(required))),
    "message" -> trim(label("Message", optional(text())))
  )(TagForm.apply)

  /**
   * Returns converted HTML from Markdown for preview.
   */
  post("/:owner/:repository/_preview")(referrersOnly { repository =>
    contentType = "text/html"
    val filename = params.get("filename")
    filename match {
      case Some(f) =>
        helpers.renderMarkup(
          filePath = List(f),
          fileContent = params("content"),
          branch = repository.repository.defaultBranch,
          repository = repository,
          enableWikiLink = params("enableWikiLink").toBoolean,
          enableRefsLink = params("enableRefsLink").toBoolean,
          enableAnchor = false
        )
      case None =>
        helpers.markdown(
          markdown = params("content"),
          repository = repository,
          branch = repository.repository.defaultBranch,
          enableWikiLink = params("enableWikiLink").toBoolean,
          enableRefsLink = params("enableRefsLink").toBoolean,
          enableLineBreaks = params("enableLineBreaks").toBoolean,
          enableTaskList = params("enableTaskList").toBoolean,
          enableAnchor = false,
          hasWritePermission = hasDeveloperRole(repository.owner, repository.name, context.loginAccount)
        )
    }
  })

  /**
   * Displays the file list of the repository root and the default branch.
   */
  get("/:owner/:repository") {
    val owner = params("owner")
    val repository = params("repository")

    if (RepositoryCreationService.isCreating(owner, repository)) {
      gitbucket.core.repo.html.creating(owner, repository)
    } else {
      params.get("go-get") match {
        case Some("1") =>
          defining(request.paths) { paths =>
            getRepository(owner, repository).map(gitbucket.core.html.goget(_)) getOrElse NotFound()
          }
        case _ => referrersOnly(fileList(_))
      }
    }
  }

  ajaxGet("/:owner/:repository/creating") {
    val owner = params("owner")
    val repository = params("repository")
    contentType = formats("json")
    val creating = RepositoryCreationService.isCreating(owner, repository)
    Serialization.write(
      Map(
        "creating" -> creating,
        "error" -> (if (creating) None else RepositoryCreationService.getCreationError(owner, repository))
      )
    )
  }

  /**
   * Displays the file list of the specified path and branch.
   */
  get("/:owner/:repository/tree/*")(referrersOnly { repository =>
    val (id, path) = repository.splitPath(multiParams("splat").head)
    if (path.isEmpty) {
      fileList(repository, id)
    } else {
      fileList(repository, id, path)
    }
  })

  /**
   * Displays the commit list of the specified resource.
   */
  get("/:owner/:repository/commits/*")(referrersOnly { repository =>
    val (branchName, path) = repository.splitPath(multiParams("splat").head)
    val page = params.get("page").flatMap(_.toIntOpt).getOrElse(1)

    Using.resource(Git.open(getRepositoryDir(repository.owner, repository.name))) {
      git =>
        JGitUtil.getCommitLog(git, branchName, page, 30, path) match {
          case Right((logs, hasNext)) =>
            html.commits(
              if (path.isEmpty) Nil else path.split("/").toList,
              branchName,
              repository,
              logs
                .map {
                  commit =>
                    (
                      CommitInfo(
                        id = commit.id,
                        shortMessage = commit.shortMessage,
                        fullMessage = commit.fullMessage,
                        parents = commit.parents,
                        authorTime = commit.authorTime,
                        authorName = commit.authorName,
                        authorEmailAddress = commit.authorEmailAddress,
                        commitTime = commit.commitTime,
                        committerName = commit.committerName,
                        committerEmailAddress = commit.committerEmailAddress,
                        commitSign = commit.commitSign,
                        verified = commit.commitSign.flatMap(GpgUtil.verifySign)
                      ),
                      JGitUtil.getTagsOnCommit(git, commit.id),
                      getCommitStatusWithSummary(repository.owner, repository.name, commit.id)
                    )
                }
                .splitWith {
                  case ((commit1, _, _), (commit2, _, _)) =>
                    view.helpers.date(commit1.commitTime) == view.helpers.date(commit2.commitTime)
                },
              page,
              hasNext,
              hasDeveloperRole(repository.owner, repository.name, context.loginAccount)
            )
          case Left(_) => NotFound()
        }
    }
  })

  get("/:owner/:repository/new/*")(writableUsersOnly { repository =>
    val (branch, path) = repository.splitPath(multiParams("splat").head)
    val protectedBranch = getProtectedBranchInfo(repository.owner, repository.name, branch)
      .needStatusCheck(context.loginAccount.get.userName)

    Using.resource(Git.open(getRepositoryDir(repository.owner, repository.name))) { git =>
      val revCommit = JGitUtil.getRevCommitFromId(git, git.getRepository.resolve(branch))

      html.editor(
        branch = branch,
        repository = repository,
        pathList = if (path.length == 0) Nil else path.split("/").toList,
        fileName = None,
        content = JGitUtil.ContentInfo("text", None, None, Some("UTF-8")),
        protectedBranch = protectedBranch,
        commit = revCommit.getName
      )
    }
  })

  get("/:owner/:repository/upload/*")(writableUsersOnly { repository =>
    val (branch, path) = repository.splitPath(multiParams("splat").head)
    val protectedBranch = getProtectedBranchInfo(repository.owner, repository.name, branch)
      .needStatusCheck(context.loginAccount.get.userName)
    Using.resource(Git.open(getRepositoryDir(repository.owner, repository.name))) { git =>
      val revCommit = JGitUtil.getRevCommitFromId(git, git.getRepository.resolve(branch))
      html.upload(
        branch,
        repository,
        if (path.length == 0) Nil else path.split("/").toList,
        protectedBranch,
        revCommit.name
      )
    }
  })

  post("/:owner/:repository/upload", uploadForm)(writableUsersOnly { (form, repository) =>
    val files = form.uploadFiles.split("\n").map { line =>
      val i = line.indexOf(':')
      CommitFile(line.substring(0, i).trim, line.substring(i + 1).trim)
    }

    val newFiles = files.map { file =>
      file.copy(name = if (form.path.length == 0) file.name else s"${form.path}/${file.name}")
    }

    if (form.newBranch) {
      val newBranchName = createNewBranchForPullRequest(repository, form.branch)
      val objectId = _commit(newBranchName)
      val issueId =
        createIssueAndPullRequest(repository, form.branch, newBranchName, form.commit, objectId.name, form.message)
      redirect(s"/${repository.owner}/${repository.name}/pull/${issueId}")
    } else {
      _commit(form.branch)
      if (form.path.length == 0) {
        redirect(s"/${repository.owner}/${repository.name}/tree/${form.branch}")
      } else {
        redirect(s"/${repository.owner}/${repository.name}/tree/${form.branch}/${form.path}")
      }
    }

    def _commit(branchName: String): ObjectId = {
      commitFiles(
        repository = repository,
        branch = branchName,
        path = form.path,
        files = files.toIndexedSeq,
        message = form.message.getOrElse("Add files via upload"),
        loginAccount = context.loginAccount.get,
        settings = context.settings
      ) {
        case (git, headTip, builder, inserter) =>
          JGitUtil.processTree(git, headTip) { (path, tree) =>
            if (!newFiles.exists(_.name.contains(path))) {
              builder.add(JGitUtil.createDirCacheEntry(path, tree.getEntryFileMode, tree.getEntryObjectId))
            }
          }

          newFiles.foreach { file =>
            val bytes =
              FileUtils.readFileToByteArray(new File(getTemporaryDir(session.getId), FileUtil.checkFilename(file.id)))
            builder.add(
              JGitUtil.createDirCacheEntry(file.name, FileMode.REGULAR_FILE, inserter.insert(Constants.OBJ_BLOB, bytes))
            )
            builder.finish()
          }
      }
    }
  })

  get("/:owner/:repository/edit/*")(writableUsersOnly { repository =>
    val (branch, path) = repository.splitPath(multiParams("splat").head)
    val protectedBranch = getProtectedBranchInfo(repository.owner, repository.name, branch)
      .needStatusCheck(context.loginAccount.get.userName)

    Using.resource(Git.open(getRepositoryDir(repository.owner, repository.name))) {
      git =>
        val revCommit = JGitUtil.getRevCommitFromId(git, git.getRepository.resolve(branch))

        getPathObjectId(git, path, revCommit).map {
          objectId =>
            val paths = path.split("/")
            val info = EditorConfigUtil.getEditorConfigInfo(git, branch, path)

            html.editor(
              branch = branch,
              repository = repository,
              pathList = paths.take(paths.size - 1).toList,
              fileName = Some(paths.last),
              content = JGitUtil.getContentInfo(git, path, objectId),
              protectedBranch = protectedBranch,
              commit = revCommit.getName,
              newLineMode = info.newLineMode,
              useSoftTabs = info.useSoftTabs,
              tabSize = info.tabSize
            )
        } getOrElse NotFound()
    }
  })

  get("/:owner/:repository/remove/*")(writableUsersOnly { repository =>
    val (branch, path) = repository.splitPath(multiParams("splat").head)
    Using.resource(Git.open(getRepositoryDir(repository.owner, repository.name))) {
      git =>
        val revCommit = JGitUtil.getRevCommitFromId(git, git.getRepository.resolve(branch))

        getPathObjectId(git, path, revCommit).map { objectId =>
          val paths = path.split("/")
          html.delete(
            branch = branch,
            repository = repository,
            pathList = paths.take(paths.size - 1).toList,
            fileName = paths.last,
            content = JGitUtil.getContentInfo(git, path, objectId),
            commit = revCommit.getName
          )
        } getOrElse NotFound()
    }
  })

  post("/:owner/:repository/create", editorForm)(writableUsersOnly { (form, repository) =>
    if (form.newBranch) {
      val newBranchName = createNewBranchForPullRequest(repository, form.branch)
      val objectId = _commit(newBranchName)
      val issueId =
        createIssueAndPullRequest(repository, form.branch, newBranchName, form.commit, objectId.name, form.message)
      redirect(s"/${repository.owner}/${repository.name}/pull/${issueId}")
    } else {
      _commit(form.branch)
      redirect(
        s"/${repository.owner}/${repository.name}/blob/${form.branch}/${if (form.path.length == 0) urlEncode(form.newFileName)
        else s"${form.path}/${urlEncode(form.newFileName)}"}"
      )
    }

    def _commit(branchName: String): ObjectId = {
      commitFile(
        repository = repository,
        branch = branchName,
        path = form.path,
        newFileName = Some(form.newFileName),
        oldFileName = None,
        content = appendNewLine(convertLineSeparator(form.content, form.lineSeparator), form.lineSeparator),
        charset = form.charset,
        message = form.message.getOrElse(s"Create ${form.newFileName}"),
        commit = form.commit,
        loginAccount = context.loginAccount.get,
        settings = context.settings
      )
    }
  })

  post("/:owner/:repository/update", editorForm)(writableUsersOnly { (form, repository) =>
    if (form.newBranch) {
      val newBranchName = createNewBranchForPullRequest(repository, form.branch)
      val objectId = _commit(newBranchName)
      val issueId =
        createIssueAndPullRequest(repository, form.branch, newBranchName, form.commit, objectId.name, form.message)
      redirect(s"/${repository.owner}/${repository.name}/pull/${issueId}")
    } else {
      _commit(form.branch)
      redirect(
        s"/${repository.owner}/${repository.name}/blob/${urlEncode(form.branch)}/${if (form.path.length == 0) urlEncode(form.newFileName)
        else s"${form.path}/${urlEncode(form.newFileName)}"}"
      )
    }

    def _commit(branchName: String): ObjectId = {
      commitFile(
        repository = repository,
        branch = branchName,
        path = form.path,
        newFileName = Some(form.newFileName),
        oldFileName = form.oldFileName,
        content = appendNewLine(convertLineSeparator(form.content, form.lineSeparator), form.lineSeparator),
        charset = form.charset,
        message = if (form.oldFileName.contains(form.newFileName)) {
          form.message.getOrElse(s"Update ${form.newFileName}")
        } else {
          form.message.getOrElse(s"Rename ${form.oldFileName.get} to ${form.newFileName}")
        },
        commit = form.commit,
        loginAccount = context.loginAccount.get,
        settings = context.settings
      )
    }
  })

  post("/:owner/:repository/remove", deleteForm)(writableUsersOnly { (form, repository) =>
    if (form.newBranch) {
      val newBranchName = createNewBranchForPullRequest(repository, form.branch)
      val objectId = _commit(newBranchName)
      val issueId =
        createIssueAndPullRequest(repository, form.branch, newBranchName, form.commit, objectId.name, form.message)
      redirect(s"/${repository.owner}/${repository.name}/pull/${issueId}")
    } else {
      _commit(form.branch)
      redirect(
        s"/${repository.owner}/${repository.name}/tree/${form.branch}${if (form.path.length == 0) ""
        else "/" + form.path}"
      )
    }

    def _commit(branchName: String): ObjectId = {
      commitFile(
        repository = repository,
        branch = branchName,
        path = form.path,
        newFileName = None,
        oldFileName = Some(form.fileName),
        content = "",
        charset = "",
        message = form.message.getOrElse(s"Delete ${form.fileName}"),
        commit = form.commit,
        loginAccount = context.loginAccount.get,
        settings = context.settings
      )
    }
  })

  private def getNewBranchName(repository: RepositoryInfo): String = {
    var i = 1
    val branchNamePrefix = cutTail(context.loginAccount.get.userName.replaceAll("[^a-zA-Z0-9-_]", "-"), 25)
    while (repository.branchList.exists(p => p.contains(s"$branchNamePrefix-patch-$i"))) {
      i += 1
    }
    s"$branchNamePrefix-patch-$i"
  }

  private def createNewBranchForPullRequest(repository: RepositoryInfo, baseBranchName: String): String = {
    val newBranchName = getNewBranchName(repository)
    Using.resource(Git.open(getRepositoryDir(repository.owner, repository.name))) { git =>
      JGitUtil.createBranch(git, baseBranchName, newBranchName)
    }
    // Call webhook
    val settings = loadSystemSettings()
    callWebHookOf(repository.owner, repository.name, WebHook.Create, settings) {
      for {
        sender <- context.loginAccount
        owner <- getAccountByUserName(repository.owner)
      } yield {
        WebHookCreatePayload(
          sender,
          repository,
          owner,
          ref = newBranchName,
          refType = "branch"
        )
      }
    }
    newBranchName
  }

  private def createIssueAndPullRequest(
    repository: RepositoryInfo,
    baseBranch: String,
    requestBranch: String,
    commitIdFrom: String,
    commitIdTo: String,
    commitMessage: Option[String]
  ): Int = {
    val issueId = insertIssue(
      owner = repository.owner,
      repository = repository.name,
      loginUser = context.loginAccount.get.userName,
      title = requestBranch,
      content = commitMessage,
      assignedUserName = None,
      milestoneId = None,
      priorityId = None,
      isPullRequest = true
    )
    createPullRequest(
      originRepository = repository,
      issueId = issueId,
      originBranch = baseBranch,
      requestUserName = repository.owner,
      requestRepositoryName = repository.name,
      requestBranch = requestBranch,
      commitIdFrom = commitIdFrom,
      commitIdTo = commitIdTo,
      isDraft = false,
      loginAccount = context.loginAccount.get,
      settings = context.settings
    )
    issueId
  }

  get("/:owner/:repository/raw/*")(referrersOnly { repository =>
    val (id, path) = repository.splitPath(multiParams("splat").head)
    Using.resource(Git.open(getRepositoryDir(repository.owner, repository.name))) { git =>
      val revCommit = JGitUtil.getRevCommitFromId(git, git.getRepository.resolve(id))

      getPathObjectId(git, path, revCommit).map { objectId =>
        responseRawFile(git, objectId, path, repository)
      } getOrElse NotFound()
    }
  })

  /**
   * Displays the file content of the specified branch or commit.
   */
  val blobRoute = get("/:owner/:repository/blob/*")(referrersOnly { repository =>
    val (id, path) = repository.splitPath(multiParams("splat").head)
    val raw = params.get("raw").getOrElse("false").toBoolean
    val highlighterTheme = getSyntaxHighlighterTheme()
    Using.resource(Git.open(getRepositoryDir(repository.owner, repository.name))) {
      git =>
        val revCommit = JGitUtil.getRevCommitFromId(git, git.getRepository.resolve(id))
        getPathObjectId(git, path, revCommit).map {
          objectId =>
            if (raw) {
              // Download (This route is left for backword compatibility)
              responseRawFile(git, objectId, path, repository)
            } else {
              val info = EditorConfigUtil.getEditorConfigInfo(git, id, path)
              html.blob(
                branch = id,
                repository = repository,
                pathList = path.split("/").toList,
                content = JGitUtil.getContentInfo(git, path, objectId),
                latestCommit = new JGitUtil.CommitInfo(JGitUtil.getLastModifiedCommit(git, revCommit, path)),
                hasWritePermission = hasDeveloperRole(repository.owner, repository.name, context.loginAccount),
                isBlame = request.paths(2) == "blame",
                isLfsFile = isLfsFile(git, objectId),
                tabSize = info.tabSize,
                highlighterTheme = highlighterTheme
              )
            }
        } getOrElse NotFound()
    }
  })

  private def getSyntaxHighlighterTheme()(implicit context: Context): String = {
    context.loginAccount match {
      case Some(account) =>
        getAccountPreference(account.userName) match {
          case Some(x) => x.highlighterTheme
          case _       => "github-v2"
        }
      case _ => "github-v2"
    }
  }

  private def isLfsFile(git: Git, objectId: ObjectId): Boolean = {
    JGitUtil.getObjectLoaderFromId(git, objectId)(JGitUtil.isLfsPointer).getOrElse(false)
  }

  get("/:owner/:repository/blame/*") {
    blobRoute.action()
  }

  /**
   * Blame data.
   */
  ajaxGet("/:owner/:repository/get-blame/*")(referrersOnly { repository =>
    val (id, path) = repository.splitPath(multiParams("splat").head)
    contentType = formats("json")
    Using.resource(Git.open(getRepositoryDir(repository.owner, repository.name))) {
      git =>
        val last = git.log.add(git.getRepository.resolve(id)).addPath(path).setMaxCount(1).call.iterator.next.name
        Serialization.write(
          Map(
            "root" -> s"${context.baseUrl}/${repository.owner}/${repository.name}",
            "id" -> id,
            "path" -> path,
            "last" -> last,
            "blame" -> JGitUtil.getBlame(git, id, path).map {
              blame =>
                Map(
                  "id" -> blame.id,
                  "author" -> view.helpers.user(blame.authorName, blame.authorEmailAddress).toString,
                  "avatar" -> view.helpers.avatarLink(blame.authorName, 32, blame.authorEmailAddress).toString,
                  "authed" -> helper.html.datetimeago(blame.authorTime).toString,
                  "prev" -> blame.prev,
                  "prevPath" -> blame.prevPath,
                  "commited" -> blame.commitTime.getTime,
                  "message" -> blame.message,
                  "lines" -> blame.lines
                )
            }
          )
        )
    }
  })

  /**
   * Displays details of the specified commit.
   */
  get("/:owner/:repository/commit/:id")(referrersOnly { repository =>
    val id = params("id")

    try {
      Using.resource(Git.open(getRepositoryDir(repository.owner, repository.name))) {
        git =>
          defining(JGitUtil.getRevCommitFromId(git, git.getRepository.resolve(id))) {
            revCommit =>
              val diffs = JGitUtil.getDiffs(git, None, id, true, false)
              val oldCommitId = JGitUtil.getParentCommitId(git, id)

              html.commit(
                id,
                new JGitUtil.CommitInfo(revCommit),
                JGitUtil.getBranchesOfCommit(git, revCommit.getName),
                JGitUtil.getTagsOfCommit(git, revCommit.getName),
                getCommitStatusWithSummary(repository.owner, repository.name, revCommit.getName),
                getCommitComments(repository.owner, repository.name, id, true),
                repository,
                diffs,
                oldCommitId,
                hasDeveloperRole(repository.owner, repository.name, context.loginAccount),
                flash.get("info"),
                flash.get("error")
              )
          }
      }
    } catch {
      case e: MissingObjectException => NotFound()
    }
  })

  get("/:owner/:repository/patch/:id")(referrersOnly { repository =>
    try {
      Using.resource(Git.open(getRepositoryDir(repository.owner, repository.name))) { git =>
        val diff = JGitUtil.getPatch(git, None, params("id"))
        contentType = formats("txt")
        diff
      }
    } catch {
      case e: MissingObjectException => NotFound()
    }
  })

  get("/:owner/:repository/patch/*...*")(referrersOnly { repository =>
    try {
      val Seq(fromId, toId) = multiParams("splat")
      Using.resource(Git.open(getRepositoryDir(repository.owner, repository.name))) { git =>
        val diff = JGitUtil.getPatch(git, Some(fromId), toId)
        contentType = formats("txt")
        diff
      }
    } catch {
      case e: MissingObjectException => NotFound()
    }
  })

  post("/:owner/:repository/commit/:id/comment/new", commentForm)(readableUsersOnly { (form, repository) =>
    val id = params("id")
    createCommitComment(
      repository,
      id,
      context.loginAccount.get,
      form.content,
      form.fileName,
      form.oldLineNumber,
      form.newLineNumber,
      form.diff,
      form.issueId
    )

    redirect(s"/${repository.owner}/${repository.name}/commit/${id}")
  })

  ajaxGet("/:owner/:repository/commit/:id/comment/_form")(readableUsersOnly { repository =>
    val id = params("id")
    val fileName = params.get("fileName")
    val oldLineNumber = params.get("oldLineNumber") map (_.toInt)
    val newLineNumber = params.get("newLineNumber") map (_.toInt)
    val issueId = params.get("issueId") map (_.toInt)
    html.commentform(
      commitId = id,
      fileName,
      oldLineNumber,
      newLineNumber,
      issueId,
      hasWritePermission = hasDeveloperRole(repository.owner, repository.name, context.loginAccount),
      repository = repository,
      focus = true
    )
  })

  ajaxPost("/:owner/:repository/commit/:id/comment/_data/new", commentForm)(readableUsersOnly { (form, repository) =>
    val id = params("id")
    val commentId = createCommitComment(
      repository,
      id,
      context.loginAccount.get,
      form.content,
      form.fileName,
      form.oldLineNumber,
      form.newLineNumber,
      form.diff,
      form.issueId
    )

    val comment = getCommitComment(repository.owner, repository.name, commentId.toString).get
    helper.html
      .commitcomment(comment, hasDeveloperRole(repository.owner, repository.name, context.loginAccount), repository)
  })

  ajaxGet("/:owner/:repository/commit_comments/_data/:id")(readableUsersOnly { repository =>
    getCommitComment(repository.owner, repository.name, params("id")) map {
      x =>
        if (isEditable(x.userName, x.repositoryName, x.commentedUserName)) {
          params.get("dataType") collect {
            case t if t == "html" => html.editcomment(x.content, x.commentId, repository)
          } getOrElse {
            contentType = formats("json")
            org.json4s.jackson.Serialization.write(
              Map(
                "content" -> view.Markdown.toHtml(
                  markdown = x.content,
                  repository = repository,
                  branch = repository.repository.defaultBranch,
                  enableWikiLink = false,
                  enableRefsLink = true,
                  enableAnchor = true,
                  enableLineBreaks = true,
                  enableTaskList = true,
                  hasWritePermission = true
                )
              )
            )
          }
        } else Unauthorized()
    } getOrElse NotFound()
  })

  ajaxPost("/:owner/:repository/commit_comments/edit/:id", commentForm)(readableUsersOnly { (form, repository) =>
    defining(repository.owner, repository.name) {
      case (owner, name) =>
        getCommitComment(owner, name, params("id")).map { comment =>
          if (isEditable(owner, name, comment.commentedUserName)) {
            updateCommitComment(comment.commentId, form.content)
            redirect(s"/${owner}/${name}/commit_comments/_data/${comment.commentId}")
          } else Unauthorized()
        } getOrElse NotFound()
    }
  })

  ajaxPost("/:owner/:repository/commit_comments/delete/:id")(readableUsersOnly { repository =>
    defining(repository.owner, repository.name) {
      case (owner, name) =>
        getCommitComment(owner, name, params("id")).map { comment =>
          if (isEditable(owner, name, comment.commentedUserName)) {
            Ok(deleteCommitComment(comment.commentId))
          } else Unauthorized()
        } getOrElse NotFound()
    }
  })

  /**
   * Displays branches.
   */
  get("/:owner/:repository/branches")(referrersOnly { repository =>
    val protectedBranches = getProtectedBranchList(repository.owner, repository.name).toSet
    val branches = Using.resource(Git.open(getRepositoryDir(repository.owner, repository.name))) {
      git =>
        JGitUtil
          .getBranches(
            git = git,
            defaultBranch = repository.repository.defaultBranch,
            origin = repository.repository.originUserName.isEmpty
          )
          .sortBy(branch => (branch.mergeInfo.isEmpty, branch.commitTime))
          .map(
            branch =>
              (
                branch,
                getPullRequestByRequestCommit(
                  repository.owner,
                  repository.name,
                  repository.repository.defaultBranch,
                  branch.name,
                  branch.commitId
                ),
                protectedBranches.contains(branch.name),
                getCommitStatusWithSummary(repository.owner, repository.name, branch.commitId)
            )
          )
          .reverse
    }

    html.branches(branches, hasDeveloperRole(repository.owner, repository.name, context.loginAccount), repository)
  })

  /**
   * Displays the create tag dialog.
   */
  get("/:owner/:repository/tag/:id")(writableUsersOnly { repository =>
    html.tag(params("id"), repository)
  })

  /**
   * Creates a tag.
   */
  post("/:owner/:repository/tag", tagForm)(writableUsersOnly { (form, repository) =>
    Using.resource(Git.open(getRepositoryDir(repository.owner, repository.name))) { git =>
      JGitUtil.createTag(git, form.tagName, form.message, form.commitId)
    } match {
      case Right(message) =>
        flash.update("info", message)
        redirect(s"/${repository.owner}/${repository.name}/commit/${form.commitId}")
      case Left(message) =>
        flash.update("error", message)
        redirect(s"/${repository.owner}/${repository.name}/commit/${form.commitId}")
    }
  })

  /**
   * Creates a branch.
   */
  post("/:owner/:repository/branches")(writableUsersOnly { repository =>
    val newBranchName = params.getOrElse("new", halt(400))
    val fromBranchName = params.getOrElse("from", halt(400))
    Using.resource(Git.open(getRepositoryDir(repository.owner, repository.name))) {
      git =>
        JGitUtil.createBranch(git, fromBranchName, newBranchName) match {
          case Right(message) =>
            flash.update("info", message)
            val settings = loadSystemSettings()
            val newCommitId = git.getRepository.resolve(s"refs/heads/${newBranchName}")
            val oldCommitId = ObjectId.fromString("0" * 40)
            // call push webhook
            callWebHookOf(repository.owner, repository.name, WebHook.Push, settings) {
              for {
                pusherAccount <- context.loginAccount
                ownerAccount <- getAccountByUserName(repository.owner)
              } yield {
                WebHookPushPayload(
                  git,
                  pusherAccount,
                  newBranchName,
                  repository,
                  List(),
                  ownerAccount,
                  newId = newCommitId,
                  oldId = oldCommitId
                )
              }
            }
            // call create webhook
            callWebHookOf(repository.owner, repository.name, WebHook.Create, settings) {
              for {
                sender <- context.loginAccount
                owner <- getAccountByUserName(repository.owner)
              } yield {
                WebHookCreatePayload(
                  sender,
                  repository,
                  owner,
                  ref = newBranchName,
                  refType = "branch"
                )
              }
            }
            redirect(
              s"/${repository.owner}/${repository.name}/tree/${StringUtil.urlEncode(newBranchName).replace("%2F", "/")}"
            )
          case Left(message) =>
            flash.update("error", message)
            redirect(s"/${repository.owner}/${repository.name}/tree/${fromBranchName}")
        }
    }
  })

  /**
   * Deletes branch.
   */
  get("/:owner/:repository/delete/*")(writableUsersOnly { repository =>
    val branchName = multiParams("splat").head
    val userName = context.loginAccount.get.userName
    if (repository.repository.defaultBranch != branchName) {
      Using.resource(Git.open(getRepositoryDir(repository.owner, repository.name))) { git =>
        git.branchDelete().setForce(true).setBranchNames(branchName).call()
        val deleteBranchInfo = DeleteBranchInfo(repository.owner, repository.name, userName, branchName)
        recordActivity(deleteBranchInfo)
      }
    }
    redirect(s"/${repository.owner}/${repository.name}/branches")
  })

  /**
   * Displays tags.
   */
  get("/:owner/:repository/tags")(referrersOnly { repository =>
    redirect(s"${repository.owner}/${repository.name}/releases")
  })

  get("/:owner/:repository/archive/:name")(referrersOnly { repository =>
    val name = params("name")
    archiveRepository(name, repository, "")
  })

  get("/:owner/:repository/archive/*/:name")(referrersOnly { repository =>
    val name = params("name")
    val path = multiParams("splat").head
    archiveRepository(name, repository, path)
  })

  get("/:owner/:repository/network/members")(referrersOnly { repository =>
    if (repository.repository.options.allowFork) {
      html.forked(
        getRepository(
          repository.repository.originUserName.getOrElse(repository.owner),
          repository.repository.originRepositoryName.getOrElse(repository.name)
        ),
        getForkedRepositories(
          repository.repository.originUserName.getOrElse(repository.owner),
          repository.repository.originRepositoryName.getOrElse(repository.name)
        ).map { repository =>
          (repository.userName, repository.repositoryName)
        },
        context.loginAccount match {
          case None                     => List()
          case account: Option[Account] => getGroupsByUserName(account.get.userName)
        }, // groups of current user
        repository
      )
    } else BadRequest()
  })

  /**
   * Displays the file find of branch.
   */
  get("/:owner/:repository/find/*")(referrersOnly { repository =>
    Using.resource(Git.open(getRepositoryDir(repository.owner, repository.name))) { git =>
      val ref = multiParams("splat").head
      JGitUtil.getTreeId(git, ref).map { treeId =>
        html.find(ref, treeId, repository)
      } getOrElse NotFound()
    }
  })

  /**
   * Get all file list of branch.
   */
  ajaxGet("/:owner/:repository/tree-list/:tree")(referrersOnly { repository =>
    Using.resource(Git.open(getRepositoryDir(repository.owner, repository.name))) { git =>
      val treeId = params("tree")
      contentType = formats("json")
      Map("paths" -> JGitUtil.getAllFileListByTreeId(git, treeId))
    }
  })

  case class UploadFiles(branch: String, path: String, fileIds: Map[String, String], message: String) {
    lazy val isValid: Boolean = fileIds.nonEmpty
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
    Using.resource(Git.open(getRepositoryDir(repository.owner, repository.name))) { git =>
      if (JGitUtil.isEmpty(git)) {
        html.guide(repository, hasDeveloperRole(repository.owner, repository.name, context.loginAccount))
      } else {
        // get specified commit
        JGitUtil.getDefaultBranch(git, repository, revstr).map {
          case (objectId, revision) =>
            defining(JGitUtil.getRevCommitFromId(git, objectId)) { revCommit =>
              val lastModifiedCommit =
                if (path == ".") revCommit else JGitUtil.getLastModifiedCommit(git, revCommit, path)
              val commitCount = JGitUtil.getCommitCount(git, lastModifiedCommit.getName)
              // get files
              val files = JGitUtil.getFileList(
                git,
                revision,
                path,
                context.settings.baseUrl,
                commitCount,
                context.settings.repositoryViewer.maxFiles
              )
              val parentPath = if (path == ".") Nil else path.split("/").toList
              // process README
              val readme = files // files should be sorted alphabetically.
                .find { file =>
                  !file.isDirectory && RepositoryService.readmeFiles.contains(file.name.toLowerCase)
                }
                .map { file =>
                  val path = (file.name :: parentPath.reverse).reverse
                  path -> StringUtil.convertFromByteArray(
                    JGitUtil
                      .getContentFromId(Git.open(getRepositoryDir(repository.owner, repository.name)), file.id, true)
                      .get
                  )
                }

              html.files(
                revision,
                repository,
                if (path == ".") Nil else path.split("/").toList, // current path
                new JGitUtil.CommitInfo(lastModifiedCommit), // last modified commit
                getCommitStatusWithSummary(repository.owner, repository.name, lastModifiedCommit.getName),
                commitCount,
                files,
                readme,
                hasDeveloperRole(repository.owner, repository.name, context.loginAccount),
                getPullRequestFromBranch(
                  repository.owner,
                  repository.name,
                  revstr,
                  repository.repository.defaultBranch
                ),
                flash.get("info"),
                flash.get("error")
              )
            }
        } getOrElse NotFound()
      }
    }
  }

  private def archiveRepository(
    filename: String,
    repository: RepositoryService.RepositoryInfo,
    path: String
  ) = {
    def archive(revision: String, archiveFormat: String, archive: ArchiveOutputStream)(
      entryCreator: (String, Long, java.util.Date, Int) => ArchiveEntry
    ): Unit = {
      Using.resource(Git.open(getRepositoryDir(repository.owner, repository.name))) { git =>
        val oid = git.getRepository.resolve(revision)
        val commit = JGitUtil.getRevCommitFromId(git, oid)
        val date = commit.getCommitterIdent.getWhen
        val sha1 = oid.getName()
        val repositorySuffix = (if (sha1.startsWith(revision)) sha1 else revision).replace('/', '-')
        val pathSuffix = if (path.isEmpty) "" else s"-${path.replace('/', '-')}"
        val baseName = repository.name + "-" + repositorySuffix + pathSuffix

        Using.resource(new TreeWalk(git.getRepository)) { treeWalk =>
          treeWalk.addTree(commit.getTree)
          treeWalk.setRecursive(true)
          if (!path.isEmpty) {
            treeWalk.setFilter(PathFilter.create(path))
          }
          if (treeWalk != null) {
            while (treeWalk.next()) {
              val entryPath =
                if (path.isEmpty) baseName + "/" + treeWalk.getPathString
                else path.split("/").last + treeWalk.getPathString.substring(path.length)
              val mode = treeWalk.getFileMode.getBits
              JGitUtil.openFile(git, repository, commit.getTree, treeWalk.getPathString) { in =>
                val tempFile = File.createTempFile("gitbucket", ".archive")
                val size = Using.resource(new FileOutputStream(tempFile)) { out =>
                  IOUtils.copy(
                    EolStreamTypeUtil.wrapInputStream(
                      in,
                      EolStreamTypeUtil
                        .detectStreamType(
                          OperationType.CHECKOUT_OP,
                          git.getRepository.getConfig.get(WorkingTreeOptions.KEY),
                          treeWalk.getAttributes
                        )
                    ),
                    out
                  )
                }

                val entry: ArchiveEntry = entryCreator(entryPath, size, date, mode)
                archive.putArchiveEntry(entry)
                Using.resource(new FileInputStream(tempFile)) { in =>
                  IOUtils.copy(in, archive)
                }
                archive.closeArchiveEntry()
                tempFile.delete()
              }
            }
          }
        }
      }
    }

    val suffix =
      path.split("/").lastOption.collect { case x if x.length > 0 => "-" + x.replace('/', '_') }.getOrElse("")
    val zipRe = """(.+)\.zip$""".r
    val tarRe = """(.+)\.tar\.(gz|bz2|xz)$""".r

    filename match {
      case zipRe(revision) =>
        response.setHeader(
          "Content-Disposition",
          s"attachment; filename=${repository.name}-${revision}${suffix}.zip"
        )
        contentType = "application/octet-stream"
        response.setBufferSize(1024 * 1024)
        Using.resource(new ZipArchiveOutputStream(response.getOutputStream)) { zip =>
          archive(revision, ".zip", zip) { (path, size, date, mode) =>
            val entry = new ZipArchiveEntry(path)
            entry.setSize(size)
            entry.setUnixMode(mode)
            entry.setTime(date.getTime)
            entry
          }
        }
        ()
      case tarRe(revision, compressor) =>
        response.setHeader(
          "Content-Disposition",
          s"attachment; filename=${repository.name}-${revision}${suffix}.tar.${compressor}"
        )
        contentType = "application/octet-stream"
        response.setBufferSize(1024 * 1024)
        Using.resource(compressor match {
          case "gz"  => new GzipCompressorOutputStream(response.getOutputStream)
          case "bz2" => new BZip2CompressorOutputStream(response.getOutputStream)
          case "xz"  => new XZCompressorOutputStream(response.getOutputStream)
        }) { compressorOutputStream =>
          Using.resource(new TarArchiveOutputStream(compressorOutputStream)) { tar =>
            tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_STAR)
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU)
            tar.setAddPaxHeadersForNonAsciiNames(true)
            archive(revision, ".tar.gz", tar) { (path, size, date, mode) =>
              val entry = new TarArchiveEntry(path)
              entry.setSize(size)
              entry.setModTime(date)
              entry.setMode(mode)
              entry
            }
          }
        }
        ()
      case _ =>
        NotFound()
    }
  }

  private def isEditable(owner: String, repository: String, author: String)(implicit context: Context): Boolean =
    hasDeveloperRole(owner, repository, context.loginAccount) || author == context.loginAccount.get.userName

  private def conflict: Constraint = new Constraint() {
    override def validate(name: String, value: String, messages: Messages): Option[String] = {
      val owner = params("owner")
      val repository = params("repository")
      val branch = params("branch")

      LockUtil.lock(s"${owner}/${repository}") {
        Using.resource(Git.open(getRepositoryDir(owner, repository))) { git =>
          val headName = s"refs/heads/${branch}"
          val headTip = git.getRepository.resolve(headName)
          if (headTip.getName != value) {
            Some("Someone pushed new commits before you. Please reload this page and re-apply your changes.")
          } else {
            None
          }
        }
      }
    }
  }

  override protected def renderUncaughtException(
    e: Throwable
  )(implicit request: HttpServletRequest, response: HttpServletResponse): Unit = {
    e.printStackTrace()
  }

}
