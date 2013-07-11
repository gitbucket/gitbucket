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

    val wrappedResponse = new HttpServletResponseWrapper(response){
      override def setCharacterEncoding(encoding: String) = {}
    }

    try {
      val paths = request.getRequestURI.substring(request.getContextPath.length).split("/")
      val repositoryOwner = paths(2)
      val repositoryName  = paths(3).replaceFirst("\\.git$", "")

      getRepository(repositoryOwner, repositoryName.replaceFirst("\\.wiki", ""), "") match {
        case Some(repository) => {
          if(!request.getRequestURI.endsWith("/git-receive-pack") && !repository.repository.isPrivate){
            chain.doFilter(req, wrappedResponse)
          } else {
            request.getHeader("Authorization") match {
              case null => requireAuth(response)
              case auth => decodeAuthHeader(auth).split(":") match {
                case Array(username, password) if(isWritableUser(username, password, repository)) => {
                  request.setAttribute("USER_NAME", username)
                  chain.doFilter(req, wrappedResponse)
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
    getAccountByUserName(username).map { account =>
      account.password == sha1(password) && hasWritePermission(repository.owner, repository.name, Some(account))
    } getOrElse false
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