package util

import javax.servlet._
import javax.servlet.http._

/**
 * Provides BASIC Authentication for [[app.GitRepositoryServlet]].
 */
class BasicAuthenticationFilter extends Filter {
  
  def init(config: FilterConfig) = {}
  
  def destroy(): Unit = {}
  
  def doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain): Unit = {
    val request  = req.asInstanceOf[HttpServletRequest]
    val response = res.asInstanceOf[HttpServletResponse]
    val session  = request.getSession
    
    try {
      session.getAttribute("USER_INFO") match {
        case null => request.getHeader("Authorization") match {
          case null => requireAuth(response)
          case auth => decodeAuthHeader(auth).split(":") match {
            // TODO authenticate using registered user info
            case Array(username, password) if(username == "gitbucket" && password == "password") => {
              session.setAttribute("USER_INFO", "gitbucket")
              chain.doFilter(req, res)
            }
            case _ => requireAuth(response)
          }
        }
        case user => chain.doFilter(req, res)
      }
    } catch {
      case _: Exception => requireAuth(response)
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