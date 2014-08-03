package servlet

import javax.servlet._
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import org.apache.commons.io.IOUtils
import twirl.api.Html
import service.{AccountService, RepositoryService, SystemSettingsService}
import model.{Account, Session}
import util.{JGitUtil, Keys}
import plugin.PluginConnectionHolder
import service.RepositoryService.RepositoryInfo
import service.SystemSettingsService.SystemSettings

class PluginActionInvokeFilter extends Filter with SystemSettingsService with RepositoryService with AccountService {

  def init(config: FilterConfig) = {}

  def destroy(): Unit = {}

  def doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain): Unit = {
    (req, res) match {
      case (request: HttpServletRequest, response: HttpServletResponse) => {
        Database(req.getServletContext) withTransaction { implicit session =>
          val path = req.asInstanceOf[HttpServletRequest].getRequestURI
          if(!processGlobalAction(path, request, response) && !processRepositoryAction(path, request, response)){
            chain.doFilter(req, res)
          }
        }
      }
    }
  }

  private def processGlobalAction(path: String, request: HttpServletRequest, response: HttpServletResponse): Boolean = {
    plugin.PluginSystem.globalActions.find(_.path == path).map { action =>
      val result = action.function(request, response)
      val systemSettings = loadSystemSettings()
      result match {
        case x: String => renderGlobalHtml(request, response, systemSettings, x)
        case x: org.mozilla.javascript.NativeObject => {
          x.get("format") match {
            case "html" => renderGlobalHtml(request, response, systemSettings, x.get("body").toString)
            case "json" => renderJson(request, response, x.get("body").toString)
          }
        }
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
      val systemSettings = loadSystemSettings()
      getRepository(owner, name, systemSettings.baseUrl(request)).flatMap { repository =>
        plugin.PluginSystem.repositoryActions.find(_.path == remain).map { action =>
          val result = try {
            PluginConnectionHolder.threadLocal.set(session.conn)
            action.function(request, response, repository)
          } finally {
            PluginConnectionHolder.threadLocal.remove()
          }
          result match {
            case x: String => renderRepositoryHtml(request, response, systemSettings, repository, x)
            case x: org.mozilla.javascript.NativeObject => {
              x.get("format") match {
                case "html" => renderRepositoryHtml(request, response, systemSettings, repository, x.get("body").toString)
                case "json" => renderJson(request, response, x.get("body").toString)
              }
            }
          }
          true
        }
      } getOrElse false
    } else false
  }

  private def renderGlobalHtml(request: HttpServletRequest, response: HttpServletResponse,
                               systemSettings: SystemSettings, body: String): Unit = {
    response.setContentType("text/html; charset=UTF-8")
    val loginAccount = request.getSession.getAttribute(Keys.Session.LoginAccount).asInstanceOf[Account]
    implicit val context = app.Context(systemSettings, Option(loginAccount), request)
    val html = _root_.html.main("GitBucket", None)(Html(body))
    IOUtils.write(html.toString.getBytes("UTF-8"), response.getOutputStream)
  }

  private def renderRepositoryHtml(request: HttpServletRequest, response: HttpServletResponse,
                                   systemSettings: SystemSettings, repository: RepositoryInfo, body: String): Unit = {
    response.setContentType("text/html; charset=UTF-8")
    val loginAccount = request.getSession.getAttribute(Keys.Session.LoginAccount).asInstanceOf[Account]
    implicit val context = app.Context(systemSettings, Option(loginAccount), request)
    val html = _root_.html.main("GitBucket", None)(_root_.html.menu("", repository)(Html(body))) // TODO specify active side menu
    IOUtils.write(html.toString.getBytes("UTF-8"), response.getOutputStream)
  }

  private def renderJson(request: HttpServletRequest, response: HttpServletResponse, body: String): Unit = {
    response.setContentType("application/json; charset=UTF-8")
    IOUtils.write(body.getBytes("UTF-8"), response.getOutputStream)
  }

}
