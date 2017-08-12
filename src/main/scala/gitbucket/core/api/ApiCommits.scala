package gitbucket.core.api

import gitbucket.core.model.Account
import gitbucket.core.util.JGitUtil.{CommitInfo, DiffInfo}
import gitbucket.core.util.RepositoryName
import org.eclipse.jgit.diff.DiffEntry.ChangeType
import ApiCommits._
import difflib.{Delta, DiffUtils}

import scala.collection.JavaConverters._

case class ApiCommits(
  url: ApiPath,
  sha: String,
  html_url: ApiPath,
  comment_url: ApiPath,
  commit: Commit,
  author: ApiUser,
  committer: ApiUser,
  parents: Seq[Tree],
  stats: Stats,
  files: Seq[File]
)

object ApiCommits {
  case class Commit(
    url: ApiPath,
    author: ApiPersonIdent,
    committer: ApiPersonIdent,
    message: String,
    comment_count: Int,
    tree: Tree,
    verification: Verification
  )

  case class Tree(
    url: ApiPath,
    tree: String
  )

  case class Verification(
    verified: Boolean,
    reason: String //,
    //  signature: String,
    //  payload: String
  )

  case class Stats(
    additions: Int,
    deletions: Int,
    total: Int
  )

  case class File(
    filename: String,
    additions: Int,
    deletions: Int,
    changes: Int,
    status: String,
    raw_url: ApiPath,
    blob_url: ApiPath,
    patch: String
  )


  def apply(repositoryName: RepositoryName, commitInfo: CommitInfo, diffs: Seq[DiffInfo], author: Account, committer: Account,
            commentCount: Int): ApiCommits = {

    val files = diffs.map { diff =>
      var additions = 0
      var deletions = 0
      var changes = 0

      diff.changeType match {
        case ChangeType.ADD => {
          additions = additions + diff.newContent.getOrElse("").replace("\r\n", "\n").split("\n").size
        }
        case ChangeType.MODIFY => {
          val oldLines = diff.oldContent.getOrElse("").replace("\r\n", "\n").split("\n")
          val newLines = diff.newContent.getOrElse("").replace("\r\n", "\n").split("\n")
          val patch = DiffUtils.diff(oldLines.toList.asJava, newLines.toList.asJava)
          patch.getDeltas.asScala.map { delta =>
            additions = additions + delta.getRevised.getLines.size
            deletions = deletions + delta.getOriginal.getLines.size
          }
        }
        case ChangeType.DELETE => {
          deletions = deletions + diff.oldContent.getOrElse("").replace("\r\n", "\n").split("\n").size
        }
      }

      File(
        filename = if(diff.changeType == ChangeType.DELETE){ diff.oldPath } else { diff.newPath },
        additions = additions,
        deletions = deletions,
        changes = changes,
        status = diff.changeType match {
          case ChangeType.ADD    => "added"
          case ChangeType.MODIFY => "modified"
          case ChangeType.DELETE => "deleted"
          case ChangeType.RENAME => "renamed"
          case ChangeType.COPY   => "copied"
        },
        raw_url = if(diff.changeType == ChangeType.DELETE){
          ApiPath(s"/${repositoryName.fullName}/raw/${commitInfo.parents.head}/${diff.oldPath}")
        } else {
          ApiPath(s"/${repositoryName.fullName}/raw/${commitInfo.id}/${diff.newPath}")
        },
        blob_url = if(diff.changeType == ChangeType.DELETE){
          ApiPath(s"/${repositoryName.fullName}/blob/${commitInfo.parents.head}/${diff.oldPath}")
        } else {
          ApiPath(s"/${repositoryName.fullName}/blob/${commitInfo.id}/${diff.newPath}")
        },
        patch = "" // TODO
      )
    }

    ApiCommits(
      url = ApiPath(s"/api/v3/repos/${repositoryName.fullName}/commits/${commitInfo.id}"),
      sha = commitInfo.id,
      html_url = ApiPath(s"${repositoryName.fullName}/commit/${commitInfo.id}"),
      comment_url = ApiPath(""),
      commit = Commit(
        url = ApiPath(s"/api/v3/repos/${repositoryName.fullName}/commits/${commitInfo.id}"),
        author = ApiPersonIdent.author(commitInfo),
        committer = ApiPersonIdent.committer(commitInfo),
        message = commitInfo.shortMessage,
        comment_count = commentCount,
        tree = Tree(
          url = ApiPath(s"/api/v3/repos/${repositoryName.fullName}/tree/${commitInfo.id}"), // TODO This endpoint has not been implemented yet.
          tree = commitInfo.id
        ),
        verification = Verification( // TODO
          verified = false,
          reason = "" //,
//          signature = "",
//          payload = ""
        )
      ),
      author = ApiUser(author),
      committer = ApiUser(committer),
      parents = commitInfo.parents.map { parent =>
        Tree(
          url = ApiPath(s"/api/v3/repos/${repositoryName.fullName}/tree/${parent}"), // TODO This endpoint has not been implemented yet.
          tree = parent
        )
      },
      stats = Stats(
        additions = files.map(_.additions).sum,
        deletions = files.map(_.deletions).sum,
        total = files.map(_.additions).sum + files.map(_.deletions).sum
      ),
      files = files
    )
  }
}
