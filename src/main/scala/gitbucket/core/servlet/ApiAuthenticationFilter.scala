package gitbucket.core.servlet

import javax.servlet._
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

import gitbucket.core.model.Account
import gitbucket.core.service.SystemSettingsService.SystemSettings
import gitbucket.core.service.{AccessTokenService, AccountService, SystemSettingsService}
import gitbucket.core.util.{AuthUtil, Keys}

class ApiAuthenticationFilter extends Filter with AccessTokenService with AccountService with SystemSettingsService {

  override def init(filterConfig: FilterConfig): Unit = {}

  override def destroy(): Unit = {}

  override def doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain): Unit = {
    implicit val request = req.asInstanceOf[HttpServletRequest]
    implicit val session = req.getAttribute(Keys.Request.DBSession).asInstanceOf[slick.jdbc.JdbcBackend#Session]
    val response = res.asInstanceOf[HttpServletResponse]
    Option(request.getHeader("Authorization"))
      .map {
        case auth if auth.startsWith("token ") =>
          AccessTokenService.getAccountByAccessToken(auth.substring(6).trim).toRight(())
        case auth if auth.startsWith("Basic ") => doBasicAuth(auth, loadSystemSettings(), request).toRight(())
        case _                                 => Left(())
      }
      .orElse {
        Option(request.getSession.getAttribute(Keys.Session.LoginAccount).asInstanceOf[Account]).map(Right(_))
      } match {
      case Some(Right(account)) => request.setAttribute(Keys.Session.LoginAccount, account); chain.doFilter(req, res)
      case None                 => chain.doFilter(req, res)
      case Some(Left(_)) => {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED)
        response.setContentType("application/json; charset=utf-8")
        val w = response.getWriter()
        w.print("""{ "message": "Bad credentials" }""")
        w.close()
      }
    }
  }

  def doBasicAuth(auth: String, settings: SystemSettings, request: HttpServletRequest): Option[Account] = {
    implicit val session = request.getAttribute(Keys.Request.DBSession).asInstanceOf[slick.jdbc.JdbcBackend#Session]
    val Array(username, password) = AuthUtil.decodeAuthHeader(auth).split(":", 2)
    authenticate(settings, username, password)
  }
}
