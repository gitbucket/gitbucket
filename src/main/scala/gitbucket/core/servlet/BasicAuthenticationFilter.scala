package gitbucket.core.servlet

import javax.servlet._
import javax.servlet.http._
import gitbucket.core.plugin.{GitRepositoryFilter, GitRepositoryRouting, PluginRegistry}
import gitbucket.core.service.SystemSettingsService.SystemSettings
import gitbucket.core.service.{RepositoryService, AccountService, SystemSettingsService}
import gitbucket.core.util.{Keys, Implicits}
import org.slf4j.LoggerFactory
import Implicits._

/**
 * Provides BASIC Authentication for [[GitRepositoryServlet]].
 */
class BasicAuthenticationFilter extends Filter with RepositoryService with AccountService with SystemSettingsService {

  private val logger = LoggerFactory.getLogger(classOf[BasicAuthenticationFilter])

  def init(config: FilterConfig) = {}
  
  def destroy(): Unit = {}
  
  def doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain): Unit = {
    val request  = req.asInstanceOf[HttpServletRequest]
    val response = res.asInstanceOf[HttpServletResponse]

    val wrappedResponse = new HttpServletResponseWrapper(response){
      override def setCharacterEncoding(encoding: String) = {}
    }

    val isUpdating = request.getRequestURI.endsWith("/git-receive-pack") || "service=git-receive-pack".equals(request.getQueryString)
    val settings = loadSystemSettings()

    try {
      PluginRegistry().getRepositoryRouting(request.gitRepositoryPath).map { case GitRepositoryRouting(_, _, filter) =>
        // served by plug-ins
        pluginRepository(request, wrappedResponse, chain, settings, isUpdating, filter)

      }.getOrElse {
        // default repositories
        defaultRepository(request, wrappedResponse, chain, settings, isUpdating)
      }
    } catch {
      case ex: Exception => {
        logger.error("error", ex)
        requireAuth(response)
      }
    }
  }

  private def pluginRepository(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain,
                               settings: SystemSettings, isUpdating: Boolean, filter: GitRepositoryFilter): Unit = {
    implicit val r = request

    val account = for {
      auth <- Option(request.getHeader("Authorization"))
      Array(username, password) = decodeAuthHeader(auth).split(":", 2)
      account <- authenticate(settings, username, password)
    } yield {
      request.setAttribute(Keys.Request.UserName, account.userName)
      account
    }

    if(filter.filter(request.gitRepositoryPath, account.map(_.userName), settings, isUpdating)){
      chain.doFilter(request, response)
    } else {
      requireAuth(response)
    }
  }

  private def defaultRepository(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain,
                                settings: SystemSettings, isUpdating: Boolean): Unit = {
    implicit val r = request

    request.paths match {
      case Array(_, repositoryOwner, repositoryName, _*) =>
        getRepository(repositoryOwner, repositoryName.replaceFirst("\\.wiki\\.git$|\\.git$", ""), "") match {
          case Some(repository) => {
            if(!isUpdating && !repository.repository.isPrivate && settings.allowAnonymousAccess){
              chain.doFilter(request, response)
            } else {
              val passed = for {
                auth <- Option(request.getHeader("Authorization"))
                Array(username, password) = decodeAuthHeader(auth).split(":", 2)
                account <- authenticate(settings, username, password)
              } yield if(isUpdating || repository.repository.isPrivate){
                  if(hasWritePermission(repository.owner, repository.name, Some(account))){
                    request.setAttribute(Keys.Request.UserName, account.userName)
                    true
                  } else false
                } else true

              if(passed.getOrElse(false)){
                chain.doFilter(request, response)
              } else {
                requireAuth(response)
              }
            }
          }
          case None => {
            logger.debug(s"Repository ${repositoryOwner}/${repositoryName} is not found.")
            response.sendError(HttpServletResponse.SC_NOT_FOUND)
          }
        }
      case _ => {
        logger.debug(s"Not enough path arguments: ${request.paths}")
        response.sendError(HttpServletResponse.SC_NOT_FOUND)
      }
    }
  }

  private def requireAuth(response: HttpServletResponse): Unit = {
    response.setHeader("WWW-Authenticate", "BASIC realm=\"GitBucket\"")
    response.sendError(HttpServletResponse.SC_UNAUTHORIZED)
  }
  
  private def decodeAuthHeader(header: String): String = {
    try {
      new String(new sun.misc.BASE64Decoder().decodeBuffer(header.substring(6)))
    } catch {
      case _: Throwable => ""
    }
  }
}