package gitbucket.core.service

import com.github.difflib.DiffUtils
import com.github.difflib.patch.DeltaType
import gitbucket.core.api.JsonFormat
import gitbucket.core.controller.Context
import gitbucket.core.model.Profile.*
import gitbucket.core.model.Profile.profile.blockingApi.*
import gitbucket.core.model.activity.OpenPullRequestInfo
import gitbucket.core.model.{CommitComments => _, Session => _, *}
import gitbucket.core.plugin.PluginRegistry
import gitbucket.core.service.RepositoryService.RepositoryInfo
import gitbucket.core.service.SystemSettingsService.SystemSettings
import gitbucket.core.util.Directory.*
import gitbucket.core.util.Implicits.*
import gitbucket.core.util.JGitUtil
import gitbucket.core.util.JGitUtil.{CommitInfo, DiffInfo, getBranchesNoMergeInfo}
import gitbucket.core.util.StringUtil.*
import gitbucket.core.view
import gitbucket.core.view.helpers
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.dircache.{DirCache, DirCacheEntry}
import org.eclipse.jgit.lib.{CommitBuilder, FileMode, ObjectId, ObjectInserter, PersonIdent, Repository}
import org.eclipse.jgit.revwalk.{RevTree, RevWalk}
import org.eclipse.jgit.treewalk.{EmptyTreeIterator, TreeWalk}

import scala.jdk.CollectionConverters.*
import scala.util.Using

trait PullRequestService {
  self: IssuesService & CommitsService & WebHookService & WebHookPullRequestService & RepositoryService & MergeService &
    ActivityService =>
  import PullRequestService.*

  def getPullRequest(owner: String, repository: String, issueId: Int)(implicit
    s: Session
  ): Option[(Issue, PullRequest)] =
    getIssue(owner, repository, issueId.toString).flatMap { issue =>
      PullRequests.filter(_.byPrimaryKey(owner, repository, issueId)).firstOption.map { pullreq =>
        (issue, pullreq)
      }
    }

  def updateCommitId(owner: String, repository: String, issueId: Int, commitIdTo: String, commitIdFrom: String)(implicit
    s: Session
  ): Unit =
    PullRequests
      .filter(_.byPrimaryKey(owner, repository, issueId))
      .map(pr => pr.commitIdTo -> pr.commitIdFrom)
      .update((commitIdTo, commitIdFrom))

  def updateDraftToPullRequest(owner: String, repository: String, issueId: Int)(implicit
    s: Session
  ): Unit =
    PullRequests
      .filter(_.byPrimaryKey(owner, repository, issueId))
      .map(pr => pr.isDraft)
      .update(false)

  def updateBaseBranch(owner: String, repository: String, issueId: Int, baseBranch: String, commitIdTo: String)(implicit
    s: Session
  ): Unit = {
    PullRequests
      .filter(_.byPrimaryKey(owner, repository, issueId))
      .map(pr => pr.branch -> pr.commitIdTo)
      .update((baseBranch, commitIdTo))
  }

  def updateMergedCommitIds(owner: String, repository: String, issueId: Int, mergedCommitIds: Seq[String])(implicit
    s: Session
  ): Unit = {
    PullRequests
      .filter(_.byPrimaryKey(owner, repository, issueId))
      .map(pr => pr.mergedCommitIds)
      .update(mergedCommitIds.mkString(","))
  }

  def getPullRequestCountGroupByUser(closed: Boolean, owner: Option[String], repository: Option[String])(implicit
    s: Session
  ): List[PullRequestCount] =
    PullRequests
      .join(Issues)
      .on { (t1, t2) =>
        t1.byPrimaryKey(t2.userName, t2.repositoryName, t2.issueId)
      }
      .filter { case (t1, t2) =>
        (t2.closed === closed.bind)
          .&&(t1.userName === owner.get.bind, owner.isDefined)
          .&&(t1.repositoryName === repository.get.bind, repository.isDefined)
      }
      .groupBy { case (t1, t2) => t2.openedUserName }
      .map { case (userName, t) => userName -> t.length }
      .sortBy(_._2 desc)
      .list
      .map { x =>
        PullRequestCount(x._1, x._2)
      }

//  def getAllPullRequestCountGroupByUser(closed: Boolean, userName: String)(implicit s: Session): List[PullRequestCount] =
//    PullRequests
//      .innerJoin(Issues).on { (t1, t2) => t1.byPrimaryKey(t2.userName, t2.repositoryName, t2.issueId) }
//      .innerJoin(Repositories).on { case ((t1, t2), t3) => t2.byRepository(t3.userName, t3.repositoryName) }
//      .filter { case ((t1, t2), t3) =>
//        (t2.closed === closed.bind) &&
//          (
//            (t3.isPrivate === false.bind) ||
//            (t3.userName  === userName.bind) ||
//            (Collaborators.filter { t4 => t4.byRepository(t3.userName, t3.repositoryName) && (t4.collaboratorName === userName.bind)} exists)
//          )
//      }
//      .groupBy { case ((t1, t2), t3) => t2.openedUserName }
//      .map { case (userName, t) => userName -> t.length }
//      .sortBy(_._2 desc)
//      .list
//      .map { x => PullRequestCount(x._1, x._2) }

  def createPullRequest(
    originRepository: RepositoryInfo,
    issueId: Int,
    originBranch: String,
    requestUserName: String,
    requestRepositoryName: String,
    requestBranch: String,
    commitIdFrom: String,
    commitIdTo: String,
    isDraft: Boolean,
    loginAccount: Account,
    settings: SystemSettings
  )(implicit s: Session, context: Context): Unit = {
    getIssue(originRepository.owner, originRepository.name, issueId.toString).foreach { baseIssue =>
      PullRequests insert PullRequest(
        originRepository.owner,
        originRepository.name,
        issueId,
        originBranch,
        requestUserName,
        requestRepositoryName,
        requestBranch,
        commitIdFrom,
        commitIdTo,
        isDraft,
        None
      )

      // fetch requested branch
      fetchAsPullRequest(
        originRepository.owner,
        originRepository.name,
        requestUserName,
        requestRepositoryName,
        requestBranch,
        issueId
      )

      // record activity
      val openPullRequestInfo = OpenPullRequestInfo(
        originRepository.owner,
        originRepository.name,
        loginAccount.userName,
        issueId,
        baseIssue.title
      )
      recordActivity(openPullRequestInfo)

      // call web hook
      callPullRequestWebHook("opened", originRepository, issueId, loginAccount, settings)

      getIssue(originRepository.owner, originRepository.name, issueId.toString) foreach { issue =>
        // extract references and create refer comment
        createReferComment(
          originRepository.owner,
          originRepository.name,
          issue,
          baseIssue.title + " " + baseIssue.content,
          loginAccount
        )

        // call hooks
        PluginRegistry().getPullRequestHooks.foreach(_.created(issue, originRepository))
      }
    }
  }

  def getPullRequestsByRequest(userName: String, repositoryName: String, branch: String, closed: Option[Boolean])(
    implicit s: Session
  ): List[PullRequest] =
    PullRequests
      .join(Issues)
      .on { (t1, t2) =>
        t1.byPrimaryKey(t2.userName, t2.repositoryName, t2.issueId)
      }
      .filter { case (t1, t2) =>
        (t1.requestUserName === userName.bind)
          .&&(t1.requestRepositoryName === repositoryName.bind)
          .&&(t1.requestBranch === branch.bind)
          .&&(t2.closed === closed.get.bind, closed.isDefined)
      }
      .map { case (t1, t2) => t1 }
      .list

  def getPullRequestsByBranch(userName: String, repositoryName: String, branch: String, closed: Option[Boolean])(
    implicit s: Session
  ): List[PullRequest] =
    PullRequests
      .join(Issues)
      .on { (t1, t2) =>
        t1.byPrimaryKey(t2.userName, t2.repositoryName, t2.issueId)
      }
      .filter { case (t1, t2) =>
        (t1.requestUserName === userName.bind)
          .&&(t1.requestRepositoryName === repositoryName.bind)
          .&&(t1.branch === branch.bind)
          .&&(t2.closed === closed.get.bind, closed.isDefined)
      }
      .map { case (t1, t2) => t1 }
      .list

  /**
   * for repository viewer.
   * 1. find pull request from `branch` to other branch on same repository
   *   1. return if exists pull request to `defaultBranch`
   *   2. return if exists pull request to other branch
   * 2. return None
   */
  def getPullRequestFromBranch(userName: String, repositoryName: String, branch: String, defaultBranch: String)(implicit
    s: Session
  ): Option[(PullRequest, Issue)] =
    PullRequests
      .join(Issues)
      .on { (t1, t2) =>
        t1.byPrimaryKey(t2.userName, t2.repositoryName, t2.issueId)
      }
      .filter { case (t1, t2) =>
        (t1.requestUserName === userName.bind) &&
        (t1.requestRepositoryName === repositoryName.bind) &&
        (t1.requestBranch === branch.bind) &&
        (t1.userName === userName.bind) &&
        (t1.repositoryName === repositoryName.bind) &&
        (t2.closed === false.bind)
      }
      .sortBy { case (t1, t2) => t1.branch =!= defaultBranch.bind }
      .firstOption

  /**
   * Fetch pull request contents into refs/pull/${issueId}/head and update pull request table.
   */
  def updatePullRequests(
    owner: String,
    repository: String,
    branch: String,
    pusherAccount: Account,
    action: String,
    settings: SystemSettings
  )(implicit
    s: Session,
    c: JsonFormat.Context
  ): Unit = {
    getPullRequestsByRequest(owner, repository, branch, Some(false)).foreach { pullreq =>
      if (Repositories.filter(_.byRepository(pullreq.userName, pullreq.repositoryName)).exists.run) {
        // Update the git repository
        val (commitIdTo, commitIdFrom) = JGitUtil.updatePullRequest(
          pullreq.userName,
          pullreq.repositoryName,
          pullreq.branch,
          pullreq.issueId,
          pullreq.requestUserName,
          pullreq.requestRepositoryName,
          pullreq.requestBranch
        )

        // Collect comment positions
        val positions = getCommitComments(pullreq.userName, pullreq.repositoryName, pullreq.commitIdTo, true)
          .collect {
            case CommitComment(_, _, _, commentId, _, _, Some(file), None, Some(newLine), _, _, _, _, _, _) =>
              (file, commentId, Right(newLine))
            case CommitComment(_, _, _, commentId, _, _, Some(file), Some(oldLine), None, _, _, _, _, _, _) =>
              (file, commentId, Left(oldLine))
          }
          .groupBy { case (file, _, _) => file }
          .map { case (file, comments) =>
            file ->
              comments.map { case (_, commentId, lineNumber) => (commentId, lineNumber) }
          }

        // Update comments position
        updatePullRequestCommentPositions(
          positions,
          pullreq.requestUserName,
          pullreq.requestRepositoryName,
          pullreq.commitIdTo,
          commitIdTo,
          settings
        )

        // Update commit id in the PULL_REQUEST table
        updateCommitId(pullreq.userName, pullreq.repositoryName, pullreq.issueId, commitIdTo, commitIdFrom)

        // call web hook
        callPullRequestWebHookByRequestBranch(
          action,
          getRepository(owner, repository).get,
          pullreq.requestBranch,
          pusherAccount,
          settings
        )
      }
    }
  }

  def updatePullRequestsByApi(
    repository: RepositoryInfo,
    issueId: Int,
    loginAccount: Account,
    settings: SystemSettings,
    title: Option[String],
    body: Option[String],
    state: Option[String],
    base: Option[String]
  )(implicit
    s: Session,
    c: JsonFormat.Context
  ): Unit = {
    getPullRequest(repository.owner, repository.name, issueId).foreach { case (issue, pr) =>
      if (Repositories.filter(_.byRepository(pr.userName, pr.repositoryName)).exists.run) {
        // Update base branch
        base.foreach { _base =>
          if (pr.branch != _base) {
            Using.resource(Git.open(getRepositoryDir(repository.owner, repository.name))) { git =>
              getBranchesNoMergeInfo(git)
                .find(_.name == _base)
                .foreach(br => updateBaseBranch(repository.owner, repository.name, issueId, br.name, br.commitId))
            }
            createComment(
              repository.owner,
              repository.name,
              loginAccount.userName,
              issue.issueId,
              pr.branch + "\r\n" + _base,
              "change_base_branch"
            )
          }
        }
        // Update title and content
        title.foreach { _title =>
          updateIssue(repository.owner, repository.name, issueId, _title, body)
          if (issue.title != _title) {
            createComment(
              repository.owner,
              repository.name,
              loginAccount.userName,
              issue.issueId,
              issue.title + "\r\n" + _title,
              "change_title"
            )
          }
        }
        // Update state
        val action = (state, issue.closed) match {
          case (Some("open"), true) =>
            updateClosed(repository.owner, repository.name, issueId, closed = false)
            "reopened"
          case (Some("closed"), false) =>
            updateClosed(repository.owner, repository.name, issueId, closed = true)
            "closed"
          case _ => "edited"
        }
        // Call web hook
        callPullRequestWebHookByRequestBranch(
          action,
          getRepository(repository.owner, repository.name).get,
          pr.requestBranch,
          loginAccount,
          settings
        )
      }
    }
  }

  def getPullRequestByRequestCommit(
    userName: String,
    repositoryName: String,
    toBranch: String,
    fromBranch: String,
    commitId: String
  )(implicit s: Session): Option[(PullRequest, Issue)] = {
    if (toBranch == fromBranch) {
      None
    } else {
      PullRequests
        .join(Issues)
        .on { (t1, t2) =>
          t1.byPrimaryKey(t2.userName, t2.repositoryName, t2.issueId)
        }
        .filter { case (t1, t2) =>
          (t1.userName === userName.bind) &&
          (t1.repositoryName === repositoryName.bind) &&
          (t1.branch === toBranch.bind) &&
          (t1.requestUserName === userName.bind) &&
          (t1.requestRepositoryName === repositoryName.bind) &&
          (t1.requestBranch === fromBranch.bind) &&
          (t1.commitIdTo === commitId.bind)
        }
        .firstOption
    }
  }

  private def updatePullRequestCommentPositions(
    positions: Map[String, Seq[(Int, Either[Int, Int])]],
    userName: String,
    repositoryName: String,
    oldCommitId: String,
    newCommitId: String,
    settings: SystemSettings
  )(implicit s: Session): Unit = {

    val (_, diffs) =
      getRequestCompareInfo(userName, repositoryName, oldCommitId, userName, repositoryName, newCommitId, settings)

    val patchs = positions.map { case (file, _) =>
      diffs
        .find(x => x.oldPath == file)
        .map { diff =>
          (diff.oldContent, diff.newContent) match {
            case (Some(oldContent), Some(newContent)) =>
              val oldLines = convertLineSeparator(oldContent, "LF").split("\n")
              val newLines = convertLineSeparator(newContent, "LF").split("\n")
              file -> Option(DiffUtils.diff(oldLines.toList.asJava, newLines.toList.asJava))
            case _ =>
              file -> None
          }
        }
        .getOrElse {
          file -> None
        }
    }

    positions.foreach { case (file, comments) =>
      patchs(file) match {
        case Some(patch) =>
          file -> comments.foreach { case (commentId, lineNumber) =>
            lineNumber match {
              case Left(oldLine)  => updateCommitCommentPosition(commentId, newCommitId, Some(oldLine), None)
              case Right(newLine) =>
                var counter = newLine
                patch.getDeltas.asScala.filter(_.getSource.getPosition < newLine).foreach { delta =>
                  delta.getType match {
                    case DeltaType.CHANGE =>
                      if (
                        delta.getSource.getPosition <= newLine - 1 && newLine <= delta.getSource.getPosition + delta.getTarget.getLines.size
                      ) {
                        counter = -1
                      } else {
                        counter = counter + (delta.getTarget.getLines.size - delta.getSource.getLines.size)
                      }
                    case DeltaType.INSERT => counter = counter + delta.getTarget.getLines.size
                    case DeltaType.DELETE => counter = counter - delta.getSource.getLines.size
                    case DeltaType.EQUAL  => // Do nothing
                  }
                }
                if (counter >= 0) {
                  updateCommitCommentPosition(commentId, newCommitId, None, Some(counter))
                }
            }
          }
        case _ =>
          comments.foreach { case (commentId, lineNumber) =>
            lineNumber match {
              case Right(oldLine) => updateCommitCommentPosition(commentId, newCommitId, Some(oldLine), None)
              case Left(newLine)  => updateCommitCommentPosition(commentId, newCommitId, None, Some(newLine))
            }
          }
      }
    }
  }

  def getSingleDiff(
    userName: String,
    repositoryName: String,
    commitId: String,
    path: String
  ): Option[DiffInfo] = {
    Using.resource(
      Git.open(getRepositoryDir(userName, repositoryName))
    ) { git =>
      val newId = git.getRepository.resolve(commitId)
      JGitUtil.getDiff(git, None, newId.getName, path)
    }
  }

  def getSingleDiff(
    userName: String,
    repositoryName: String,
    branch: String,
    requestUserName: String,
    requestRepositoryName: String,
    requestCommitId: String,
    path: String
  ): Option[DiffInfo] = {
    Using.resources(
      Git.open(getRepositoryDir(userName, repositoryName)),
      Git.open(getRepositoryDir(requestUserName, requestRepositoryName))
    ) { (oldGit, newGit) =>
      val oldId = oldGit.getRepository.resolve(branch)
      val newId = newGit.getRepository.resolve(requestCommitId)

      JGitUtil.getDiff(newGit, Some(oldId.getName), newId.getName, path)
    }
  }

  def getRequestCompareInfo(
    userName: String,
    repositoryName: String,
    branch: String,
    requestUserName: String,
    requestRepositoryName: String,
    requestCommitId: String,
    settings: SystemSettings
  ): (Seq[Seq[CommitInfo]], Seq[DiffInfo]) =
    Using.resources(
      Git.open(getRepositoryDir(userName, repositoryName)),
      Git.open(getRepositoryDir(requestUserName, requestRepositoryName))
    ) { (oldGit, newGit) =>
      val oldId = oldGit.getRepository.resolve(branch)
      val newId = newGit.getRepository.resolve(requestCommitId)

      val commits = newGit.log
        .addRange(oldId, newId)
        .call
        .iterator
        .asScala
        .map { revCommit =>
          new CommitInfo(revCommit)
        }
        .toList
        .splitWith { (commit1, commit2) =>
          helpers.date(commit1.commitTime) == view.helpers.date(commit2.commitTime)
        }

      val diffs = JGitUtil.getDiffs(
        git = newGit,
        from = Some(oldId.getName),
        to = newId.getName,
        fetchContent = true,
        makePatch = false,
        maxFiles = settings.repositoryViewer.maxDiffFiles,
        maxLines = settings.repositoryViewer.maxDiffLines
      )

      (commits, diffs)
    }

  def getPullRequestComments(userName: String, repositoryName: String, issueId: Int, commits: Seq[CommitInfo])(implicit
    s: Session
  ): Seq[Comment] = {
    (commits.flatMap(commit => getCommitComments(userName, repositoryName, commit.id, true)) ++ getComments(
      userName,
      repositoryName,
      issueId
    )).groupBy {
      case x: IssueComment                        => (Some(x.commentId), None, None, None)
      case x: CommitComment if x.fileName.isEmpty => (Some(x.commentId), None, None, None)
      case x: CommitComment                       => (None, x.fileName, x.originalOldLine, x.originalNewLine)
      case x                                      => throw new MatchError(x)
    }.toSeq
      .map {
        // Normal comment
        case ((Some(_), _, _, _), comments) =>
          comments.head
        // Comment on a specific line of a commit
        case ((None, Some(fileName), oldLine, newLine), comments) =>
          gitbucket.core.model.CommitComments(
            fileName = fileName,
            commentedUserName = comments.head.commentedUserName,
            registeredDate = comments.head.registeredDate,
            comments = comments.map(_.asInstanceOf[CommitComment]),
            diff = loadCommitCommentDiff(
              userName,
              repositoryName,
              comments.head.asInstanceOf[CommitComment].originalCommitId,
              fileName,
              oldLine,
              newLine
            )
          )
      }
      .sortWith(_.registeredDate before _.registeredDate)
  }

  def markMergeAndClosePullRequest(userName: String, owner: String, repository: String, pull: PullRequest)(implicit
    s: Session
  ): Unit = {
    createComment(owner, repository, userName, pull.issueId, "Merged by user", "merge")
    createComment(owner, repository, userName, pull.issueId, "Close", "close")
    updateClosed(owner, repository, pull.issueId, true)
  }

  /**
   * Parses branch identifier and extracts owner and branch name as tuple.
   *
   * - "owner:branch" to ("owner", "branch")
   * - "branch" to ("defaultOwner", "branch")
   */
  def parseCompareIdentifier(value: String, defaultOwner: String): (String, String) =
    if (value.contains(':')) {
      val array = value.split(":")
      (array(0), array(1))
    } else {
      (defaultOwner, value)
    }

  def getPullRequestCommitFromTo(
    originRepository: RepositoryInfo,
    forkedRepository: RepositoryInfo,
    originId: String,
    forkedId: String
  ): (Option[ObjectId], Option[ObjectId]) = {
    Using.resources(
      Git.open(getRepositoryDir(originRepository.owner, originRepository.name)),
      Git.open(getRepositoryDir(forkedRepository.owner, forkedRepository.name))
    ) { case (oldGit, newGit) =>
      if (originRepository.branchList.contains(originId)) {
        val forkedId2 =
          forkedRepository.tags.collectFirst { case x if x.name == forkedId => x.commitId }.getOrElse(forkedId)

        val originId2 = JGitUtil.getForkedCommitId(
          oldGit,
          newGit,
          originRepository.owner,
          originRepository.name,
          originId,
          forkedRepository.owner,
          forkedRepository.name,
          forkedId2
        )

        (Option(oldGit.getRepository.resolve(originId2)), Option(newGit.getRepository.resolve(forkedId2)))

      } else {
        val originId2 =
          originRepository.tags.collectFirst { case x if x.name == originId => x.commitId }.getOrElse(originId)
        val forkedId2 =
          forkedRepository.tags.collectFirst { case x if x.name == forkedId => x.commitId }.getOrElse(forkedId)

        (Option(oldGit.getRepository.resolve(originId2)), Option(newGit.getRepository.resolve(forkedId2)))
      }
    }
  }

  /**
   * Creates a revert commit directly on the bare repository without cloning.
   * This works by creating a reverse diff of the merged commits and applying it to the base branch.
   */
  def createRevertCommit(
    git: Git,
    targetBranch: String,
    mergedCommitIds: Seq[String],
    committerName: String,
    committerEmail: String,
    commitMessage: String
  ): Either[String, ObjectId] = {
    try {
      val repository = git.getRepository
      val inserter = repository.newObjectInserter()

      Using.resource(new RevWalk(repository)) { revWalk =>
        // Get the target branch head
        val targetHeadId = repository.resolve(s"refs/heads/$targetBranch")
        if (targetHeadId == null) {
          return Left(s"Branch $targetBranch not found")
        }
        val targetHead = revWalk.parseCommit(targetHeadId)

        // Parse the commits to revert (in reverse order for proper reverting)
        val commitsToRevert = mergedCommitIds.reverse.map { commitId =>
          val objectId = repository.resolve(commitId)
          if (objectId == null) {
            throw new IllegalArgumentException(s"Commit $commitId not found")
          }
          revWalk.parseCommit(objectId)
        }

        // Start with the current tree of the target branch
        var currentTreeId = targetHead.getTree.getId

        // Apply reverse changes for each commit
        for (commit <- commitsToRevert) {
          val parentCommit = if (commit.getParentCount > 0) {
            revWalk.parseCommit(commit.getParent(0))
          } else {
            // This is an initial commit, revert by creating empty tree
            null
          }

          // Create new tree by applying reverse diff
          currentTreeId = createTreeWithReverseDiff(
            repository,
            inserter,
            currentTreeId,
            if (parentCommit != null) parentCommit.getTree else null,
            commit.getTree
          )
        }

        // Create the revert commit
        val commitBuilder = new CommitBuilder()
        commitBuilder.setTreeId(currentTreeId)
        commitBuilder.setParentId(targetHeadId)
        commitBuilder.setAuthor(new PersonIdent(committerName, committerEmail))
        commitBuilder.setCommitter(new PersonIdent(committerName, committerEmail))
        commitBuilder.setMessage(commitMessage)

        val revertCommitId = inserter.insert(commitBuilder)
        inserter.flush()

        // Update the branch to point to the new commit
        val refUpdate = repository.updateRef(s"refs/heads/$targetBranch")
        refUpdate.setNewObjectId(revertCommitId)
        refUpdate.update()

        Right(revertCommitId)
      }
    } catch {
      case ex: Exception =>
        Left(ex.getMessage)
    }
  }

  /**
   * Creates a new tree by applying the reverse of changes between fromTree and toTree to baseTree.
   */
  private def createTreeWithReverseDiff(
    repository: Repository,
    inserter: ObjectInserter,
    baseTreeId: ObjectId,
    fromTree: RevTree,
    toTree: RevTree
  ): ObjectId = {
    val dirCache = DirCache.newInCore()
    val builder = dirCache.builder()

    val entries = scala.collection.mutable.Map[String, DirCacheEntry]()

    // Start with all files from the base tree
    if (baseTreeId != null) {
      Using.resource(new TreeWalk(repository)) { walk =>
        walk.addTree(baseTreeId)
        walk.setRecursive(true)

        while (walk.next()) {
          val entry = new DirCacheEntry(walk.getPathString)
          entry.setFileMode(walk.getFileMode(0))
          entry.setObjectId(walk.getObjectId(0))
          entries(walk.getPathString) = entry
        }
      }
    }

    // Apply reverse changes: if a file was added in the original change, remove it
    // if a file was deleted, restore it; if modified, restore original content
    Using.resource(new TreeWalk(repository)) { walk =>
      if (fromTree != null) walk.addTree(fromTree) else walk.addTree(new EmptyTreeIterator())
      walk.addTree(toTree)
      walk.setRecursive(true)

      while (walk.next()) {
        val path = walk.getPathString
        val fromMode = if (walk.getTreeCount > 1) walk.getFileMode(0) else FileMode.MISSING
        val toMode = walk.getFileMode(walk.getTreeCount - 1)

        if (fromMode == FileMode.MISSING && toMode != FileMode.MISSING) {
          // File was added in the original change, so remove it in the revert
          entries.remove(path)
        } else if (fromMode != FileMode.MISSING && toMode == FileMode.MISSING) {
          // File was deleted in the original change, so restore it in the revert
          val entry = new DirCacheEntry(path)
          entry.setFileMode(fromMode)
          entry.setObjectId(walk.getObjectId(0))
          entries(path) = entry
        } else if (fromMode != FileMode.MISSING && toMode != FileMode.MISSING) {
          val fromObjectId = walk.getObjectId(0)
          val toObjectId = walk.getObjectId(walk.getTreeCount - 1)

          if (!fromObjectId.equals(toObjectId)) {
            // File was modified in the original change, restore original content
            val entry = new DirCacheEntry(path)
            entry.setFileMode(fromMode)
            entry.setObjectId(fromObjectId)
            entries(path) = entry
          }
        }
      }
    }

    // Build the final tree
    entries.values.toSeq.sortBy(_.getPathString).foreach(builder.add)
    builder.finish()
    dirCache.writeTree(inserter)
  }
}

object PullRequestService {

  val PullRequestLimit = 25

  case class PullRequestCount(userName: String, count: Int)

  case class MergeStatus(
    conflictMessage: Option[String],
    commitStatuses: List[CommitStatus],
    branchProtection: ProtectedBranchService.ProtectedBranchInfo,
    branchIsOutOfDate: Boolean,
    hasUpdatePermission: Boolean,
    needStatusCheck: Boolean,
    hasMergePermission: Boolean,
    commitIdTo: String
  ) {

    val hasConflict: Boolean = conflictMessage.isDefined
    val statuses: List[CommitStatus] =
      commitStatuses ++ (branchProtection.contexts.getOrElse(Nil).toSet -- commitStatuses.map(_.context).toSet)
        .map(CommitStatus.pending(branchProtection.owner, branchProtection.repository, _))
    val hasRequiredStatusProblem: Boolean = needStatusCheck && branchProtection.contexts
      .getOrElse(Nil)
      .exists(context => !statuses.find(_.context == context).map(_.state).contains(CommitState.SUCCESS))
    val hasProblem: Boolean = hasRequiredStatusProblem || hasConflict || (statuses.nonEmpty && CommitState.combine(
      statuses.map(_.state).toSet
    ) != CommitState.SUCCESS)
    val canUpdate: Boolean = branchIsOutOfDate && !hasConflict
    val canMerge: Boolean = hasMergePermission && !hasConflict && !hasRequiredStatusProblem
    lazy val commitStateSummary: (CommitState, String) = {
      val stateMap = statuses.groupBy(_.state)
      val state = CommitState.combine(stateMap.keySet)
      val summary = stateMap.map { case (keyState, states) => s"${states.size} ${keyState.name}" }.mkString(", ")
      state -> summary
    }
    lazy val statusesAndRequired: List[(CommitStatus, Boolean)] = statuses.map { s =>
      s -> branchProtection.contexts.getOrElse(Nil).contains(s.context)
    }
    lazy val isAllSuccess: Boolean = commitStateSummary._1 == CommitState.SUCCESS
  }
}
