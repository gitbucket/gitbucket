package gitbucket.core.controller.api
import gitbucket.core.api.{ApiBranchCommit, ApiBranchForHeadCommit, ApiCommits, JsonFormat}
import gitbucket.core.controller.ControllerBase
import gitbucket.core.model.Account
import gitbucket.core.service.{AccountService, CommitsService, ProtectedBranchService}
import gitbucket.core.util.Directory.getRepositoryDir
import gitbucket.core.util.Implicits.*
import gitbucket.core.util.JGitUtil.{CommitInfo, getBranchesNoMergeInfo, getBranchesOfCommit}
import gitbucket.core.util.{JGitUtil, ReferrerAuthenticator, RepositoryName}
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.revwalk.filter.*
import org.eclipse.jgit.treewalk.filter.{AndTreeFilter, PathFilterGroup, TreeFilter}

import java.time.format.DateTimeFormatter.*
import java.time.{LocalDateTime, ZoneOffset}
import java.util.Date
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.*
import scala.math.min
import scala.util.Using

trait ApiRepositoryCommitControllerBase extends ControllerBase {
  self: AccountService & CommitsService & ProtectedBranchService & ReferrerAuthenticator =>
  /*
   * i. List commits on a repository
   * https://developer.github.com/v3/repos/commits/#list-commits-on-a-repository
   */
  get("/api/v3/repos/:owner/:repository/commits")(referrersOnly { repository =>
    val owner = repository.owner
    val name = repository.name
    val sha = params.get("sha").filter(_.nonEmpty).getOrElse("HEAD")
    val page = params.get("page").filter(_.nonEmpty).getOrElse("1").toInt
    val per_page = min(params.get("per_page").filter(_.nonEmpty).getOrElse("30").toInt, 100)
    val author = params.get("author").filter(_.nonEmpty)
    val path = params.get("path").filter(_.nonEmpty)
    val since = params.get("since").filter(_.nonEmpty)
    val until = params.get("until").filter(_.nonEmpty)
    Using.resource(Git.open(getRepositoryDir(owner, name))) { git =>
      val repo = git.getRepository
      Using.resource(new RevWalk(repo)) { revWalk =>
        val objectId = repo.resolve(sha)
        revWalk.markStart(revWalk.parseCommit(objectId))
        if (path.nonEmpty) {
          revWalk.setTreeFilter(
            AndTreeFilter.create(PathFilterGroup.createFromStrings(path.get), TreeFilter.ANY_DIFF)
          )
        }
        val revfilters = new ListBuffer[(RevFilter)]()
        if (author.nonEmpty) {
          revfilters += AuthorRevFilter.create(author.get)
        }
        if (since.nonEmpty) {
          revfilters += CommitTimeRevFilter.after(
            Date.from(LocalDateTime.parse(since.get, ISO_DATE_TIME).toInstant(ZoneOffset.UTC))
          )
        }
        if (until.nonEmpty) {
          revfilters += CommitTimeRevFilter.before(
            Date.from(LocalDateTime.parse(until.get, ISO_DATE_TIME).toInstant(ZoneOffset.UTC))
          )
        }
        if (page > 1) {
          revfilters += SkipRevFilter.create(page * per_page - 2)
        }
        revfilters += MaxCountRevFilter.create(per_page);
        revWalk.setRevFilter(
          if (revfilters.size > 1) {
            AndRevFilter.create(revfilters.toArray)
          } else {
            revfilters(0)
          }
        )
        JsonFormat(revWalk.asScala.map { commit =>
          val commitInfo = new CommitInfo(commit)
          ApiCommits(
            repositoryName = RepositoryName(repository),
            commitInfo = commitInfo,
            diffs = JGitUtil.getDiffs(git, commitInfo.parents.headOption, commitInfo.id, false, true),
            author = getAccount(commitInfo.authorName, commitInfo.authorEmailAddress),
            committer = getAccount(commitInfo.committerName, commitInfo.committerEmailAddress),
            commentCount = getCommitComment(repository.owner, repository.name, commitInfo.id).size
          )
        })
      }
    }
  })

  /*
   * ii. Get a single commit
   * https://developer.github.com/v3/repos/commits/#get-a-single-commit
   */
  get("/api/v3/repos/:owner/:repository/commits/:sha")(referrersOnly { repository =>
    val owner = repository.owner
    val name = repository.name
    val sha = params("sha")

    Using.resource(Git.open(getRepositoryDir(owner, name))) { git =>
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

  /*
   * vi. List branches for HEAD commit
   * https://docs.github.com/en/rest/reference/repos#list-branches-for-head-commit
   */
  get("/api/v3/repos/:owner/:repository/commits/:sha/branches-where-head")(referrersOnly { repository =>
    val sha = params("sha")
    Using.resource(Git.open(getRepositoryDir(repository.owner, repository.name))) { git =>
      val apiBranchForCommits = for {
        branch <- getBranchesOfCommit(git, sha)
        br <- getBranchesNoMergeInfo(git).find(_.name == branch)
      } yield {
        val protection = getProtectedBranchInfo(repository.owner, repository.name, branch)
        ApiBranchForHeadCommit(branch, ApiBranchCommit(br.commitId), protection.enabled)
      }
      JsonFormat(apiBranchForCommits)
    }
  })
}
