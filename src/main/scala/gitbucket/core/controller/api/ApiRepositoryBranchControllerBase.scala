package gitbucket.core.controller.api
import gitbucket.core.api._
import gitbucket.core.controller.ControllerBase
import gitbucket.core.service.{AccountService, ProtectedBranchService, RepositoryService}
import gitbucket.core.util._
import gitbucket.core.util.Directory._
import gitbucket.core.util.Implicits._
import gitbucket.core.util.JGitUtil.getBranches
import org.eclipse.jgit.api.Git
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
   * https://developer.github.com/v3/repos/branches/#list-branches
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
   * ii. Get branch
   * https://developer.github.com/v3/repos/branches/#get-branch
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
   * https://developer.github.com/v3/repos/branches/#get-branch-protection
   */

  /*
   * iv. Update branch protection
   * https://developer.github.com/v3/repos/branches/#update-branch-protection
   */

  /*
   * v. Remove branch protection
   * https://developer.github.com/v3/repos/branches/#remove-branch-protection
   */

  /*
   * vi. Get required status checks of protected branch
   * https://developer.github.com/v3/repos/branches/#get-required-status-checks-of-protected-branch
   */

  /*
   * vii. Update required status checks of protected branch
   * https://developer.github.com/v3/repos/branches/#update-required-status-checks-of-protected-branch
   */

  /*
   * viii. Remove required status checks of protected branch
   * https://developer.github.com/v3/repos/branches/#remove-required-status-checks-of-protected-branch
   */

  /*
   * ix. List required status checks contexts of protected branch
   * https://developer.github.com/v3/repos/branches/#list-required-status-checks-contexts-of-protected-branch
   */

  /*
   * x. Replace required status checks contexts of protected branch
   * https://developer.github.com/v3/repos/branches/#replace-required-status-checks-contexts-of-protected-branch
   */

  /*
   * xi. Add required status checks contexts of protected branch
   * https://developer.github.com/v3/repos/branches/#remove-required-status-checks-contexts-of-protected-branch
   */

  /*
   * xii. Remove required status checks contexts of protected branch
   * https://developer.github.com/v3/repos/branches/#remove-required-status-checks-contexts-of-protected-branch
   */

  /*
   * xiii. Get pull request review enforcement of protected branch
   * https://developer.github.com/v3/repos/branches/#get-pull-request-review-enforcement-of-protected-branch
   */

  /*
   * xiv. Update pull request review enforcement of protected branch
   * https://developer.github.com/v3/repos/branches/#update-pull-request-review-enforcement-of-protected-branch
   */

  /*
   * xv. Remove pull request review enforcement of protected branch
   * https://developer.github.com/v3/repos/branches/#remove-pull-request-review-enforcement-of-protected-branch
   */

  /*
   * xvi. Get required signatures of protected branch
   * https://developer.github.com/v3/repos/branches/#get-required-signatures-of-protected-branch
   */

  /*
   * xvii. Add required signatures of protected branch
   * https://developer.github.com/v3/repos/branches/#add-required-signatures-of-protected-branch
   */

  /*
   * xviii. Remove required signatures of protected branch
   * https://developer.github.com/v3/repos/branches/#remove-required-signatures-of-protected-branch
   */

  /*
   * xix. Get admin enforcement of protected branch
   * https://developer.github.com/v3/repos/branches/#get-admin-enforcement-of-protected-branch
   */

  /*
   * xx. Add admin enforcement of protected branch
   * https://developer.github.com/v3/repos/branches/#add-admin-enforcement-of-protected-branch
   */

  /*
   * xxi. Remove admin enforcement of protected branch
   * https://developer.github.com/v3/repos/branches/#remove-admin-enforcement-of-protected-branch
   */

  /*
   * xxii. Get restrictions of protected branch
   * https://developer.github.com/v3/repos/branches/#get-restrictions-of-protected-branch
   */

  /*
   * xxiii. Remove restrictions of protected branch
   * https://developer.github.com/v3/repos/branches/#remove-restrictions-of-protected-branch
   */

  /*
   * xxiv. List team restrictions of protected branch
   * https://developer.github.com/v3/repos/branches/#list-team-restrictions-of-protected-branch
   */

  /*
   * xxv. Replace team restrictions of protected branch
   * https://developer.github.com/v3/repos/branches/#replace-team-restrictions-of-protected-branch
   */

  /*
   * xxvi. Add team restrictions of protected branch
   * https://developer.github.com/v3/repos/branches/#add-team-restrictions-of-protected-branch
   */

  /*
   * xxvii. Remove team restrictions of protected branch
   * https://developer.github.com/v3/repos/branches/#remove-team-restrictions-of-protected-branch
   */

  /*
   * xxviii. List user restrictions of protected branch
   * https://developer.github.com/v3/repos/branches/#list-user-restrictions-of-protected-branch
   */

  /*
   * xxix. Replace user restrictions of protected branch
   * https://developer.github.com/v3/repos/branches/#replace-user-restrictions-of-protected-branch
   */

  /*
   * xxx. Add user restrictions of protected branch
   * https://developer.github.com/v3/repos/branches/#add-user-restrictions-of-protected-branch
   */

  /*
   * xxxi. Remove user restrictions of protected branch
   * https://developer.github.com/v3/repos/branches/#remove-user-restrictions-of-protected-branch
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
