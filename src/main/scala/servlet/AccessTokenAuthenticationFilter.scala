package servlet

import javax.servlet._
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

import service.AccessTokenService
import util.Keys
import org.scalatra.servlet.ServletApiImplicits._
import model.Account
import org.scalatra._

class AccessTokenAuthenticationFilter extends Filter with AccessTokenService {
  private val tokenHeaderPrefix = "token "

  override def init(filterConfig: FilterConfig): Unit = {}

  override def destroy(): Unit = {}

  override def doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain): Unit = {
    implicit val request = req.asInstanceOf[HttpServletRequest]
    implicit val session = req.getAttribute(Keys.Request.DBSession).asInstanceOf[slick.jdbc.JdbcBackend#Session]
    val response = res.asInstanceOf[HttpServletResponse]
    Option(request.getHeader("Authorization")).map{
      case auth if auth.startsWith("token ") => AccessTokenService.getAccountByAccessToken(auth.substring(6).trim).toRight(Unit)
      // TODO Basic Authentication Support
      case _ => Left(Unit)
    }.orElse{
      Option(request.getSession.getAttribute(Keys.Session.LoginAccount).asInstanceOf[Account]).map(Right(_))
    } match {
      case Some(Right(account)) => request.setAttribute(Keys.Session.LoginAccount, account); chain.doFilter(req, res)
      case None => chain.doFilter(req, res)
      case Some(Left(_)) => {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED)
        response.setContentType("Content-Type: application/json; charset=utf-8")
        val w = response.getWriter()
        w.print("""{ "message": "Bad credentials" }""")
        w.close()
      }
    }
  }
}
