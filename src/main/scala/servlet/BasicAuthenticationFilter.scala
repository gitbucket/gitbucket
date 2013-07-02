package servlet

import javax.servlet._
import javax.servlet.http._
import util.StringUtil._
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
      val paths = request.getRequestURI.split("/")
      val repositoryOwner = paths(2)
      val repositoryName  = paths(3).replaceFirst("\\.git$", "")

      getRepository(repositoryOwner, repositoryName.replaceFirst("\\.wiki", ""), "") match {
        case Some(repository) => {
          if(!request.getRequestURI.endsWith("/git-receive-pack") && !repository.repository.isPrivate){
            chain.doFilter(req, res)
          } else {
            request.getHeader("Authorization") match {
              case null => requireAuth(response)
              case auth => decodeAuthHeader(auth).split(":") match {
                case Array(username, password) if(isWritableUser(username, password, repository)) => {
                  request.setAttribute("USER_NAME", username)
                  chain.doFilter(req, res)
                }
                case _ => requireAuth(response)
              }
            }
          }
        }
        case None => response.sendError(HttpServletResponse.SC_NOT_FOUND)
      }
    } catch {
      case ex: Exception => {
        logger.error("error", ex)
        requireAuth(response)
      }
    }
  }

  private def isWritableUser(username: String, password: String, repository: RepositoryService.RepositoryInfo): Boolean = {
    getAccountByUserName(username) match {
      case Some(account) if(account.password == encrypt(password)) => {
        // TODO Use hasWritePermission?
        (account.isAdmin // administrator
          || account.userName == repository.owner // repository owner
          || getCollaborators(repository.owner, repository.name).contains(account.userName)) // collaborator
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