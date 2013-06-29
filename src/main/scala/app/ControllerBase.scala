package app

import model.Account
import util.Validations
import org.scalatra._
import org.scalatra.json._
import org.json4s._
import jp.sf.amateras.scalatra.forms._

/**
 * Provides generic features for ScalatraServlet implementations.
 */
abstract class ControllerBase extends ScalatraFilter
  with ClientSideValidationFormSupport with JacksonJsonSupport with Validations {

  implicit val jsonFormats = DefaultFormats

  /**
   * Returns the context object for the request.
   */
  implicit def context: Context = Context(servletContext.getContextPath, LoginAccount, currentURL)

  private def currentURL: String = {
    val queryString = request.getQueryString
    request.getRequestURI + (if(queryString != null) "?" + queryString else "")
  }

  private def LoginAccount: Option[Account] = {
    session.get("LOGIN_ACCOUNT") match {
      case Some(x: Account) => Some(x)
      case _ => None
    }
  }

  def ajaxGet(path : String)(action : => Any) : Route = {
    super.get(path){
      request.setAttribute("AJAX", "true")
      action
    }
  }

  override def ajaxGet[T](path : String, form : MappingValueType[T])(action : T => Any) : Route = {
    super.ajaxGet(path, form){ form =>
      request.setAttribute("AJAX", "true")
      action(form)
    }
  }

  def ajaxPost(path : String)(action : => Any) : Route = {
    super.post(path){
      request.setAttribute("AJAX", "true")
      action
    }
  }

  override def ajaxPost[T](path : String, form : MappingValueType[T])(action : T => Any) : Route = {
    super.ajaxPost(path, form){ form =>
      request.setAttribute("AJAX", "true")
      action(form)
    }
  }

  protected def NotFound() = {
    if(request.getAttribute("AJAX") == null){
      org.scalatra.NotFound(html.error("Not Found"))
    } else {
      org.scalatra.NotFound()
    }
  }

  // TODO redirect to the sign-in page if not logged in?
  protected def Unauthorized()(implicit context: app.Context) = {
    if(request.getAttribute("AJAX") == null){
      if(context.loginAccount.isDefined){
        org.scalatra.Unauthorized(redirect("/"))
      } else {
        org.scalatra.Unauthorized(redirect("/signin?" + currentURL))
      }
    } else {
      org.scalatra.Unauthorized()
    }
  }

  protected def baseUrl = {
    val url = request.getRequestURL.toString
    url.substring(0, url.length - request.getRequestURI.length)
  }

}

case class Context(path: String, loginAccount: Option[Account], currentUrl: String)