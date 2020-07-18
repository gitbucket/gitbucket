package gitbucket.core.service

import gitbucket.core.model.{Account, Issue, Repository, Session}
import gitbucket.core.util.Implicits
import gitbucket.core.controller.Context
import Implicits.request2Session
import gitbucket.core.model.Profile.{Accounts, Repositories}
import gitbucket.core.model.Profile.profile.blockingApi._

/**
 * This service is used for a view helper mainly.
 *
 * It may be called many times in one request, so each method stores
 * its result into the cache which available during a request.
 */
trait RequestCache
    extends SystemSettingsService
    with AccountService
    with IssuesService
    with RepositoryService
    with LabelsService
    with MilestonesService
    with PrioritiesService {

  private implicit def context2Session(implicit context: Context): Session =
    request2Session(context.request)

  def getIssueFromCache(userName: String, repositoryName: String, issueId: String)(
    implicit context: Context
  ): Option[Issue] = {
    context.cache(s"issue.${userName}/${repositoryName}#${issueId}") {
      super.getIssue(userName, repositoryName, issueId)
    }
  }

  def getAccountByUserNameFromCache(userName: String)(implicit context: Context): Option[Account] = {
    context.cache(s"account.${userName}") {
      super.getAccountByUserName(userName)
    }
  }

  def getAccountByMailAddressFromCache(mailAddress: String)(implicit context: Context): Option[Account] = {
    context.cache(s"account.${mailAddress}") {
      super.getAccountByMailAddress(mailAddress)
    }
  }

  def getRepositoryInfoFromCache(userName: String, repositoryName: String)(
    implicit context: Context
  ): Option[Repository] = {
    context.cache(s"repository.${userName}/${repositoryName}") {
      Repositories
        .join(Accounts)
        .on(_.userName === _.userName)
        .filter {
          case (t1, t2) =>
            t1.byRepository(userName, repositoryName) && t2.removed === false.bind
        }
        .map {
          case (t1, t2) => t1
        }
        .firstOption
    }
  }
}
