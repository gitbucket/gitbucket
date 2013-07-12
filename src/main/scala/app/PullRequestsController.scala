package app

import util.{FileUtil, JGitUtil, ReferrerAuthenticator}
import util.Directory._
import service._
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import util.JGitUtil.{DiffInfo, CommitInfo}
import scala.collection.mutable.ArrayBuffer

class PullRequestsController extends PullRequestsControllerBase
  with RepositoryService with AccountService with ReferrerAuthenticator

trait PullRequestsControllerBase extends ControllerBase {
  self: ReferrerAuthenticator =>

  get("/:owner/:repository/pulls")(referrersOnly { repository =>
    pulls.html.list(repository)
  })

  // TODO Replace correct authenticator
  get("/:owner/:repository/pulls/compare/*:*...*")(referrersOnly { repository =>
    if(repository.repository.originUserName.isEmpty || repository.repository.originRepositoryName.isEmpty){
      NotFound // TODO BadRequest?
    } else {
      val userName       = params("owner")
      val repositoryName = params("repository")
      val Seq(origin, originId, forkedId) = multiParams("splat")

      JGitUtil.withGit(getRepositoryDir(userName, repositoryName)){ newGit =>
        JGitUtil.withGit(getRepositoryDir(origin, repository.repository.originRepositoryName.get)){ oldGit =>
          val oldReader = oldGit.getRepository.newObjectReader
          val oldTreeIter = new CanonicalTreeParser
          oldTreeIter.reset(oldReader, oldGit.getRepository.resolve("master^{tree}"))

          val newReader = newGit.getRepository.newObjectReader
          val newTreeIter = new CanonicalTreeParser
          newTreeIter.reset(newReader, newGit.getRepository.resolve("master^{tree}"))

          import scala.collection.JavaConverters._
          import util.Implicits._

          val oldId = oldGit.getRepository.resolve(originId)
          val newId = newGit.getRepository.resolve(forkedId)
          val i = newGit.log.addRange(oldId, newId).call.iterator

          val commits = new ArrayBuffer[CommitInfo]
          while(i.hasNext){
            val revCommit = i.next
            commits += new CommitInfo(revCommit)
          }

          val diffs = newGit.diff.setOldTree(oldTreeIter).setNewTree(newTreeIter).call.asScala.map { diff =>
            if(FileUtil.isImage(diff.getOldPath) || FileUtil.isImage(diff.getNewPath)){
              DiffInfo(diff.getChangeType, diff.getOldPath, diff.getNewPath, None, None)
            } else {
              DiffInfo(diff.getChangeType, diff.getOldPath, diff.getNewPath,
                JGitUtil.getContent(oldGit, diff.getOldId.toObjectId, false).filter(FileUtil.isText).map(new String(_, "UTF-8")),
                JGitUtil.getContent(newGit, diff.getNewId.toObjectId, false).filter(FileUtil.isText).map(new String(_, "UTF-8")))
            }
          }

          pulls.html.compare(commits.toList.splitWith{ (commit1, commit2) =>
            view.helpers.date(commit1.time) == view.helpers.date(commit2.time)
          }, diffs.toList, origin, originId, forkedId, newId.getName, repository)
        }
      }
    }
  })
}
