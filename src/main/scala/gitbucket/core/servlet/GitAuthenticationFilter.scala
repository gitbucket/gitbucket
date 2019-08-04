package gitbucket.core.servlet

import javax.servlet._
import javax.servlet.http._

import gitbucket.core.model.Account
import gitbucket.core.plugin.{GitRepositoryFilter, GitRepositoryRouting, PluginRegistry}
import gitbucket.core.service.SystemSettingsService.SystemSettings
import gitbucket.core.service.{AccessTokenService, AccountService, RepositoryService, SystemSettingsService}
import gitbucket.core.util.Implicits._
import gitbucket.core.util.{AuthUtil, Keys}
import gitbucket.core.model.Profile.profile.blockingApi._
// Imported names have higher precedence than names, defined in other files.
// If Database is not bound by explicit import, then "Database" refers to the Database introduced by the wildcard import above.
import gitbucket.core.servlet.Database

import org.slf4j.LoggerFactory

/**
 * Provides BASIC Authentication for [[GitRepositoryServlet]].
 */
class GitAuthenticationFilter extends Filter with RepositoryService with AccountService with SystemSettingsService {

  private val logger = LoggerFactory.getLogger(classOf[GitAuthenticationFilter])

  def init(config: FilterConfig) = {}

  def destroy(): Unit = {}

  def doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain): Unit = {
    val request = req.asInstanceOf[HttpServletRequest]
    val response = res.asInstanceOf[HttpServletResponse]

    val wrappedResponse = new HttpServletResponseWrapper(response) {
      override def setCharacterEncoding(encoding: String) = {}
    }

    val isUpdating = request.getRequestURI.endsWith("/git-receive-pack") || "service=git-receive-pack".equals(
      request.getQueryString
    )
    val settings = loadSystemSettings()

    try {
      PluginRegistry()
        .getRepositoryRouting(request.gitRepositoryPath)
        .map {
          case GitRepositoryRouting(_, _, filter) =>
            // served by plug-ins
            pluginRepository(request, wrappedResponse, chain, settings, isUpdating, filter)

        }
        .getOrElse {
          // default repositories
          defaultRepository(request, wrappedResponse, chain, settings, isUpdating)
        }
    } catch {
      case ex: Exception => {
        logger.error("error", ex)
        AuthUtil.requireAuth(response)
      }
    }
  }

  private def pluginRepository(
    request: HttpServletRequest,
    response: HttpServletResponse,
    chain: FilterChain,
    settings: SystemSettings,
    isUpdating: Boolean,
    filter: GitRepositoryFilter
  ): Unit = {
    Database() withSession { implicit session =>
      val account = for {
        authorizationHeader <- Option(request.getHeader("Authorization"))
        account <- authenticateByHeader(authorizationHeader, settings)
      } yield {
        request.setAttribute(Keys.Request.UserName, account.userName)
        account
      }

      if (filter.filter(request.gitRepositoryPath, account.map(_.userName), settings, isUpdating)) {
        chain.doFilter(request, response)
      } else {
        AuthUtil.requireAuth(response)
      }
    }
  }

  private def defaultRepository(
    request: HttpServletRequest,
    response: HttpServletResponse,
    chain: FilterChain,
    settings: SystemSettings,
    isUpdating: Boolean
  ): Unit = {
    val action = request.paths match {
      case Array(_, repositoryOwner, repositoryName, _*) =>
        Database() withSession { implicit session =>
          getRepository(repositoryOwner, repositoryName.replaceFirst("(\\.wiki)?\\.git$", "")) match {
            case Some(repository) => {
              val execute = if (!isUpdating && !repository.repository.isPrivate && settings.allowAnonymousAccess) {
                // Authentication is not required
                true
              } else {
                // Authentication is required
                val passed = for {
                  authorizationHeader <- Option(request.getHeader("Authorization"))
                  account <- authenticateByHeader(authorizationHeader, settings)
                } yield
                  if (isUpdating) {
                    if (hasDeveloperRole(repository.owner, repository.name, Some(account))) {
                      request.setAttribute(Keys.Request.UserName, account.userName)
                      request.setAttribute(Keys.Request.RepositoryLockKey, s"${repository.owner}/${repository.name}")
                      true
                    } else false
                  } else if (repository.repository.isPrivate) {
                    if (hasGuestRole(repository.owner, repository.name, Some(account))) {
                      request.setAttribute(Keys.Request.UserName, account.userName)
                      true
                    } else false
                  } else true
                passed.getOrElse(false)
              }

              if (execute) { () =>
                chain.doFilter(request, response)
              } else { () =>
                AuthUtil.requireAuth(response)
              }
            }
            case None =>
              () =>
                {
                  logger.debug(s"Repository ${repositoryOwner}/${repositoryName} is not found.")
                  response.sendError(HttpServletResponse.SC_NOT_FOUND)
                }
          }
        }
      case _ =>
        () =>
          {
            logger.debug(s"Not enough path arguments: ${request.paths}")
            response.sendError(HttpServletResponse.SC_NOT_FOUND)
          }
    }

    action()
  }

  /**
   * Authenticate by an Authorization header.
   * This accepts one of the following credentials:
   * - username and password
   * - username and personal access token
   *
   * @param authorizationHeader Authorization header
   * @param settings system settings
   * @param s database session
   * @return an account or none
   */
  private def authenticateByHeader(authorizationHeader: String, settings: SystemSettings)(
    implicit s: Session
  ): Option[Account] = {
    val Array(username, password) = AuthUtil.decodeAuthHeader(authorizationHeader).split(":", 2)
    authenticate(settings, username, password).orElse {
      AccessTokenService.getAccountByAccessToken(password) match {
        case Some(account) if account.userName == username => Some(account)
        case _                                             => None
      }
    }
  }
}
