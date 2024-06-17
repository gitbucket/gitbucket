package gitbucket.core.service
import gitbucket.core.api.JsonFormat
import gitbucket.core.model.{Account, WebHook}
import gitbucket.core.model.Profile.profile.blockingApi._
import gitbucket.core.model.activity.{CloseIssueInfo, PushInfo}
import gitbucket.core.plugin.PluginRegistry
import gitbucket.core.service.SystemSettingsService.SystemSettings
import gitbucket.core.service.WebHookService.WebHookPushPayload
import gitbucket.core.util.Directory.getRepositoryDir
import gitbucket.core.util.JGitUtil.CommitInfo
import gitbucket.core.util.{JGitUtil, LockUtil}
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.dircache.{DirCache, DirCacheBuilder}
import org.eclipse.jgit.lib._
import org.eclipse.jgit.transport.{ReceiveCommand, ReceivePack}

import scala.util.Using

trait RepositoryCommitFileService {
  self: AccountService & ActivityService & IssuesService & PullRequestService & WebHookPullRequestService &
    RepositoryService =>

  /**
   * Create multiple files by callback function.
   * Returns commitId.
   */
  def commitFiles(
    repository: RepositoryService.RepositoryInfo,
    branch: String,
    message: String,
    loginAccount: Account,
    settings: SystemSettings
  )(
    f: (Git, ObjectId, DirCacheBuilder, ObjectInserter) => Unit
  )(implicit s: Session, c: JsonFormat.Context): Either[String, ObjectId] = {
    _createFiles(repository, branch, message, loginAccount, loginAccount.fullName, loginAccount.mailAddress, settings)(
      f
    ).map(_._1)
  }

  /**
   * Create a file from string content.
   * Returns commitId + blobId.
   */
  def commitFile(
    repository: RepositoryService.RepositoryInfo,
    branch: String,
    path: String,
    newFileName: Option[String],
    oldFileName: Option[String],
    content: String,
    charset: String,
    message: String,
    commit: String,
    loginAccount: Account,
    settings: SystemSettings
  )(implicit s: Session, c: JsonFormat.Context): Either[String, (ObjectId, Option[ObjectId])] = {
    commitFile(
      repository,
      branch,
      path,
      newFileName,
      oldFileName,
      if (content.nonEmpty) { content.getBytes(charset) }
      else { Array.emptyByteArray },
      message,
      commit,
      loginAccount,
      loginAccount.fullName,
      loginAccount.mailAddress,
      settings
    )
  }

  /**
   * Create a file from byte array content.
   * Returns commitId + blobId.
   */
  def commitFile(
    repository: RepositoryService.RepositoryInfo,
    branch: String,
    path: String,
    newFileName: Option[String],
    oldFileName: Option[String],
    content: Array[Byte],
    message: String,
    commit: String,
    pusherAccount: Account,
    committerName: String,
    committerMailAddress: String,
    settings: SystemSettings
  )(implicit s: Session, c: JsonFormat.Context): Either[String, (ObjectId, Option[ObjectId])] = {

    val newPath = newFileName.map { newFileName =>
      if (path.length == 0) newFileName else s"${path}/${newFileName}"
    }
    val oldPath = oldFileName.map { oldFileName =>
      if (path.length == 0) oldFileName else s"${path}/${oldFileName}"
    }

    _createFiles(repository, branch, message, pusherAccount, committerName, committerMailAddress, settings) {
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
            .map { bits =>
              FileMode.fromBits(bits)
            }
            .getOrElse(FileMode.REGULAR_FILE)

          val objectId = newPath.map { newPath =>
            val objectId = inserter.insert(Constants.OBJ_BLOB, content)
            builder.add(JGitUtil.createDirCacheEntry(newPath, permission, objectId))
            objectId
          }
          builder.finish()
          objectId
        } else None
    }
  }

  private def _createFiles[R](
    repository: RepositoryService.RepositoryInfo,
    branch: String,
    message: String,
    pusherAccount: Account,
    committerName: String,
    committerMailAddress: String,
    settings: SystemSettings
  )(
    f: (Git, ObjectId, DirCacheBuilder, ObjectInserter) => R
  )(implicit s: Session, c: JsonFormat.Context): Either[String, (ObjectId, R)] = {

    LockUtil.lock(s"${repository.owner}/${repository.name}") {
      Using.resource(Git.open(getRepositoryDir(repository.owner, repository.name))) { git =>
        val builder = DirCache.newInCore.builder()
        val inserter = git.getRepository.newObjectInserter()
        val headName = s"refs/heads/${branch}"
        val headTip = git.getRepository.resolve(headName)

        val result = f(git, headTip, builder, inserter)

        val commitId = JGitUtil.createNewCommit(
          git,
          inserter,
          headTip,
          builder.getDirCache.writeTree(inserter),
          headName,
          committerName,
          committerMailAddress,
          message
        )

        inserter.flush()
        inserter.close()

        val receivePack = new ReceivePack(git.getRepository)
        val receiveCommand = new ReceiveCommand(headTip, commitId, headName)

        // call pre-commit hook
        val error = PluginRegistry().getReceiveHooks.flatMap { hook =>
          hook.preReceive(repository.owner, repository.name, receivePack, receiveCommand, pusherAccount.userName, false)
        }.headOption

        error match {
          case Some(error) =>
            // commit is rejected
            val refUpdate = git.getRepository.updateRef(headName)
            refUpdate.setNewObjectId(headTip)
            refUpdate.setForceUpdate(true)
            refUpdate.update()
            Left(error)

          case None =>
            // update refs
            val refUpdate = git.getRepository.updateRef(headName)
            refUpdate.setNewObjectId(commitId)
            refUpdate.setForceUpdate(false)
            refUpdate.setRefLogIdent(new PersonIdent(committerName, committerMailAddress))
            refUpdate.update()

            // update pull request
            updatePullRequests(repository.owner, repository.name, branch, pusherAccount, "synchronize", settings)

            // record activity
            updateLastActivityDate(repository.owner, repository.name)
            val commitInfo = new CommitInfo(JGitUtil.getRevCommitFromId(git, commitId))
            val pushInfo = PushInfo(repository.owner, repository.name, pusherAccount.userName, branch, List(commitInfo))
            recordActivity(pushInfo)

            // create issue comment by commit message
            createIssueComment(repository.owner, repository.name, commitInfo)

            // close issue by commit message
            if (branch == repository.repository.defaultBranch) {
              closeIssuesFromMessage(message, committerName, repository.owner, repository.name).foreach { issueId =>
                getIssue(repository.owner, repository.name, issueId.toString).foreach { issue =>
                  callIssuesWebHook("closed", repository, issue, pusherAccount, settings)
                  val closeIssueInfo = CloseIssueInfo(
                    repository.owner,
                    repository.name,
                    pusherAccount.userName,
                    issue.issueId,
                    issue.title
                  )
                  recordActivity(closeIssueInfo)
                  PluginRegistry().getIssueHooks
                    .foreach(_.closedByCommitComment(issue, repository, message, pusherAccount))
                }
              }
            }

            // call post-commit hook
            PluginRegistry().getReceiveHooks.foreach { hook =>
              hook.postReceive(repository.owner, repository.name, receivePack, receiveCommand, committerName, false)
            }

            val commit = new JGitUtil.CommitInfo(JGitUtil.getRevCommitFromId(git, commitId))
            callWebHookOf(repository.owner, repository.name, WebHook.Push, settings) {
              getAccountByUserName(repository.owner).map { ownerAccount =>
                WebHookPushPayload(
                  git,
                  pusherAccount,
                  headName,
                  repository,
                  List(commit),
                  ownerAccount,
                  oldId = headTip,
                  newId = commitId
                )
              }
            }
            Right((commitId, result))
        }
      }
    }
  }

}

object RepositoryCommitFileService {
  case class CommitFile(id: String, name: String)
}
