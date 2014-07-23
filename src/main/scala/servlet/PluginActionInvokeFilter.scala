package servlet

import javax.servlet._
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import org.apache.commons.io.IOUtils
import twirl.api.Html
import service.{AccountService, RepositoryService, SystemSettingsService}
import model.Account
import util.{JGitUtil, Keys}

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
      result match {
        case x: String => {
          response.setContentType("text/html; charset=UTF-8")
          val loginAccount = request.getSession.getAttribute(Keys.Session.LoginAccount).asInstanceOf[Account]
          implicit val context = app.Context(loadSystemSettings(), Option(loginAccount), request)
          val html = _root_.html.main("GitBucket", None)(Html(x))
          IOUtils.write(html.toString.getBytes("UTF-8"), response.getOutputStream)
        }
        case x => {
          // TODO returns as JSON?
          response.setContentType("application/json; charset=UTF-8")

        }
      }
      true
    } getOrElse false
  }

  private def processRepositoryAction(path: String, request: HttpServletRequest, response: HttpServletResponse)
                                     (implicit session: model.simple.Session): Boolean = {
    val elements = path.split("/")
    if(elements.length > 3){
      val owner  = elements(1)
      val name   = elements(2)
      val remain = elements.drop(3).mkString("/", "/", "")
      val systemSettings = loadSystemSettings()
      getRepository(owner, name, systemSettings.baseUrl(request)).flatMap { repository =>
        plugin.PluginSystem.repositoryActions.find(_.path == remain).map { action =>
          val result = action.function(request, response, repository)
          result match {
            case x: String => {
              response.setContentType("text/html; charset=UTF-8")
              val loginAccount = request.getSession.getAttribute(Keys.Session.LoginAccount).asInstanceOf[Account]
              implicit val context = app.Context(systemSettings, Option(loginAccount), request)
              val html = _root_.html.main("GitBucket", None)(_root_.html.menu("", repository)(Html(x))) // TODO specify active side menu
              IOUtils.write(html.toString.getBytes("UTF-8"), response.getOutputStream)
            }
            case x => {
              // TODO returns as JSON?
              response.setContentType("application/json; charset=UTF-8")

            }
          }
          true
        }
      } getOrElse false
    } else false
  }

}
