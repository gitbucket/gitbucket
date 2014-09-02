package servlet

import javax.servlet._
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import org.apache.commons.io.IOUtils
import play.twirl.api.Html
import service.{AccountService, RepositoryService, SystemSettingsService}
import model.{Account, Session}
import util.{JGitUtil, Keys}
import plugin.{RawData, Fragment, PluginConnectionHolder, Redirect}
import service.RepositoryService.RepositoryInfo
import plugin.Security._

class PluginActionInvokeFilter extends Filter with SystemSettingsService with RepositoryService with AccountService {

  def init(config: FilterConfig) = {}

  def destroy(): Unit = {}

  def doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain): Unit = {
    (req, res) match {
      case (request: HttpServletRequest, response: HttpServletResponse) => {
        Database(req.getServletContext) withTransaction { implicit session =>
          val path = request.getRequestURI.substring(request.getServletContext.getContextPath.length)
          if(!processGlobalAction(path, request, response) && !processRepositoryAction(path, request, response)){
            chain.doFilter(req, res)
          }
        }
      }
    }
  }

  private def processGlobalAction(path: String, request: HttpServletRequest, response: HttpServletResponse)
                                 (implicit session: Session): Boolean = {
    plugin.PluginSystem.globalActions.find(x =>
      x.method.toLowerCase == request.getMethod.toLowerCase && path.matches(x.path)
    ).map { action =>
      val loginAccount = request.getSession.getAttribute(Keys.Session.LoginAccount).asInstanceOf[Account]
      val systemSettings = loadSystemSettings()
      implicit val context = app.Context(systemSettings, Option(loginAccount), request)

      if(authenticate(action.security, context)){
        val result = try {
          PluginConnectionHolder.threadLocal.set(session.conn)
          action.function(request, response, context)
        } finally {
          PluginConnectionHolder.threadLocal.remove()
        }
        processActionResult(result, request, response, context)
      } else {
        // TODO NotFound or Error?
      }
      true
    } getOrElse false
  }

  private def processRepositoryAction(path: String, request: HttpServletRequest, response: HttpServletResponse)
                                     (implicit session: Session): Boolean = {
    val elements = path.split("/")
    if(elements.length > 3){
      val owner  = elements(1)
      val name   = elements(2)
      val remain = elements.drop(3).mkString("/", "/", "")

      val loginAccount = request.getSession.getAttribute(Keys.Session.LoginAccount).asInstanceOf[Account]
      val systemSettings = loadSystemSettings()
      implicit val context = app.Context(systemSettings, Option(loginAccount), request)

      getRepository(owner, name, systemSettings.baseUrl(request)).flatMap { repository =>
        plugin.PluginSystem.repositoryActions.find(x => remain.matches(x.path)).map { action =>
          if(authenticate(action.security, context, repository)){
            val result = try {
              PluginConnectionHolder.threadLocal.set(session.conn)
              action.function(request, response, context, repository)
            } finally {
              PluginConnectionHolder.threadLocal.remove()
            }
            processActionResult(result, request, response, context)
          } else {
            // TODO NotFound or Error?
          }
          true
        }
      } getOrElse false
    } else false
  }

  private def processActionResult(result: Any, request: HttpServletRequest, response: HttpServletResponse,
                                   context: app.Context): Unit = {
    result match {
      case null|None         => renderError(request, response, context, 404)
      case x: String         => renderGlobalHtml(request, response, context, x)
      case Some(x: String)   => renderGlobalHtml(request, response, context, x)
      case x: Html           => renderGlobalHtml(request, response, context, x.toString)
      case Some(x: Html)     => renderGlobalHtml(request, response, context, x.toString)
      case x: Fragment       => renderFragmentHtml(request, response, context, x.html.toString)
      case Some(x: Fragment) => renderFragmentHtml(request, response, context, x.html.toString)
      case x: RawData        => renderRawData(request, response, context, x)
      case Some(x: RawData)  => renderRawData(request, response, context, x)
      case x: Redirect       => response.sendRedirect(x.path)
      case Some(x: Redirect) => response.sendRedirect(x.path)
      case x: AnyRef         => renderJson(request, response, x)
    }
  }

  /**
   * Authentication for global action
   */
  private def authenticate(security: Security, context: app.Context)(implicit session: Session): Boolean = {
    // Global Action
    security match {
      case All() => true
      case Login() => context.loginAccount.isDefined
      case Admin() => context.loginAccount.exists(_.isAdmin)
      case _ => false // TODO throw Exception?
    }
  }

  /**
   * Authenticate for repository action
   */
  private def authenticate(security: Security, context: app.Context, repository: RepositoryInfo)(implicit session: Session): Boolean = {
    if(repository.repository.isPrivate){
      // Private Repository
      security match {
        case Admin() => context.loginAccount.exists(_.isAdmin)
        case Owner() => context.loginAccount.exists { account =>
          account.userName == repository.owner ||
            getGroupMembers(repository.owner).exists(m => m.userName == account.userName && m.isManager)
        }
        case _ => context.loginAccount.exists { account =>
          account.isAdmin || account.userName == repository.owner ||
            getCollaborators(repository.owner, repository.name).contains(account.userName)
        }
      }
    } else {
      // Public Repository
      security match {
        case All() => true
        case Login() => context.loginAccount.isDefined
        case Owner() => context.loginAccount.exists { account =>
          account.userName == repository.owner ||
            getGroupMembers(repository.owner).exists(m => m.userName == account.userName && m.isManager)
        }
        case Member() => context.loginAccount.exists { account =>
          account.userName == repository.owner ||
            getCollaborators(repository.owner, repository.name).contains(account.userName)
        }
        case Admin() => context.loginAccount.exists(_.isAdmin)
      }
    }
  }

  private def renderError(request: HttpServletRequest, response: HttpServletResponse, context: app.Context, error: Int): Unit = {
    response.sendError(error)
  }

  private def renderGlobalHtml(request: HttpServletRequest, response: HttpServletResponse, context: app.Context, body: String): Unit = {
    response.setContentType("text/html; charset=UTF-8")
    val html = _root_.html.main("GitBucket", None)(Html(body))(context)
    IOUtils.write(html.toString.getBytes("UTF-8"), response.getOutputStream)
  }

  private def renderRepositoryHtml(request: HttpServletRequest, response: HttpServletResponse, context: app.Context, repository: RepositoryInfo, body: String): Unit = {
    response.setContentType("text/html; charset=UTF-8")
    val html = _root_.html.main("GitBucket", None)(_root_.html.menu("", repository)(Html(body))(context))(context) // TODO specify active side menu
    IOUtils.write(html.toString.getBytes("UTF-8"), response.getOutputStream)
  }

  private def renderFragmentHtml(request: HttpServletRequest, response: HttpServletResponse, context: app.Context, body: String): Unit = {
    response.setContentType("text/html; charset=UTF-8")
    IOUtils.write(body.getBytes("UTF-8"), response.getOutputStream)
  }

  private def renderRawData(request: HttpServletRequest, response: HttpServletResponse, context: app.Context, rawData: RawData): Unit = {
    response.setContentType(rawData.contentType)
    IOUtils.write(rawData.content, response.getOutputStream)
  }

  private def renderJson(request: HttpServletRequest, response: HttpServletResponse, obj: AnyRef): Unit = {
    import org.json4s._
    import org.json4s.jackson.Serialization
    import org.json4s.jackson.Serialization.write
    implicit val formats = Serialization.formats(NoTypeHints)

    val json = write(obj)

    response.setContentType("application/json; charset=UTF-8")
    IOUtils.write(json.getBytes("UTF-8"), response.getOutputStream)
  }

}
