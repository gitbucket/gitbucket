package app

import _root_.util.Directory._
import _root_.util.{StringUtil, FileUtil, Validations}
import org.scalatra._
import org.scalatra.json._
import org.json4s._
import jp.sf.amateras.scalatra.forms._
import org.apache.commons.io.FileUtils
import model.Account
import scala.Some
import service.AccountService
import javax.servlet.http.{HttpServletResponse, HttpSession, HttpServletRequest}
import java.text.SimpleDateFormat
import javax.servlet.{FilterChain, ServletResponse, ServletRequest}

/**
 * Provides generic features for controller implementations.
 */
abstract class ControllerBase extends ScalatraFilter
  with ClientSideValidationFormSupport with JacksonJsonSupport with Validations {

  implicit val jsonFormats = DefaultFormats

  before("^(?!/assets/).*$".r) {
    // specify content-type excluding "/assets/"
    contentType = "text/html"
  }

  override def doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
    val httpRequest  = request.asInstanceOf[HttpServletRequest]
    val httpResponse = response.asInstanceOf[HttpServletResponse]
    val context      = request.getServletContext.getContextPath
    val path         = httpRequest.getRequestURI.substring(context.length)

    if(path.startsWith("/console/")){
      val account = httpRequest.getSession.getAttribute("LOGIN_ACCOUNT").asInstanceOf[Account]
      if(account == null){
        // Redirect to login form
        httpResponse.sendRedirect(context + "/signin?" + path)
      } else if(account.isAdmin){
        // H2 Console (administrators only)
        chain.doFilter(request, response)
      } else {
        // Redirect to dashboard
        httpResponse.sendRedirect(context + "/")
      }
    } else if(path.startsWith("/git/")){
      // Git repository
      chain.doFilter(request, response)
    } else {
      // Scalatra actions
      super.doFilter(request, response, chain)
    }
  }

  /**
   * Returns the context object for the request.
   */
  implicit def context: Context = Context(servletContext.getContextPath, LoginAccount, currentURL, request)

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
        if(request.getMethod.toUpperCase == "POST"){
          org.scalatra.Unauthorized(redirect("/signin"))
        } else {
          org.scalatra.Unauthorized(redirect("/signin?redirect=" + currentURL))
        }
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
case class Context(path: String, loginAccount: Option[Account], currentUrl: String, request: HttpServletRequest){

  def redirectUrl = {
    if(request.getParameter("redirect") != null){
      request.getParameter("redirect")
    } else {
      currentUrl
    }
  }

  /**
   * Get object from cache.
   *
   * If object has not been cached with the specified key then retrieves by given action.
   * Cached object are available during a request.
   */
  def cache[A](key: String)(action: => A): A = {
    Option(request.getAttribute("cache." + key).asInstanceOf[A]).getOrElse {
      val newObject = action
      request.setAttribute("cache." + key, newObject)
      newObject
    }
  }

}

/**
 * Base trait for controllers which manages account information.
 */
trait AccountManagementControllerBase extends ControllerBase with FileUploadControllerBase {
  self: AccountService  =>

  protected def updateImage(userName: String, fileId: Option[String], clearImage: Boolean): Unit = {
    if(clearImage){
      getAccountByUserName(userName).flatMap(_.image).map { image =>
        new java.io.File(getUserUploadDir(userName), image).delete()
        updateAvatarImage(userName, None)
      }
    } else {
      fileId.map { fileId =>
        val filename = "avatar." + FileUtil.getExtension(getUploadedFilename(fileId).get)
        FileUtils.moveFile(
          getTemporaryFile(fileId),
          new java.io.File(getUserUploadDir(userName), filename)
        )
        updateAvatarImage(userName, Some(filename))
      }
    }
  }

  protected def uniqueUserName: Constraint = new Constraint(){
    override def validate(name: String, value: String): Option[String] =
      getAccountByUserName(value).map { _ => "User already exists." }
  }

  protected def uniqueMailAddress(paramName: String = ""): Constraint = new Constraint(){
    override def validate(name: String, value: String, params: Map[String, String]): Option[String] =
      getAccountByMailAddress(value)
        .filter { x => if(paramName.isEmpty) true else Some(x.userName) != params.get(paramName) }
        .map    { _ => "Mail address is already registered." }
  }

}

/**
 * Base trait for controllers which needs file uploading feature.
 */
trait FileUploadControllerBase {

  def generateFileId: String =
    new SimpleDateFormat("yyyyMMddHHmmSSsss").format(new java.util.Date(System.currentTimeMillis))

  def TemporaryDir(implicit session: HttpSession): java.io.File =
    new java.io.File(GitBucketHome, s"tmp/_upload/${session.getId}")

  def getTemporaryFile(fileId: String)(implicit session: HttpSession): java.io.File =
    new java.io.File(TemporaryDir, fileId)

  //  def removeTemporaryFile(fileId: String)(implicit session: HttpSession): Unit =
  //    getTemporaryFile(fileId).delete()

  def removeTemporaryFiles()(implicit session: HttpSession): Unit =
    FileUtils.deleteDirectory(TemporaryDir)

  def getUploadedFilename(fileId: String)(implicit session: HttpSession): Option[String] = {
    val filename = Option(session.getAttribute("upload_" + fileId).asInstanceOf[String])
    if(filename.isDefined){
      session.removeAttribute("upload_" + fileId)
    }
    filename
  }

}