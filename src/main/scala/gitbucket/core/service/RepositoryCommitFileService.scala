package gitbucket.core.service
import gitbucket.core.api.JsonFormat
import gitbucket.core.model.{Account, WebHook}
import gitbucket.core.model.Profile._
import gitbucket.core.model.Profile.profile.blockingApi._
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
  self: AccountService with ActivityService with IssuesService with PullRequestService with WebHookPullRequestService =>
  import RepositoryCommitFileService._

  def commitFiles(
    repository: RepositoryService.RepositoryInfo,
    files: Seq[CommitFile],
    branch: String,
    path: String,
    message: String,
    loginAccount: Account,
    settings: SystemSettings
  )(
    f: (Git, ObjectId, DirCacheBuilder, ObjectInserter) => Unit
  )(implicit s: Session, c: JsonFormat.Context) = {
    // prepend path to the filename
    _commitFile(repository, branch, message, loginAccount, loginAccount.fullName, loginAccount.mailAddress, settings)(f)
  }

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
  )(implicit s: Session, c: JsonFormat.Context): ObjectId = {
    commitFile(
      repository,
      branch,
      path,
      newFileName,
      oldFileName,
      if (content.nonEmpty) { content.getBytes(charset) } else { Array.emptyByteArray },
      message,
      commit,
      loginAccount,
      loginAccount.fullName,
      loginAccount.mailAddress,
      settings
    )
  }

  def commitFile(
    repository: RepositoryService.RepositoryInfo,
    branch: String,
    path: String,
    newFileName: Option[String],
    oldFileName: Option[String],
    content: Array[Byte],
    message: String,
    commit: String,
    loginAccount: Account,
    fullName: String,
    mailAddress: String,
    settings: SystemSettings
  )(implicit s: Session, c: JsonFormat.Context): ObjectId = {

    val newPath = newFileName.map { newFileName =>
      if (path.length == 0) newFileName else s"${path}/${newFileName}"
    }
    val oldPath = oldFileName.map { oldFileName =>
      if (path.length == 0) oldFileName else s"${path}/${oldFileName}"
    }

    _commitFile(repository, branch, message, loginAccount, fullName, mailAddress, settings) {
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
            } getOrElse FileMode.REGULAR_FILE, inserter.insert(Constants.OBJ_BLOB, content)))
          }
          builder.finish()
        }
    }
  }

  private def _commitFile(
    repository: RepositoryService.RepositoryInfo,
    branch: String,
    message: String,
    loginAccount: Account,
    committerName: String,
    committerMailAddress: String,
    settings: SystemSettings
  )(
    f: (Git, ObjectId, DirCacheBuilder, ObjectInserter) => Unit
  )(implicit s: Session, c: JsonFormat.Context): ObjectId = {

    LockUtil.lock(s"${repository.owner}/${repository.name}") {
      Using.resource(Git.open(getRepositoryDir(repository.owner, repository.name))) { git =>
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
          committerName,
          committerMailAddress,
          message
        )

        inserter.flush()
        inserter.close()

        val receivePack = new ReceivePack(git.getRepository)
        val receiveCommand = new ReceiveCommand(headTip, commitId, headName)

        // call post commit hook
        val error = PluginRegistry().getReceiveHooks.flatMap { hook =>
          hook.preReceive(repository.owner, repository.name, receivePack, receiveCommand, committerName)
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
            refUpdate.setRefLogIdent(new PersonIdent(committerName, committerMailAddress))
            refUpdate.update()

            // update pull request
            updatePullRequests(repository.owner, repository.name, branch, loginAccount, "synchronize", settings)

            // record activity
            val commitInfo = new CommitInfo(JGitUtil.getRevCommitFromId(git, commitId))
            recordPushActivity(repository.owner, repository.name, loginAccount.userName, branch, List(commitInfo))

            // create issue comment by commit message
            createIssueComment(repository.owner, repository.name, commitInfo)

            // close issue by commit message
            if (branch == repository.repository.defaultBranch) {
              closeIssuesFromMessage(message, committerName, repository.owner, repository.name).foreach { issueId =>
                getIssue(repository.owner, repository.name, issueId.toString).foreach { issue =>
                  callIssuesWebHook("closed", repository, issue, loginAccount, settings)
                  PluginRegistry().getIssueHooks
                    .foreach(_.closedByCommitComment(issue, repository, message, loginAccount))
                }
              }
            }

            // call post commit hook
            PluginRegistry().getReceiveHooks.foreach { hook =>
              hook.postReceive(repository.owner, repository.name, receivePack, receiveCommand, committerName)
            }

            val commit = new JGitUtil.CommitInfo(JGitUtil.getRevCommitFromId(git, commitId))
            callWebHookOf(repository.owner, repository.name, WebHook.Push, settings) {
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
        commitId
      }
    }
  }

}

object RepositoryCommitFileService {
  case class CommitFile(id: String, name: String)
}
