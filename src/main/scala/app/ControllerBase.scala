package app

import _root_.util.Directory._
import _root_.util.Implicits._
import _root_.util.ControlUtil._
import _root_.util.{StringUtil, FileUtil, Validations, Keys}
import org.scalatra._
import org.scalatra.json._
import org.json4s._
import jp.sf.amateras.scalatra.forms._
import org.apache.commons.io.FileUtils
import model.Account
import service.{SystemSettingsService, AccountService}
import javax.servlet.http.{HttpServletResponse, HttpSession, HttpServletRequest}
import java.text.SimpleDateFormat
import javax.servlet.{FilterChain, ServletResponse, ServletRequest}
import org.scalatra.i18n._

/**
 * Provides generic features for controller implementations.
 */
abstract class ControllerBase extends ScalatraFilter
  with ClientSideValidationFormSupport with JacksonJsonSupport with I18nSupport with Validations with SystemSettingsService {

  implicit val jsonFormats = DefaultFormats

  // Don't set content type via Accept header.
  override def format(implicit request: HttpServletRequest) = ""

  override def doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
    val httpRequest  = request.asInstanceOf[HttpServletRequest]
    val httpResponse = response.asInstanceOf[HttpServletResponse]
    val context      = request.getServletContext.getContextPath
    val path         = httpRequest.getRequestURI.substring(context.length)

    if(path.startsWith("/console/")){
      val account = httpRequest.getSession.getAttribute(Keys.Session.LoginAccount).asInstanceOf[Account]
      if(account == null){
        // Redirect to login form
        httpResponse.sendRedirect(context + "/signin?" + StringUtil.urlEncode(path))
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
  implicit def context: Context = Context(servletContext.getContextPath, LoginAccount, request)

  private def LoginAccount: Option[Account] = session.getAs[Account](Keys.Session.LoginAccount)

  def ajaxGet(path : String)(action : => Any) : Route =
    super.get(path){
      request.setAttribute(Keys.Request.Ajax, "true")
      action
    }

  override def ajaxGet[T](path : String, form : ValueType[T])(action : T => Any) : Route =
    super.ajaxGet(path, form){ form =>
      request.setAttribute(Keys.Request.Ajax, "true")
      action(form)
    }

  def ajaxPost(path : String)(action : => Any) : Route =
    super.post(path){
      request.setAttribute(Keys.Request.Ajax, "true")
      action
    }

  override def ajaxPost[T](path : String, form : ValueType[T])(action : T => Any) : Route =
    super.ajaxPost(path, form){ form =>
      request.setAttribute(Keys.Request.Ajax, "true")
      action(form)
    }

  protected def NotFound() =
    if(request.hasAttribute(Keys.Request.Ajax)){
      org.scalatra.NotFound()
    } else {
      org.scalatra.NotFound(html.error("Not Found"))
    }

  protected def Unauthorized()(implicit context: app.Context) =
    if(request.hasAttribute(Keys.Request.Ajax)){
      org.scalatra.Unauthorized()
    } else {
      if(context.loginAccount.isDefined){
        org.scalatra.Unauthorized(redirect("/"))
      } else {
        if(request.getMethod.toUpperCase == "POST"){
          org.scalatra.Unauthorized(redirect("/signin"))
        } else {
          val currentUrl = baseUrl + defining(request.getQueryString){ queryString =>
            request.getRequestURI.substring(request.getContextPath.length) + (if(queryString != null) "?" + queryString else "")
          }
          session.setAttribute(Keys.Session.Redirect, currentUrl)
          org.scalatra.Unauthorized(redirect("/signin"))
        }
      }
    }

  protected def baseUrl = loadSystemSettings().baseUrl.getOrElse {
    defining(request.getRequestURL.toString){ url =>
      url.substring(0, url.length - (request.getRequestURI.length - request.getContextPath.length))
    }
  }.replaceFirst("/$", "")

}

/**
 * Context object for the current request.
 */
case class Context(path: String, loginAccount: Option[Account], request: HttpServletRequest){

  /**
   * Get object from cache.
   *
   * If object has not been cached with the specified key then retrieves by given action.
   * Cached object are available during a request.
   */
  def cache[A](key: String)(action: => A): A =
    defining(Keys.Request.Cache(key)){ cacheKey =>
      Option(request.getAttribute(cacheKey).asInstanceOf[A]).getOrElse {
        val newObject = action
        request.setAttribute(cacheKey, newObject)
        newObject
      }
    }

}

/**
 * Base trait for controllers which manages account information.
 */
trait AccountManagementControllerBase extends ControllerBase with FileUploadControllerBase {
  self: AccountService  =>

  protected def updateImage(userName: String, fileId: Option[String], clearImage: Boolean): Unit =
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

  protected def uniqueUserName: Constraint = new Constraint(){
    override def validate(name: String, value: String, messages: Messages): Option[String] =
      getAccountByUserName(value, true).map { _ => "User already exists." }
  }

  protected def uniqueMailAddress(paramName: String = ""): Constraint = new Constraint(){
    override def validate(name: String, value: String, params: Map[String, String], messages: Messages): Option[String] =
      getAccountByMailAddress(value, true)
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

  def getUploadedFilename(fileId: String)(implicit session: HttpSession): Option[String] =
    session.getAndRemove[String](Keys.Session.Upload(fileId))

}