package gitbucket.core.controller.api
import gitbucket.core.api._
import gitbucket.core.controller.ControllerBase
import gitbucket.core.service.{AccountService, ProtectedBranchService, RepositoryService}
import gitbucket.core.util._
import gitbucket.core.util.Directory._
import gitbucket.core.util.Implicits._
import gitbucket.core.util.JGitUtil.getBranches
import org.eclipse.jgit.api.Git
import org.scalatra.NoContent

import scala.util.Using

trait ApiRepositoryBranchControllerBase extends ControllerBase {
  self: RepositoryService
    with AccountService
    with OwnerAuthenticator
    with UsersAuthenticator
    with GroupManagerAuthenticator
    with ProtectedBranchService
    with ReferrerAuthenticator
    with ReadableUsersAuthenticator
    with WritableUsersAuthenticator =>

  /**
   * i. List branches
   * https://docs.github.com/en/rest/reference/repos#list-branches
   */
  get("/api/v3/repos/:owner/:repository/branches")(referrersOnly { repository =>
    Using.resource(Git.open(getRepositoryDir(repository.owner, repository.name))) { git =>
      JsonFormat(
        JGitUtil
          .getBranches(
            git = git,
            defaultBranch = repository.repository.defaultBranch,
            origin = repository.repository.originUserName.isEmpty
          )
          .map { br =>
            ApiBranchForList(br.name, ApiBranchCommit(br.commitId))
          }
      )
    }
  })

  /**
   * ii. Get a branch
   * https://docs.github.com/en/rest/reference/repos#get-a-branch
   */
  get("/api/v3/repos/:owner/:repository/branches/*")(referrersOnly { repository =>
    Using.resource(Git.open(getRepositoryDir(repository.owner, repository.name))) {
      git =>
        (for {
          branch <- params.get("splat") if repository.branchList.contains(branch)
          br <- getBranches(
            git,
            repository.repository.defaultBranch,
            repository.repository.originUserName.isEmpty
          ).find(_.name == branch)
        } yield {
          val protection = getProtectedBranchInfo(repository.owner, repository.name, branch)
          JsonFormat(
            ApiBranch(branch, ApiBranchCommit(br.commitId), ApiBranchProtection(protection))(RepositoryName(repository))
          )
        }) getOrElse NotFound()
    }
  })

  /*
   * iii. Get branch protection
   * https://docs.github.com/en/rest/reference/repos#get-branch-protection
   */
  get("/api/v3/repos/:owner/:repository/branches/:branch/protection")(referrersOnly { repository =>
    val branch = params("branch")
    if (repository.branchList.contains(branch)) {
      val protection = getProtectedBranchInfo(repository.owner, repository.name, branch)
      JsonFormat(
        ApiBranchProtection(protection)
      )
    } else { NotFound() }
  })

  /*
   * iv. Update branch protection
   * https://docs.github.com/en/rest/reference/repos#update-branch-protection
   */

  /*
   * v. Delete branch protection
   * https://docs.github.com/en/rest/reference/repos#delete-branch-protection
   */
  delete("/api/v3/repos/:owner/:repository/branches/:branch/protection")(writableUsersOnly { repository =>
    val branch = params("branch")
    if (repository.branchList.contains(branch)) {
      val protection = getProtectedBranchInfo(repository.owner, repository.name, branch)
      if (protection.enabled) {
        disableBranchProtection(repository.owner, repository.name, branch)
        NoContent()
      } else NotFound()
    } else NotFound()
  })

  /*
   * vi. Get admin branch protection
   * https://docs.github.com/en/rest/reference/repos#get-admin-branch-protection
   */

  /*
   * vii. Set admin branch protection
   * https://docs.github.com/en/rest/reference/repos#set-admin-branch-protection
   */

  /*
   * viii. Delete admin branch protection
   * https://docs.github.com/en/rest/reference/repos#delete-admin-branch-protection
   */

  /*
   * ix. Get pull request review protection
   * https://docs.github.com/en/rest/reference/repos#get-pull-request-review-protection
   */

  /*
   * x. Update pull request review protection
   * https://docs.github.com/en/rest/reference/repos#update-pull-request-review-protection
   */

  /*
   * xi. Delete pull request review protection
   * https://docs.github.com/en/rest/reference/repos#delete-pull-request-review-protection
   */

  /*
   * xii. Get commit signature protection
   * https://docs.github.com/en/rest/reference/repos#get-commit-signature-protection
   */

  /*
   * xiii. Create commit signature protection
   * https://docs.github.com/en/rest/reference/repos#create-commit-signature-protection
   */

  /*
   * xiv. Delete commit signature protection
   * https://docs.github.com/en/rest/reference/repos#delete-commit-signature-protection
   */

  /*
   * xv. Get status checks protection
   * https://docs.github.com/en/rest/reference/repos#get-status-checks-protection
   */
  get("/api/v3/repos/:owner/:repository/branches/:branch/protection/required_status_checks")(referrersOnly {
    repository =>
      val branch = params("branch")
      if (repository.branchList.contains(branch)) {
        val protection = getProtectedBranchInfo(repository.owner, repository.name, branch)
        JsonFormat(
          ApiBranchProtection(protection).required_status_checks
        )
      } else { NotFound() }
  })

  /*
   * xvi. Update status check protection
   * https://docs.github.com/en/rest/reference/repos#update-status-check-protection
   */

  /*
   * xvii. Remove status check protection
   * https://docs.github.com/en/rest/reference/repos#remove-status-check-protection
   */

  /*
   * xviii. Get all status check contexts
   * https://docs.github.com/en/free-pro-team@latest/rest/reference/repos#get-all-status-check-contexts
   */
  get("/api/v3/repos/:owner/:repository/branches/:branch/protection/required_status_checks/contexts")(referrersOnly {
    repository =>
      val branch = params("branch")
      if (repository.branchList.contains(branch)) {
        val protection = getProtectedBranchInfo(repository.owner, repository.name, branch)
        if (protection.enabled) {
          protection.contexts.toList
        } else NotFound()
      } else NotFound()
  })

  /*
   * xix. Add status check contexts
   * https://docs.github.com/en/rest/reference/repos#add-status-check-contexts
   */

  /*
   * xx. Set status check contexts
   * https://docs.github.com/en/rest/reference/repos#set-status-check-contexts
   */

  /*
   * xxi. Remove status check contexts
   * https://docs.github.com/en/rest/reference/repos#remove-status-check-contexts
   */

  /*
   * xxii. Get access restrictions
   * https://docs.github.com/en/rest/reference/repos#get-access-restrictions
   */

  /*
   * xxiii. Delete access restrictions
   * https://docs.github.com/en/rest/reference/repos#delete-access-restrictions
   */

  /*
   * xxiv. Get apps with access to the protected branch
   * https://docs.github.com/en/rest/reference/repos#get-apps-with-access-to-the-protected-branch
   */

  /*
   * xxv. Add app access restrictions
   * https://docs.github.com/en/rest/reference/repos#add-app-access-restrictions
   */

  /*
   * xxvi. Set app access restrictions
   * https://docs.github.com/en/rest/reference/repos#set-app-access-restrictions
   */

  /*
   * xxvii. Remove app access restrictions
   * https://docs.github.com/en/rest/reference/repos#remove-app-access-restrictions
   */

  /*
   * xxviii. Get teams with access to the protected branch
   * https://docs.github.com/en/rest/reference/repos#get-teams-with-access-to-the-protected-branch
   */

  /*
   * xxix. Add team access restrictions
   * https://docs.github.com/en/rest/reference/repos#add-team-access-restrictions
   */

  /*
   * xxx. Set team access restrictions
   * https://docs.github.com/en/rest/reference/repos#set-team-access-restrictions
   */

  /*
   * xxxi. Remove team access restrictions
   * https://docs.github.com/en/rest/reference/repos#remove-team-access-restrictions
   */

  /*
   * xxxii. Get users with access to the protected branch
   * https://docs.github.com/en/rest/reference/repos#get-users-with-access-to-the-protected-branch
   */

  /*
   * xxxiii. Add user access restrictions
   * https://docs.github.com/en/rest/reference/repos#add-user-access-restrictions
   */

  /*
   * xxxiv. Set user access restrictions
   * https://docs.github.com/en/rest/reference/repos#set-user-access-restrictions
   */

  /*
   * xxxv. Remove user access restrictions
   * https://docs.github.com/en/rest/reference/repos#remove-user-access-restrictions
   */

  /**
   * Enabling and disabling branch protection: deprecated?
   * https://developer.github.com/v3/repos/#enabling-and-disabling-branch-protection
   */
  patch("/api/v3/repos/:owner/:repository/branches/*")(ownerOnly { repository =>
    import gitbucket.core.api._
    Using.resource(Git.open(getRepositoryDir(repository.owner, repository.name))) {
      git =>
        (for {
          branch <- params.get("splat") if repository.branchList.contains(branch)
          protection <- extractFromJsonBody[ApiBranchProtection.EnablingAndDisabling].map(_.protection)
          br <- getBranches(
            git,
            repository.repository.defaultBranch,
            repository.repository.originUserName.isEmpty
          ).find(_.name == branch)
        } yield {
          if (protection.enabled) {
            enableBranchProtection(
              repository.owner,
              repository.name,
              branch,
              protection.status.enforcement_level == ApiBranchProtection.Everyone,
              protection.status.contexts
            )
          } else {
            disableBranchProtection(repository.owner, repository.name, branch)
          }
          JsonFormat(ApiBranch(branch, ApiBranchCommit(br.commitId), protection)(RepositoryName(repository)))
        }) getOrElse NotFound()
    }
  })
}
