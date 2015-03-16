package gitbucket.core.controller

import gitbucket.core.api.ApiError
import gitbucket.core.model.Account
import gitbucket.core.service.{AccountService, SystemSettingsService}
import gitbucket.core.util.ControlUtil._
import gitbucket.core.util.Directory._
import gitbucket.core.util.Implicits._
import gitbucket.core.util._

import jp.sf.amateras.scalatra.forms._
import org.apache.commons.io.FileUtils
import org.json4s._
import org.scalatra._
import org.scalatra.i18n._
import org.scalatra.json._

import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import javax.servlet.{FilterChain, ServletResponse, ServletRequest}

import scala.util.Try


/**
 * Provides generic features for controller implementations.
 */
abstract class ControllerBase extends ScalatraFilter
  with ClientSideValidationFormSupport with JacksonJsonSupport with I18nSupport with FlashMapSupport with Validations
  with SystemSettingsService {

  implicit val jsonFormats = DefaultFormats

// TODO Scala 2.11
//  // Don't set content type via Accept header.
//  override def format(implicit request: HttpServletRequest) = ""

  override def doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain): Unit = try {
    val httpRequest  = request.asInstanceOf[HttpServletRequest]
    val httpResponse = response.asInstanceOf[HttpServletResponse]
    val context      = request.getServletContext.getContextPath
    val path         = httpRequest.getRequestURI.substring(context.length)

    if(path.startsWith("/console/")){
      val account = httpRequest.getSession.getAttribute(Keys.Session.LoginAccount).asInstanceOf[Account]
      val baseUrl = this.baseUrl(httpRequest)
      if(account == null){
        // Redirect to login form
        httpResponse.sendRedirect(baseUrl + "/signin?redirect=" + StringUtil.urlEncode(path))
      } else if(account.isAdmin){
        // H2 Console (administrators only)
        chain.doFilter(request, response)
      } else {
        // Redirect to dashboard
        httpResponse.sendRedirect(baseUrl + "/")
      }
    } else if(path.startsWith("/git/")){
      // Git repository
      chain.doFilter(request, response)
    } else {
      if(path.startsWith("/api/v3/")){
        httpRequest.setAttribute(Keys.Request.APIv3, true)
      }
      // Scalatra actions
      super.doFilter(request, response, chain)
    }
  } finally {
    contextCache.remove();
  }

  private val contextCache = new java.lang.ThreadLocal[Context]()

  /**
   * Returns the context object for the request.
   */
  implicit def context: Context = {
    contextCache.get match {
      case null => {
        val context = Context(loadSystemSettings(), LoginAccount, request)
        contextCache.set(context)
        context
      }
      case context => context
    }
  }

  private def LoginAccount: Option[Account] = request.getAs[Account](Keys.Session.LoginAccount).orElse(session.getAs[Account](Keys.Session.LoginAccount))

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
    } else if(request.hasAttribute(Keys.Request.APIv3)){
      contentType = formats("json")
      org.scalatra.NotFound(ApiError("Not Found"))
    } else {
      org.scalatra.NotFound(gitbucket.core.html.error("Not Found"))
    }

  protected def Unauthorized()(implicit context: Context) =
    if(request.hasAttribute(Keys.Request.Ajax)){
      org.scalatra.Unauthorized()
    } else if(request.hasAttribute(Keys.Request.APIv3)){
      contentType = formats("json")
      org.scalatra.Unauthorized(ApiError("Requires authentication"))
    } else {
      if(context.loginAccount.isDefined){
        org.scalatra.Unauthorized(redirect("/"))
      } else {
        if(request.getMethod.toUpperCase == "POST"){
          org.scalatra.Unauthorized(redirect("/signin"))
        } else {
          org.scalatra.Unauthorized(redirect("/signin?redirect=" + StringUtil.urlEncode(
            defining(request.getQueryString){ queryString =>
              request.getRequestURI.substring(request.getContextPath.length) + (if(queryString != null) "?" + queryString else "")
            }
          )))
        }
      }
    }

  // TODO Scala 2.11
  override def url(path: String, params: Iterable[(String, Any)] = Iterable.empty,
                   includeContextPath: Boolean = true, includeServletPath: Boolean = true,
                   absolutize: Boolean = true, withSessionId: Boolean = true)
                  (implicit request: HttpServletRequest, response: HttpServletResponse): String =
    if (path.startsWith("http")) path
    else baseUrl + super.url(path, params, false, false, false)

  /**
   * Use this method to response the raw data against XSS.
   */
  protected def RawData[T](contentType: String, rawData: T): T = {
    if(contentType.split(";").head.trim.toLowerCase.startsWith("text/html")){
      this.contentType = "text/plain"
    } else {
      this.contentType = contentType
    }
    response.addHeader("X-Content-Type-Options", "nosniff")
    rawData
  }

  // jenkins send message as 'application/x-www-form-urlencoded' but scalatra already parsed as multi-part-request.
  def extractFromJsonBody[A](implicit request:HttpServletRequest, mf:Manifest[A]): Option[A] = {
    (request.contentType.map(_.split(";").head.toLowerCase) match{
      case Some("application/x-www-form-urlencoded") => multiParams.keys.headOption.map(parse(_))
      case Some("application/json") => Some(parsedBody)
      case _ => Some(parse(request.body))
    }).filterNot(_ == JNothing).flatMap(j => Try(j.extract[A]).toOption)
  }
}

/**
 * Context object for the current request.
 */
case class Context(settings: SystemSettingsService.SystemSettings, loginAccount: Option[Account], request: HttpServletRequest){

  val path = settings.baseUrl.getOrElse(request.getContextPath)
  val currentPath = request.getRequestURI.substring(request.getContextPath.length)
  val baseUrl = settings.baseUrl(request)
  val host = new java.net.URL(baseUrl).getHost

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
trait AccountManagementControllerBase extends ControllerBase {
  self: AccountService  =>

  protected def updateImage(userName: String, fileId: Option[String], clearImage: Boolean): Unit =
    if(clearImage){
      getAccountByUserName(userName).flatMap(_.image).map { image =>
        new java.io.File(getUserUploadDir(userName), image).delete()
        updateAvatarImage(userName, None)
      }
    } else {
      fileId.map { fileId =>
        val filename = "avatar." + FileUtil.getExtension(session.getAndRemove(Keys.Session.Upload(fileId)).get)
        FileUtils.moveFile(
          new java.io.File(getTemporaryDir(session.getId), fileId),
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
