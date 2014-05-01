package servlet

import javax.servlet._
import javax.servlet.http._
import service.{SystemSettingsService, AccountService, RepositoryService}
import model.Account
import org.slf4j.LoggerFactory
import util.Implicits._
import util.ControlUtil._
import util.Keys

/**
 * Provides BASIC Authentication for [[servlet.GitRepositoryServlet]].
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
                  case Array(username, password) => getWritableUser(username, password, repository) match {
                    case Some(account) => {
                      request.setAttribute(Keys.Request.UserName, account.userName)
                      chain.doFilter(req, wrappedResponse)
                    }
                    case None => requireAuth(response)
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

  private def getWritableUser(username: String, password: String, repository: RepositoryService.RepositoryInfo): Option[Account] =
    authenticate(loadSystemSettings(), username, password) match {
      case x @ Some(account) if(hasWritePermission(repository.owner, repository.name, x)) => x
      case _ => None
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