package gitbucket.core.controller

import java.io.{File, FileInputStream}

import gitbucket.core.api.{ApiError, JsonFormat}
import gitbucket.core.model.Account
import gitbucket.core.service.{AccountService, RepositoryService, SystemSettingsService}
import gitbucket.core.util.SyntaxSugars._
import gitbucket.core.util.Directory._
import gitbucket.core.util.Implicits._
import gitbucket.core.util._
import org.json4s._
import org.scalatra._
import org.scalatra.i18n._
import org.scalatra.json._
import org.scalatra.forms._
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import javax.servlet.{FilterChain, ServletRequest, ServletResponse}

import is.tagomor.woothee.Classifier

import scala.util.Try
import scala.util.Using
import net.coobird.thumbnailator.Thumbnails
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.treewalk._
import org.apache.commons.io.IOUtils
import org.slf4j.LoggerFactory

/**
 * Provides generic features for controller implementations.
 */
abstract class ControllerBase
    extends ScalatraFilter
    with ValidationSupport
    with JacksonJsonSupport
    with I18nSupport
    with FlashMapSupport
    with Validations
    with SystemSettingsService {

  private val logger = LoggerFactory.getLogger(getClass)

  implicit val jsonFormats = gitbucket.core.api.JsonFormat.jsonFormats

  before("/api/v3/*") {
    contentType = formats("json")
    request.setAttribute(Keys.Request.APIv3, true)
  }

  override def requestPath(uri: String, idx: Int): String = {
    val path = super.requestPath(uri, idx)
    if (path != "/" && path.endsWith("/")) {
      path.substring(0, path.length - 1)
    } else {
      path
    }
  }

  override def doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain): Unit =
    try {
      val httpRequest = request.asInstanceOf[HttpServletRequest]
      val context = request.getServletContext.getContextPath
      val path = httpRequest.getRequestURI.substring(context.length)

      if (path.startsWith("/git/") || path.startsWith("/git-lfs/")) {
        // Git repository
        chain.doFilter(request, response)
      } else {
        // Scalatra actions
        super.doFilter(request, response, chain)
      }
    } finally {
      contextCache.remove()
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

  private def LoginAccount: Option[Account] =
    request.getAs[Account](Keys.Session.LoginAccount).orElse(session.getAs[Account](Keys.Session.LoginAccount))

  def ajaxGet(path: String)(action: => Any): Route =
    super.get(path) {
      request.setAttribute(Keys.Request.Ajax, "true")
      action
    }

  override def ajaxGet[T](path: String, form: ValueType[T])(action: T => Any): Route =
    super.ajaxGet(path, form) { form =>
      request.setAttribute(Keys.Request.Ajax, "true")
      action(form)
    }

  def ajaxPost(path: String)(action: => Any): Route =
    super.post(path) {
      request.setAttribute(Keys.Request.Ajax, "true")
      action
    }

  override def ajaxPost[T](path: String, form: ValueType[T])(action: T => Any): Route =
    super.ajaxPost(path, form) { form =>
      request.setAttribute(Keys.Request.Ajax, "true")
      action(form)
    }

  protected def NotFound() =
    if (request.hasAttribute(Keys.Request.Ajax)) {
      org.scalatra.NotFound()
    } else if (request.hasAttribute(Keys.Request.APIv3)) {
      contentType = formats("json")
      org.scalatra.NotFound(JsonFormat(ApiError("Not Found")))
    } else {
      org.scalatra.NotFound(gitbucket.core.html.error("Not Found"))
    }

  private def isBrowser(userAgent: String): Boolean = {
    if (userAgent == null || userAgent.isEmpty) {
      false
    } else {
      val data = Classifier.parse(userAgent)
      val category = data.get("category")
      category == "pc" || category == "smartphone" || category == "mobilephone"
    }
  }

  protected def Unauthorized()(implicit context: Context) =
    if (request.hasAttribute(Keys.Request.Ajax)) {
      org.scalatra.Unauthorized()
    } else if (request.hasAttribute(Keys.Request.APIv3)) {
      contentType = formats("json")
      org.scalatra.Unauthorized(JsonFormat(ApiError("Requires authentication")))
    } else if (!isBrowser(request.getHeader("USER-AGENT"))) {
      org.scalatra.Unauthorized()
    } else {
      if (context.loginAccount.isDefined) {
        org.scalatra.Unauthorized(redirect("/"))
      } else {
        if (request.getMethod.toUpperCase == "POST") {
          org.scalatra.Unauthorized(redirect("/signin"))
        } else {
          org.scalatra.Unauthorized(
            redirect(
              "/signin?redirect=" + StringUtil.urlEncode(
                defining(request.getQueryString) { queryString =>
                  request.getRequestURI.substring(request.getContextPath.length) + (if (queryString != null)
                                                                                      "?" + queryString
                                                                                    else "")
                }
              )
            )
          )
        }
      }
    }

  error {
    case e => {
      logger.error(s"Catch unhandled error in request: ${request}", e)
      if (request.hasAttribute(Keys.Request.Ajax)) {
        org.scalatra.InternalServerError()
      } else if (request.hasAttribute(Keys.Request.APIv3)) {
        contentType = formats("json")
        org.scalatra.InternalServerError(JsonFormat(ApiError("Internal Server Error")))
      } else {
        org.scalatra.InternalServerError(gitbucket.core.html.error("Internal Server Error", Some(e)))
      }
    }
  }

  override def url(
    path: String,
    params: Iterable[(String, Any)] = Iterable.empty,
    includeContextPath: Boolean = true,
    includeServletPath: Boolean = true,
    absolutize: Boolean = true,
    withSessionId: Boolean = true
  )(implicit request: HttpServletRequest, response: HttpServletResponse): String =
    if (path.startsWith("http")) path
    else baseUrl + super.url(path, params, false, false, false)

  /**
   * Extends scalatra-form's trim rule to eliminate CR and LF.
   */
  protected def trim2[T](valueType: SingleValueType[T]): SingleValueType[T] = new SingleValueType[T]() {
    def convert(value: String, messages: Messages): T = valueType.convert(trim(value), messages)

    override def validate(
      name: String,
      value: String,
      params: Map[String, Seq[String]],
      messages: Messages
    ): Seq[(String, String)] =
      valueType.validate(name, trim(value), params, messages)

    private def trim(value: String): String = if (value == null) null else value.replace("\r\n", "").trim
  }

  /**
   * Use this method to response the raw data against XSS.
   */
  protected def RawData[T](contentType: String, rawData: T): T = {
    if (contentType.split(";").head.trim.toLowerCase.startsWith("text/html")) {
      this.contentType = "text/plain"
    } else {
      this.contentType = contentType
    }
    response.addHeader("X-Content-Type-Options", "nosniff")
    rawData
  }

  // jenkins send message as 'application/x-www-form-urlencoded' but scalatra already parsed as multi-part-request.
  def extractFromJsonBody[A](implicit request: HttpServletRequest, mf: Manifest[A]): Option[A] = {
    (request.contentType.map(_.split(";").head.toLowerCase) match {
      case Some("application/x-www-form-urlencoded") => multiParams.keys.headOption.map(parse(_))
      case Some("application/json")                  => Some(parsedBody)
      case _                                         => Some(parse(request.body))
    }).filterNot(_ == JNothing).flatMap(j => Try(j.extract[A]).toOption)
  }

  protected def getPathObjectId(git: Git, path: String, revCommit: RevCommit): Option[ObjectId] = {
    @scala.annotation.tailrec
    def _getPathObjectId(path: String, walk: TreeWalk): Option[ObjectId] = walk.next match {
      case true if (walk.getPathString == path) => Some(walk.getObjectId(0))
      case true                                 => _getPathObjectId(path, walk)
      case false                                => None
    }

    Using.resource(new TreeWalk(git.getRepository)) { treeWalk =>
      treeWalk.addTree(revCommit.getTree)
      treeWalk.setRecursive(true)
      _getPathObjectId(path, treeWalk)
    }
  }

  protected def responseRawFile(
    git: Git,
    objectId: ObjectId,
    path: String,
    repository: RepositoryService.RepositoryInfo
  ): Unit = {
    JGitUtil.getObjectLoaderFromId(git, objectId) { loader =>
      contentType = FileUtil.getSafeMimeType(path)

      if (loader.isLarge) {
        response.setContentLength(loader.getSize.toInt)
        loader.copyTo(response.outputStream)
      } else {
        val bytes = loader.getCachedBytes
        val text = new String(bytes, "UTF-8")

        val attrs = JGitUtil.getLfsObjects(text)
        if (attrs.nonEmpty) {
          response.setContentLength(attrs("size").toInt)
          val oid = attrs("oid").split(":")(1)

          Using.resource(new FileInputStream(FileUtil.getLfsFilePath(repository.owner, repository.name, oid))) { in =>
            IOUtils.copy(in, response.getOutputStream)
          }
        } else {
          response.setContentLength(loader.getSize.toInt)
          response.getOutputStream.write(bytes)
        }
      }
    }
  }
}

/**
 * Context object for the current request.
 */
case class Context(
  settings: SystemSettingsService.SystemSettings,
  loginAccount: Option[Account],
  request: HttpServletRequest
) {
  val path = settings.baseUrl.getOrElse(request.getContextPath)
  val currentPath = request.getRequestURI.substring(request.getContextPath.length)
  val baseUrl = settings.baseUrl(request)
  val host = new java.net.URL(baseUrl).getHost
  val platform = request.getHeader("User-Agent") match {
    case null                             => null
    case agent if agent.contains("Mac")   => "mac"
    case agent if agent.contains("Linux") => "linux"
    case agent if agent.contains("Win")   => "windows"
    case _                                => null
  }
  val sidebarCollapse = request.getSession.getAttribute("sidebar-collapse") != null

  /**
   * Get object from cache.
   *
   * If object has not been cached with the specified key then retrieves by given action.
   * Cached object are available during a request.
   */
  def cache[A](key: String)(action: => A): A =
    defining(Keys.Request.Cache(key)) { cacheKey =>
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
  self: AccountService =>

  private val logger = LoggerFactory.getLogger(getClass)

  protected def updateImage(userName: String, fileId: Option[String], clearImage: Boolean): Unit =
    if (clearImage) {
      getAccountByUserName(userName).flatMap(_.image).foreach { image =>
        new File(getUserUploadDir(userName), FileUtil.checkFilename(image)).delete()
        updateAvatarImage(userName, None)
      }
    } else {
      try {
        fileId.foreach { fileId =>
          val filename = "avatar." + FileUtil.getExtension(session.getAndRemove(Keys.Session.Upload(fileId)).get)
          val uploadDir = getUserUploadDir(userName)
          if (!uploadDir.exists) {
            uploadDir.mkdirs()
          }
          Thumbnails
            .of(new File(getTemporaryDir(session.getId), FileUtil.checkFilename(fileId)))
            .size(324, 324)
            .toFile(new File(uploadDir, FileUtil.checkFilename(filename)))
          updateAvatarImage(userName, Some(filename))
        }
      } catch {
        case e: Exception => logger.info("Error while updateImage" + e.getMessage)
      }
    }

  protected def uniqueUserName: Constraint = new Constraint() {
    override def validate(name: String, value: String, messages: Messages): Option[String] =
      getAccountByUserNameIgnoreCase(value, true).map { _ =>
        "User already exists."
      }
  }

  protected def uniqueMailAddress(paramName: String = ""): Constraint = new Constraint() {
    override def validate(
      name: String,
      value: String,
      params: Map[String, Seq[String]],
      messages: Messages
    ): Option[String] = {
      val extraMailAddresses = params.view.filterKeys(k => k.startsWith("extraMailAddresses"))
      if (extraMailAddresses.exists {
            case (k, v) =>
              v.contains(value)
          }) {
        Some("These mail addresses are duplicated.")
      } else {
        getAccountByMailAddress(value, true)
          .collect {
            case x if paramName.isEmpty || Some(x.userName) != params.optionValue(paramName) =>
              "Mail address is already registered."
          }
      }
    }
  }

  protected def uniqueExtraMailAddress(paramName: String = ""): Constraint = new Constraint() {
    override def validate(
      name: String,
      value: String,
      params: Map[String, Seq[String]],
      messages: Messages
    ): Option[String] = {
      val extraMailAddresses = params.view.filterKeys(k => k.startsWith("extraMailAddresses"))
      if (Some(value) == params.optionValue("mailAddress") || extraMailAddresses.count {
            case (k, v) =>
              v.contains(value)
          } > 1) {
        Some("These mail addresses are duplicated.")
      } else {
        getAccountByMailAddress(value, true)
          .collect {
            case x if paramName.isEmpty || Some(x.userName) != params.optionValue(paramName) =>
              "Mail address is already registered."
          }
      }
    }
  }

  val allReservedNames = Set(
    "git",
    "admin",
    "upload",
    "api",
    "assets",
    "plugin-assets",
    "signin",
    "signout",
    "register",
    "activities.atom",
    "sidebar-collapse",
    "groups",
    "new"
  )

  protected def reservedNames(): Constraint = new Constraint() {
    override def validate(name: String, value: String, messages: Messages): Option[String] =
      if (allReservedNames.contains(value.toLowerCase)) {
        Some(s"${value} is reserved")
      } else {
        None
      }
  }

}
