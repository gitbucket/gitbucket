package servlet

import javax.servlet._
import javax.servlet.http._
import service.{AccountService, RepositoryService}
import org.slf4j.LoggerFactory

/**
 * Provides BASIC Authentication for [[servlet.GitRepositoryServlet]].
 */
class BasicAuthenticationFilter extends Filter with RepositoryService with AccountService {

  private val logger = LoggerFactory.getLogger(classOf[BasicAuthenticationFilter])

  def init(config: FilterConfig) = {}
  
  def destroy(): Unit = {}
  
  def doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain): Unit = {
    val request  = req.asInstanceOf[HttpServletRequest]
    val response = res.asInstanceOf[HttpServletResponse]

    try {
      request.getHeader("Authorization") match {
        case null => requireAuth(response)
        case auth => decodeAuthHeader(auth).split(":") match {
          case Array(username, password) if(isValidUser(username, password, request)) => {
            request.setAttribute("USER_NAME", username)
            chain.doFilter(req, res)
          }
          case _ => requireAuth(response)
        }
      }
    } catch {
      case ex: Exception => {
        logger.error("error", ex)
        requireAuth(response)
      }
    }
  }

  // TODO If the repository is public, it must allow users which have readable right.
  private def isValidUser(username: String, password: String, request: HttpServletRequest): Boolean = {
    val paths = request.getRequestURI.split("/")
    getAccountByUserName(username) match {
      case Some(account) if(account.password == password) => {
        if(account.userType == AccountService.Administrator // administrator
          || account.userName == paths(2) // repository owner
          || getCollaborators(paths(2), paths(3).replaceFirst("\\.git$", "")).contains(account.userName)){ // collaborator
          true
        } else {
          false
        }
      }
      case _ => false
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