package gitbucket.core.controller

import java.io.File
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

import gitbucket.core.plugin.PluginRegistry
import gitbucket.core.repo.html
import gitbucket.core.helper
import gitbucket.core.service._
import gitbucket.core.util._
import gitbucket.core.util.JGitUtil._
import gitbucket.core.util.StringUtil._
import gitbucket.core.util.SyntaxSugars._
import gitbucket.core.util.Implicits._
import gitbucket.core.util.Directory._
import gitbucket.core.model.{Account, CommitState, CommitStatus, WebHook}
import gitbucket.core.service.WebHookService._
import gitbucket.core.view
import gitbucket.core.view.helpers
import org.scalatra.forms._
import org.apache.commons.io.FileUtils
import org.eclipse.jgit.api.{ArchiveCommand, Git}
import org.eclipse.jgit.archive.{TgzFormat, ZipFormat}
import org.eclipse.jgit.dircache.{DirCache, DirCacheBuilder}
import org.eclipse.jgit.errors.MissingObjectException
import org.eclipse.jgit.lib._
import org.eclipse.jgit.transport.{ReceiveCommand, ReceivePack}
import org.json4s.jackson.Serialization
import org.scalatra._
import org.scalatra.i18n.Messages

class RepositoryViewerController
    extends RepositoryViewerControllerBase
    with RepositoryService
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
    with PullRequestService
    with CommitStatusService
    with WebHookPullRequestService
    with WebHookPullRequestReviewCommentService
    with ProtectedBranchService

/**
 * The repository viewer.
 */
trait RepositoryViewerControllerBase extends ControllerBase {
  self: RepositoryService
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
    message: Option[String]
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
    commit: String
  )

  case class DeleteForm(
    branch: String,
    path: String,
    message: Option[String],
    fileName: String,
    commit: String
  )

  case class CommentForm(
    fileName: Option[String],
    oldLineNumber: Option[Int],
    newLineNumber: Option[Int],
    content: String,
    issueId: Option[Int]
  )

  val uploadForm = mapping(
    "branch" -> trim(label("Branch", text(required))),
    "path" -> trim(label("Path", text())),
    "uploadFiles" -> trim(label("Upload files", text(required))),
    "message" -> trim(label("Message", optional(text()))),
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
    "commit" -> trim(label("Commit", text(required, conflict)))
  )(EditorForm.apply)

  val deleteForm = mapping(
    "branch" -> trim(label("Branch", text(required))),
    "path" -> trim(label("Path", text())),
    "message" -> trim(label("Message", optional(text()))),
    "fileName" -> trim(label("Filename", text(required))),
    "commit" -> trim(label("Commit", text(required, conflict)))
  )(DeleteForm.apply)

  val commentForm = mapping(
    "fileName" -> trim(label("Filename", optional(text()))),
    "oldLineNumber" -> trim(label("Old line number", optional(number()))),
    "newLineNumber" -> trim(label("New line number", optional(number()))),
    "content" -> trim(label("Content", text(required))),
    "issueId" -> trim(label("Issue Id", optional(number())))
  )(CommentForm.apply)

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
          branch = "master",
          repository = repository,
          enableWikiLink = params("enableWikiLink").toBoolean,
          enableRefsLink = params("enableRefsLink").toBoolean,
          enableAnchor = false
        )
      case None =>
        helpers.markdown(
          markdown = params("content"),
          repository = repository,
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

    def getStatuses(sha: String): List[CommitStatus] = {
      getCommitStatues(repository.owner, repository.name, sha)
    }

    def getSummary(statuses: List[CommitStatus]): (CommitState, String) = {
      val stateMap = statuses.groupBy(_.state)
      val state = CommitState.combine(stateMap.keySet)
      val summary = stateMap.map { case (keyState, states) => states.size + " " + keyState.name }.mkString(", ")
      state -> summary
    }

    using(Git.open(getRepositoryDir(repository.owner, repository.name))) {
      git =>
        JGitUtil.getCommitLog(git, branchName, page, 30, path) match {
          case Right((logs, hasNext)) =>
            html.commits(
              if (path.isEmpty) Nil else path.split("/").toList,
              branchName,
              repository,
              logs.splitWith { (commit1, commit2) =>
                view.helpers.date(commit1.commitTime) == view.helpers.date(commit2.commitTime)
              },
              page,
              hasNext,
              hasDeveloperRole(repository.owner, repository.name, context.loginAccount),
              getStatuses,
              getSummary
            )
          case Left(_) => NotFound()
        }
    }
  })

  get("/:owner/:repository/new/*")(writableUsersOnly { repository =>
    val (branch, path) = repository.splitPath(multiParams("splat").head)
    val protectedBranch = getProtectedBranchInfo(repository.owner, repository.name, branch)
      .needStatusCheck(context.loginAccount.get.userName)

    using(Git.open(getRepositoryDir(repository.owner, repository.name))) { git =>
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
    html.upload(branch, repository, if (path.length == 0) Nil else path.split("/").toList, protectedBranch)
  })

  post("/:owner/:repository/upload", uploadForm)(writableUsersOnly { (form, repository) =>
    val files = form.uploadFiles.split("\n").map { line =>
      val i = line.indexOf(':')
      CommitFile(line.substring(0, i).trim, line.substring(i + 1).trim)
    }

    commitFiles(
      repository = repository,
      branch = form.branch,
      path = form.path,
      files = files,
      message = form.message.getOrElse("Add files via upload")
    )

    if (form.path.length == 0) {
      redirect(s"/${repository.owner}/${repository.name}/tree/${form.branch}")
    } else {
      redirect(s"/${repository.owner}/${repository.name}/tree/${form.branch}/${form.path}")
    }
  })

  get("/:owner/:repository/edit/*")(writableUsersOnly { repository =>
    val (branch, path) = repository.splitPath(multiParams("splat").head)
    val protectedBranch = getProtectedBranchInfo(repository.owner, repository.name, branch)
      .needStatusCheck(context.loginAccount.get.userName)

    using(Git.open(getRepositoryDir(repository.owner, repository.name))) {
      git =>
        val revCommit = JGitUtil.getRevCommitFromId(git, git.getRepository.resolve(branch))

        getPathObjectId(git, path, revCommit).map { objectId =>
          val paths = path.split("/")
          html.editor(
            branch = branch,
            repository = repository,
            pathList = paths.take(paths.size - 1).toList,
            fileName = Some(paths.last),
            content = JGitUtil.getContentInfo(git, path, objectId),
            protectedBranch = protectedBranch,
            commit = revCommit.getName
          )
        } getOrElse NotFound()
    }
  })

  get("/:owner/:repository/remove/*")(writableUsersOnly { repository =>
    val (branch, path) = repository.splitPath(multiParams("splat").head)
    using(Git.open(getRepositoryDir(repository.owner, repository.name))) {
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
    commitFile(
      repository = repository,
      branch = form.branch,
      path = form.path,
      newFileName = Some(form.newFileName),
      oldFileName = None,
      content = appendNewLine(convertLineSeparator(form.content, form.lineSeparator), form.lineSeparator),
      charset = form.charset,
      message = form.message.getOrElse(s"Create ${form.newFileName}"),
      commit = form.commit
    )

    redirect(
      s"/${repository.owner}/${repository.name}/blob/${form.branch}/${if (form.path.length == 0) urlEncode(form.newFileName)
      else s"${form.path}/${urlEncode(form.newFileName)}"}"
    )
  })

  post("/:owner/:repository/update", editorForm)(writableUsersOnly { (form, repository) =>
    commitFile(
      repository = repository,
      branch = form.branch,
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
      commit = form.commit
    )

    redirect(
      s"/${repository.owner}/${repository.name}/blob/${urlEncode(form.branch)}/${if (form.path.length == 0) urlEncode(form.newFileName)
      else s"${form.path}/${urlEncode(form.newFileName)}"}"
    )
  })

  post("/:owner/:repository/remove", deleteForm)(writableUsersOnly { (form, repository) =>
    commitFile(
      repository = repository,
      branch = form.branch,
      path = form.path,
      newFileName = None,
      oldFileName = Some(form.fileName),
      content = "",
      charset = "",
      message = form.message.getOrElse(s"Delete ${form.fileName}"),
      commit = form.commit
    )

    redirect(
      s"/${repository.owner}/${repository.name}/tree/${form.branch}${if (form.path.length == 0) "" else form.path}"
    )
  })

  get("/:owner/:repository/raw/*")(referrersOnly { repository =>
    val (id, path) = repository.splitPath(multiParams("splat").head)
    using(Git.open(getRepositoryDir(repository.owner, repository.name))) { git =>
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
    using(Git.open(getRepositoryDir(repository.owner, repository.name))) {
      git =>
        val revCommit = JGitUtil.getRevCommitFromId(git, git.getRepository.resolve(id))
        getPathObjectId(git, path, revCommit).map {
          objectId =>
            if (raw) {
              // Download (This route is left for backword compatibility)
              responseRawFile(git, objectId, path, repository)
            } else {
              html.blob(
                branch = id,
                repository = repository,
                pathList = path.split("/").toList,
                content = JGitUtil.getContentInfo(git, path, objectId),
                latestCommit = new JGitUtil.CommitInfo(JGitUtil.getLastModifiedCommit(git, revCommit, path)),
                hasWritePermission = hasDeveloperRole(repository.owner, repository.name, context.loginAccount),
                isBlame = request.paths(2) == "blame",
                isLfsFile = isLfsFile(git, objectId)
              )
            }
        } getOrElse NotFound()
    }
  })

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
    using(Git.open(getRepositoryDir(repository.owner, repository.name))) {
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
      using(Git.open(getRepositoryDir(repository.owner, repository.name))) {
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
                getCommitComments(repository.owner, repository.name, id, true),
                repository,
                diffs,
                oldCommitId,
                hasDeveloperRole(repository.owner, repository.name, context.loginAccount)
              )
          }
      }
    } catch {
      case e: MissingObjectException => NotFound()
    }
  })

  get("/:owner/:repository/patch/:id")(referrersOnly { repository =>
    try {
      using(Git.open(getRepositoryDir(repository.owner, repository.name))) { git =>
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
      using(Git.open(getRepositoryDir(repository.owner, repository.name))) { git =>
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
      repository.owner,
      repository.name,
      id,
      context.loginAccount.get.userName,
      form.content,
      form.fileName,
      form.oldLineNumber,
      form.newLineNumber,
      form.issueId
    )
    form.issueId match {
      case Some(issueId) =>
        recordCommentPullRequestActivity(
          repository.owner,
          repository.name,
          context.loginAccount.get.userName,
          issueId,
          form.content
        )
      case None =>
        recordCommentCommitActivity(
          repository.owner,
          repository.name,
          context.loginAccount.get.userName,
          id,
          form.content
        )
    }
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
      repository = repository
    )
  })

  ajaxPost("/:owner/:repository/commit/:id/comment/_data/new", commentForm)(readableUsersOnly { (form, repository) =>
    val id = params("id")
    val commentId = createCommitComment(
      repository.owner,
      repository.name,
      id,
      context.loginAccount.get.userName,
      form.content,
      form.fileName,
      form.oldLineNumber,
      form.newLineNumber,
      form.issueId
    )
    val comment = getCommitComment(repository.owner, repository.name, commentId.toString).get
    form.issueId match {
      case Some(issueId) =>
        getPullRequest(repository.owner, repository.name, issueId).foreach {
          case (issue, pullRequest) =>
            recordCommentPullRequestActivity(
              repository.owner,
              repository.name,
              context.loginAccount.get.userName,
              issueId,
              form.content
            )
            PluginRegistry().getPullRequestHooks.foreach(_.addedComment(commentId, form.content, issue, repository))
            callPullRequestReviewCommentWebHook(
              "create",
              comment,
              repository,
              issue,
              pullRequest,
              context.baseUrl,
              context.loginAccount.get
            )
        }
      case None =>
        recordCommentCommitActivity(
          repository.owner,
          repository.name,
          context.loginAccount.get.userName,
          id,
          form.content
        )
    }
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
                  enableWikiLink = false,
                  enableRefsLink = true,
                  enableAnchor = true,
                  enableLineBreaks = true,
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
    val branches = JGitUtil
      .getBranches(
        owner = repository.owner,
        name = repository.name,
        defaultBranch = repository.repository.defaultBranch,
        origin = repository.repository.originUserName.isEmpty
      )
      .sortBy(br => (br.mergeInfo.isEmpty, br.commitTime))
      .map(
        br =>
          (
            br,
            getPullRequestByRequestCommit(
              repository.owner,
              repository.name,
              repository.repository.defaultBranch,
              br.name,
              br.commitId
            ),
            protectedBranches.contains(br.name)
        )
      )
      .reverse

    html.branches(branches, hasDeveloperRole(repository.owner, repository.name, context.loginAccount), repository)
  })

  /**
   * Creates a branch.
   */
  post("/:owner/:repository/branches")(writableUsersOnly { repository =>
    val newBranchName = params.getOrElse("new", halt(400))
    val fromBranchName = params.getOrElse("from", halt(400))
    using(Git.open(getRepositoryDir(repository.owner, repository.name))) { git =>
      JGitUtil.createBranch(git, fromBranchName, newBranchName)
    } match {
      case Right(message) =>
        flash += "info" -> message
        redirect(
          s"/${repository.owner}/${repository.name}/tree/${StringUtil.urlEncode(newBranchName).replace("%2F", "/")}"
        )
      case Left(message) =>
        flash += "error" -> message
        redirect(s"/${repository.owner}/${repository.name}/tree/${fromBranchName}")
    }
  })

  /**
   * Deletes branch.
   */
  get("/:owner/:repository/delete/*")(writableUsersOnly { repository =>
    val branchName = multiParams("splat").head
    val userName = context.loginAccount.get.userName
    if (repository.repository.defaultBranch != branchName) {
      using(Git.open(getRepositoryDir(repository.owner, repository.name))) { git =>
        git.branchDelete().setForce(true).setBranchNames(branchName).call()
        recordDeleteBranchActivity(repository.owner, repository.name, userName, branchName)
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

  /**
   * Download repository contents as an archive.
   */
  get("/:owner/:repository/archive/*")(referrersOnly { repository =>
    multiParams("splat").head match {
      case name if name.endsWith(".zip") =>
        archiveRepository(name, ".zip", repository)
      case name if name.endsWith(".tar.gz") =>
        archiveRepository(name, ".tar.gz", repository)
      case _ => BadRequest()
    }
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
    using(Git.open(getRepositoryDir(repository.owner, repository.name))) { git =>
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
    using(Git.open(getRepositoryDir(repository.owner, repository.name))) { git =>
      val treeId = params("tree")
      contentType = formats("json")
      Map("paths" -> JGitUtil.getAllFileListByTreeId(git, treeId))
    }
  })

  case class UploadFiles(branch: String, path: String, fileIds: Map[String, String], message: String) {
    lazy val isValid: Boolean = fileIds.nonEmpty
  }

  case class CommitFile(id: String, name: String)

  private def commitFiles(
    repository: RepositoryService.RepositoryInfo,
    files: Seq[CommitFile],
    branch: String,
    path: String,
    message: String
  ) = {
    // prepend path to the filename
    val newFiles = files.map { file =>
      file.copy(name = if (path.length == 0) file.name else s"${path}/${file.name}")
    }

    _commitFile(repository, branch, message) {
      case (git, headTip, builder, inserter) =>
        JGitUtil.processTree(git, headTip) { (path, tree) =>
          if (!newFiles.exists(_.name.contains(path))) {
            builder.add(JGitUtil.createDirCacheEntry(path, tree.getEntryFileMode, tree.getEntryObjectId))
          }
        }

        newFiles.foreach { file =>
          val bytes = FileUtils.readFileToByteArray(new File(getTemporaryDir(session.getId), file.id))
          builder.add(
            JGitUtil.createDirCacheEntry(file.name, FileMode.REGULAR_FILE, inserter.insert(Constants.OBJ_BLOB, bytes))
          )
          builder.finish()
        }
    }
  }

  private def commitFile(
    repository: RepositoryService.RepositoryInfo,
    branch: String,
    path: String,
    newFileName: Option[String],
    oldFileName: Option[String],
    content: String,
    charset: String,
    message: String,
    commit: String
  ) = {

    val newPath = newFileName.map { newFileName =>
      if (path.length == 0) newFileName else s"${path}/${newFileName}"
    }
    val oldPath = oldFileName.map { oldFileName =>
      if (path.length == 0) oldFileName else s"${path}/${oldFileName}"
    }

    _commitFile(repository, branch, message) {
      case (git, headTip, builder, inserter) =>
        if (headTip.getName == commit) {
          val permission = JGitUtil
            .processTree(git, headTip) { (path, tree) =>
              // Add all entries except the editing file
              if (!newPath.contains(path) && !oldPath.contains(path)) {
                builder.add(JGitUtil.createDirCacheEntry(path, tree.getEntryFileMode, tree.getEntryObjectId))
              }
              // Retrieve permission if file exists to keep it
              oldPath.collect { case x if x == path => tree.getEntryFileMode.getBits }
            }
            .flatten
            .headOption

          newPath.foreach { newPath =>
            builder.add(JGitUtil.createDirCacheEntry(newPath, permission.map { bits =>
              FileMode.fromBits(bits)
            } getOrElse FileMode.REGULAR_FILE, inserter.insert(Constants.OBJ_BLOB, content.getBytes(charset))))
          }
          builder.finish()
        }
    }
  }

  private def _commitFile(repository: RepositoryService.RepositoryInfo, branch: String, message: String)(
    f: (Git, ObjectId, DirCacheBuilder, ObjectInserter) => Unit
  ) = {

    LockUtil.lock(s"${repository.owner}/${repository.name}") {
      using(Git.open(getRepositoryDir(repository.owner, repository.name))) { git =>
        val loginAccount = context.loginAccount.get
        val builder = DirCache.newInCore.builder()
        val inserter = git.getRepository.newObjectInserter()
        val headName = s"refs/heads/${branch}"
        val headTip = git.getRepository.resolve(headName)

        f(git, headTip, builder, inserter)

        val commitId = JGitUtil.createNewCommit(
          git,
          inserter,
          headTip,
          builder.getDirCache.writeTree(inserter),
          headName,
          loginAccount.fullName,
          loginAccount.mailAddress,
          message
        )

        inserter.flush()
        inserter.close()

        val receivePack = new ReceivePack(git.getRepository)
        val receiveCommand = new ReceiveCommand(headTip, commitId, headName)

        // call post commit hook
        val error = PluginRegistry().getReceiveHooks.flatMap { hook =>
          hook.preReceive(repository.owner, repository.name, receivePack, receiveCommand, loginAccount.userName)
        }.headOption

        error match {
          case Some(error) =>
            // commit is rejected
            // TODO Notify commit failure to edited user
            val refUpdate = git.getRepository.updateRef(headName)
            refUpdate.setNewObjectId(headTip)
            refUpdate.setForceUpdate(true)
            refUpdate.update()

          case None =>
            // update refs
            val refUpdate = git.getRepository.updateRef(headName)
            refUpdate.setNewObjectId(commitId)
            refUpdate.setForceUpdate(false)
            refUpdate.setRefLogIdent(new PersonIdent(loginAccount.fullName, loginAccount.mailAddress))
            refUpdate.update()

            // update pull request
            updatePullRequests(repository.owner, repository.name, branch)

            // record activity
            val commitInfo = new CommitInfo(JGitUtil.getRevCommitFromId(git, commitId))
            recordPushActivity(repository.owner, repository.name, loginAccount.userName, branch, List(commitInfo))

            // create issue comment by commit message
            createIssueComment(repository.owner, repository.name, commitInfo)

            // close issue by commit message
            if (branch == repository.repository.defaultBranch) {
              closeIssuesFromMessage(message, loginAccount.userName, repository.owner, repository.name)
            }

            // call post commit hook
            PluginRegistry().getReceiveHooks.foreach { hook =>
              hook.postReceive(repository.owner, repository.name, receivePack, receiveCommand, loginAccount.userName)
            }

            //call web hook
            callPullRequestWebHookByRequestBranch("synchronize", repository, branch, context.baseUrl, loginAccount)
            val commit = new JGitUtil.CommitInfo(JGitUtil.getRevCommitFromId(git, commitId))
            callWebHookOf(repository.owner, repository.name, WebHook.Push) {
              getAccountByUserName(repository.owner).map { ownerAccount =>
                WebHookPushPayload(
                  git,
                  loginAccount,
                  headName,
                  repository,
                  List(commit),
                  ownerAccount,
                  oldId = headTip,
                  newId = commitId
                )
              }
            }
        }
      }
    }
  }

  private val readmeFiles = PluginRegistry().renderableExtensions.map { extension =>
    s"readme.${extension}"
  } ++ Seq("readme.txt", "readme")

  /**
   * Provides HTML of the file list.
   *
   * @param repository the repository information
   * @param revstr the branch name or commit id(optional)
   * @param path the directory path (optional)
   * @return HTML of the file list
   */
  private def fileList(repository: RepositoryService.RepositoryInfo, revstr: String = "", path: String = ".") = {
    using(Git.open(getRepositoryDir(repository.owner, repository.name))) { git =>
      if (JGitUtil.isEmpty(git)) {
        html.guide(repository, hasDeveloperRole(repository.owner, repository.name, context.loginAccount))
      } else {
        // get specified commit
        JGitUtil.getDefaultBranch(git, repository, revstr).map {
          case (objectId, revision) =>
            defining(JGitUtil.getRevCommitFromId(git, objectId)) { revCommit =>
              val lastModifiedCommit =
                if (path == ".") revCommit else JGitUtil.getLastModifiedCommit(git, revCommit, path)
              // get files
              val files = JGitUtil.getFileList(git, revision, path, context.settings.baseUrl)
              val parentPath = if (path == ".") Nil else path.split("/").toList
              // process README.md or README.markdown
              val readme = files
                .find { file =>
                  !file.isDirectory && readmeFiles.contains(file.name.toLowerCase)
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
                JGitUtil.getCommitCount(repository.owner, repository.name, lastModifiedCommit.getName),
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

  private def archiveRepository(name: String, suffix: String, repository: RepositoryService.RepositoryInfo): Unit = {
    val revision = name.stripSuffix(suffix)

    using(Git.open(getRepositoryDir(repository.owner, repository.name))) { git =>
      val oid = git.getRepository.resolve(revision)
      val revCommit = JGitUtil.getRevCommitFromId(git, oid)
      val sha1 = oid.getName()
      val repositorySuffix = (if (sha1.startsWith(revision)) sha1 else revision).replace('/', '-')
      val filename = repository.name + "-" + repositorySuffix + suffix

      contentType = "application/octet-stream"
      response.setHeader("Content-Disposition", s"attachment; filename=${filename}")
      response.setBufferSize(1024 * 1024);

      git.archive
        .setFormat(suffix.tail)
        .setPrefix(repository.name + "-" + repositorySuffix + "/")
        .setTree(revCommit)
        .setOutputStream(response.getOutputStream)
        .call()
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
        using(Git.open(getRepositoryDir(owner, repository))) { git =>
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
