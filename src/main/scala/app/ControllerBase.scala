package app

import _root_.util.Directory._
import _root_.util.{FileUploadUtil, FileUtil, Validations}
import org.scalatra._
import org.scalatra.json._
import org.json4s._
import jp.sf.amateras.scalatra.forms._
import org.apache.commons.io.FileUtils
import model.Account
import scala.Some
import service.AccountService

/**
 * Provides generic features for controller implementations.
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
    url.substring(0, url.length - (request.getRequestURI.length - request.getContextPath.length))
  }

}

/**
 * Context object for the current request.
 */
case class Context(path: String, loginAccount: Option[Account], currentUrl: String)

/**
 * Base trait for controllers which manages account information.
 */
trait AccountManagementControllerBase extends ControllerBase { self: AccountService =>

  protected def updateImage(userName: String, fileId: Option[String]): Unit = {
    fileId.map { fileId =>
      val filename = "avatar." + FileUtil.getExtension(FileUploadUtil.getUploadedFilename(fileId).get)
      FileUtils.moveFile(
        FileUploadUtil.getTemporaryFile(fileId),
        new java.io.File(getUserUploadDir(userName), filename)
      )
      updateAvatarImage(userName, Some(filename))
    }
  }

  protected def uniqueUserName: Constraint = new Constraint(){
    def validate(name: String, value: String): Option[String] =
      getAccountByUserName(value).map { _ => "User already exists." }
  }

  protected def uniqueMailAddress(paramName: String = ""): Constraint = new Constraint(){
    def validate(name: String, value: String): Option[String] =
      getAccountByMailAddress(value)
        .filter { x => if(paramName.isEmpty) true else Some(x.userName) != params.get(paramName) }
        .map    { _ => "Mail address is already registered." }
  }

}