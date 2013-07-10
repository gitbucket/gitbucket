package app

import service._
import util.{FileUtil, FileUploadUtil, OneselfAuthenticator}
import util.StringUtil._
import util.Directory._
import jp.sf.amateras.scalatra.forms._
import org.apache.commons.io.FileUtils
import org.scalatra.FlashMapSupport

class AccountController extends AccountControllerBase
  with SystemSettingsService with AccountService with RepositoryService with ActivityService
  with OneselfAuthenticator

trait AccountControllerBase extends ControllerBase with FlashMapSupport {
  self: SystemSettingsService with AccountService with RepositoryService with ActivityService
    with OneselfAuthenticator =>

  case class AccountNewForm(userName: String, password: String,mailAddress: String,
                            url: Option[String], fileId: Option[String])

  case class AccountEditForm(password: Option[String], mailAddress: String,
                             url: Option[String], fileId: Option[String], clearImage: Boolean)

  val newForm = mapping(
    "userName"    -> trim(label("User name"    , text(required, maxlength(100), identifier, uniqueUserName))),
    "password"    -> trim(label("Password"     , text(required, maxlength(20)))),
    "mailAddress" -> trim(label("Mail Address" , text(required, maxlength(100), uniqueMailAddress()))),
    "url"         -> trim(label("URL"          , optional(text(maxlength(200))))),
    "fileId"      -> trim(label("File ID"      , optional(text())))
  )(AccountNewForm.apply)

  val editForm = mapping(
    "password"    -> trim(label("Password"     , optional(text(maxlength(20))))),
    "mailAddress" -> trim(label("Mail Address" , text(required, maxlength(100), uniqueMailAddress("userName")))),
    "url"         -> trim(label("URL"          , optional(text(maxlength(200))))),
    "fileId"      -> trim(label("File ID"      , optional(text()))),
    "clearImage"  -> trim(label("Clear image"  , boolean()))
  )(AccountEditForm.apply)

  /**
   * Displays user information.
   */
  get("/:userName") {
    val userName = params("userName")
    getAccountByUserName(userName).map { x =>
      params.getOrElse("tab", "repositories") match {
        // Public Activity
        case "activity" => account.html.activity(x, getActivitiesByUser(userName, true))
        // Repositories
        case _ => account.html.repositories(x, getVisibleRepositories(userName, baseUrl, context.loginAccount.map(_.userName)))
      }
    } getOrElse NotFound
  }

  get("/:userName/_avatar"){
    val userName = params("userName")
    getAccountByUserName(userName).flatMap(_.image).map { image =>
      contentType = FileUtil.getMimeType(image)
      new java.io.File(getUserUploadDir(userName), image)
    } getOrElse {
      contentType = "image/png"
      Thread.currentThread.getContextClassLoader.getResourceAsStream("noimage.png")
    }
  }

  get("/:userName/_edit")(oneselfOnly {
    val userName = params("userName")
    getAccountByUserName(userName).map(x => account.html.edit(Some(x), flash.get("info"))) getOrElse NotFound
  })

  post("/:userName/_edit", editForm)(oneselfOnly { form =>
    val userName = params("userName")
    getAccountByUserName(userName).map { account =>
      updateAccount(account.copy(
        password    = form.password.map(encrypt).getOrElse(account.password),
        mailAddress = form.mailAddress,
        url         = form.url))

      if(form.clearImage){
        account.image.map { image =>
          new java.io.File(getUserUploadDir(userName), image).delete()
          updateAvatarImage(userName, None)
        }
      } else {
        updateImage(userName, form.fileId)
      }

      flash += "info" -> "Account information has been updated."
      redirect("/%s/_edit".format(userName))
    } getOrElse NotFound
  })

  get("/register"){
    if(loadSystemSettings().allowAccountRegistration){
      account.html.edit(None, None)
    } else NotFound
  }

  post("/register", newForm){ form =>
    if(loadSystemSettings().allowAccountRegistration){
      createAccount(form.userName, encrypt(form.password), form.mailAddress, false, form.url)
      updateImage(form.userName, form.fileId)
      redirect("/signin")
    } else NotFound
  }

  private def updateImage(userName: String, fileId: Option[String]): Unit = {
    fileId.map { fileId =>
      val filename = "avatar." + FileUtil.getExtension(FileUploadUtil.getUploadedFilename(fileId).get)
      FileUtils.moveFile(
        FileUploadUtil.getTemporaryFile(fileId),
        new java.io.File(getUserUploadDir(userName), filename)
      )
      updateAvatarImage(userName, Some(filename))
    }
  }

  // TODO Merge with UserManagementController
  private def uniqueUserName: Constraint = new Constraint(){
    def validate(name: String, value: String): Option[String] =
      getAccountByUserName(value).map { _ => "User already exists." }
  }

  // TODO Merge with UserManagementController
  private def uniqueMailAddress(paramName: String = ""): Constraint = new Constraint(){
    def validate(name: String, value: String): Option[String] =
      getAccountByMailAddress(value)
        .filter { x => if(paramName.isEmpty) true else Some(x.userName) != params.get(paramName) }
        .map    { _ => "Mail address is already registered." }
  }

}
