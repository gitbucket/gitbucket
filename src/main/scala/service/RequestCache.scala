package service

import model._
import servlet.Database
import service.SystemSettingsService.SystemSettings

/**
 * This service is used for a view helper mainly.
 *
 * It may be called many times in one request, so each method stores
 * its result into the cache which available during a request.
 */
trait RequestCache {

  def getSystemSettings()(implicit context: app.Context): SystemSettings =
    context.cache("system_settings"){
      new SystemSettingsService {}.loadSystemSettings()
    }

  def getIssue(userName: String, repositoryName: String, issueId: String)(implicit context: app.Context): Option[Issue] = {
    context.cache(s"issue.${userName}/${repositoryName}#${issueId}"){
      new IssuesService with Profile {
        val profile = Database.driver(context.request.getServletContext)
      }.getIssue(userName, repositoryName, issueId)
    }
  }

  def getAccountByUserName(userName: String)(implicit context: app.Context): Option[Account] = {
    context.cache(s"account.${userName}"){
      new AccountService with Profile {
        val profile = Database.driver(context.request.getServletContext)
      }.getAccountByUserName(userName)
    }
  }

  def getAccountByMailAddress(mailAddress: String)(implicit context: app.Context): Option[Account] = {
    context.cache(s"account.${mailAddress}"){
      new AccountService with Profile {
        val profile = Database.driver(context.request.getServletContext)
      }.getAccountByMailAddress(mailAddress)
    }
  }
}

