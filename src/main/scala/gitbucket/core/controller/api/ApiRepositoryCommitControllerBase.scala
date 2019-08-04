package gitbucket.core.controller.api
import gitbucket.core.api.{ApiCommits, JsonFormat}
import gitbucket.core.controller.ControllerBase
import gitbucket.core.model.Account
import gitbucket.core.service.{AccountService, CommitsService}
import gitbucket.core.util.Directory.getRepositoryDir
import gitbucket.core.util.Implicits._
import gitbucket.core.util.JGitUtil.CommitInfo
import gitbucket.core.util.{JGitUtil, ReferrerAuthenticator, RepositoryName}
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.revwalk.RevWalk
import scala.util.Using

trait ApiRepositoryCommitControllerBase extends ControllerBase {
  self: AccountService with CommitsService with ReferrerAuthenticator =>
  /*
   * i. List commits on a repository
   * https://developer.github.com/v3/repos/commits/#list-commits-on-a-repository
   */

  /*
   * ii. Get a single commit
   * https://developer.github.com/v3/repos/commits/#get-a-single-commit
   */
  get("/api/v3/repos/:owner/:repository/commits/:sha")(referrersOnly { repository =>
    val owner = repository.owner
    val name = repository.name
    val sha = params("sha")

    Using.resource(Git.open(getRepositoryDir(owner, name))) {
      git =>
        val repo = git.getRepository
        val objectId = repo.resolve(sha)
        val commitInfo = Using.resource(new RevWalk(repo)) { revWalk =>
          new CommitInfo(revWalk.parseCommit(objectId))
        }

        JsonFormat(
          ApiCommits(
            repositoryName = RepositoryName(repository),
            commitInfo = commitInfo,
            diffs = JGitUtil.getDiffs(git, commitInfo.parents.headOption, commitInfo.id, false, true),
            author = getAccount(commitInfo.authorName, commitInfo.authorEmailAddress),
            committer = getAccount(commitInfo.committerName, commitInfo.committerEmailAddress),
            commentCount = getCommitComment(repository.owner, repository.name, sha).size
          )
        )
    }
  })

  private def getAccount(userName: String, email: String): Account = {
    getAccountByMailAddress(email).getOrElse {
      Account(
        userName = userName,
        fullName = userName,
        mailAddress = email,
        password = "xxx",
        isAdmin = false,
        url = None,
        registeredDate = new java.util.Date(),
        updatedDate = new java.util.Date(),
        lastLoginDate = None,
        image = None,
        isGroupAccount = false,
        isRemoved = true,
        description = None
      )
    }
  }

  /*
   * iii. Get the SHA-1 of a commit reference
   * https://developer.github.com/v3/repos/commits/#get-the-sha-1-of-a-commit-reference
   */

  /*
   * iv. Compare two commits
   * https://developer.github.com/v3/repos/commits/#compare-two-commits
   */

  /*
 * v. Commit signature verification
 * https://developer.github.com/v3/repos/commits/#commit-signature-verification
 */
}
