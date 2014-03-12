package servlet

import javax.servlet._
import javax.servlet.http._
import model.Profile
import service.{SystemSettingsService, AccountService, RepositoryService}
import org.slf4j.LoggerFactory
import util.Implicits._
import util.ControlUtil._
import util.Keys
import scala.slick.driver.ExtendedProfile

/**
 * Provides BASIC Authentication for [[servlet.GitRepositoryServlet]].
 */
class BasicAuthenticationFilter(override val profile: ExtendedProfile) extends Filter
  with RepositoryService with AccountService with SystemSettingsService with Profile {

  private val logger = LoggerFactory.getLogger(classOf[BasicAuthenticationFilter])

  def init(config: FilterConfig) = {}
  
  def destroy(): Unit = {}
  
  def doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain): Unit = {
    val request  = req.asInstanceOf[HttpServletRequest]
    val response = res.asInstanceOf[HttpServletResponse]

    val wrappedResponse = new HttpServletResponseWrapper(response){
      override def setCharacterEncoding(encoding: String) = {}
    }

    try {
      defining(request.paths){ case Array(_, repositoryOwner, repositoryName, _*) =>
        getRepository(repositoryOwner, repositoryName.replaceFirst("\\.wiki\\.git$|\\.git$", ""), "") match {
          case Some(repository) => {
            if(!request.getRequestURI.endsWith("/git-receive-pack") &&
              !"service=git-receive-pack".equals(request.getQueryString) && !repository.repository.isPrivate){
              chain.doFilter(req, wrappedResponse)
            } else {
              request.getHeader("Authorization") match {
                case null => requireAuth(response)
                case auth => decodeAuthHeader(auth).split(":") match {
                  case Array(username, password) if(isWritableUser(username, password, repository)) => {
                    request.setAttribute(Keys.Request.UserName, username)
                    chain.doFilter(req, wrappedResponse)
                  }
                  case _ => requireAuth(response)
                }
              }
            }
          }
          case None => {
            logger.debug(s"Repository ${repositoryOwner}/${repositoryName} is not found.")
            response.sendError(HttpServletResponse.SC_NOT_FOUND)
          }
        }
      }
    } catch {
      case ex: Exception => {
        logger.error("error", ex)
        requireAuth(response)
      }
    }
  }

  private def isWritableUser(username: String, password: String, repository: RepositoryService.RepositoryInfo): Boolean =
    authenticate(loadSystemSettings(), username, password) match {
      case Some(account) => hasWritePermission(repository.owner, repository.name, Some(account))
      case None => false
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