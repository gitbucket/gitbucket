package app

import service._
import util.OneselfAuthenticator
import util.StringUtil._
import util.Directory._
import jp.sf.amateras.scalatra.forms._
import org.apache.commons.io.FileSystemUtils

class AccountController extends AccountControllerBase
  with SystemSettingsService with AccountService with RepositoryService with ActivityService
  with OneselfAuthenticator

trait AccountControllerBase extends ControllerBase {
  self: SystemSettingsService with AccountService with RepositoryService with ActivityService
    with OneselfAuthenticator =>

  case class AccountNewForm(userName: String, password: String,mailAddress: String, url: Option[String])

  case class AccountEditForm(password: Option[String], mailAddress: String, url: Option[String], fileId: Option[String])

  val newForm = mapping(
    "userName"    -> trim(label("User name"    , text(required, maxlength(100), identifier, uniqueUserName))),
    "password"    -> trim(label("Password"     , text(required, maxlength(20)))),
    "mailAddress" -> trim(label("Mail Address" , text(required, maxlength(100), uniqueMailAddress()))),
    "url"         -> trim(label("URL"          , optional(text(maxlength(200)))))
  )(AccountNewForm.apply)

  val editForm = mapping(
    "password"    -> trim(label("Password"     , optional(text(maxlength(20))))),
    "mailAddress" -> trim(label("Mail Address" , text(required, maxlength(100), uniqueMailAddress("userName")))),
    "url"         -> trim(label("URL"          , optional(text(maxlength(200))))),
    "fileId"      -> trim(label("File ID"      , optional(text())))
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

  get("/:userName/_edit")(oneselfOnly {
    val userName = params("userName")
    getAccountByUserName(userName).map(x => account.html.edit(Some(x))) getOrElse NotFound
  })

  post("/:userName/_edit", editForm)(oneselfOnly { form =>
    val userName = params("userName")
    getAccountByUserName(userName).map { account =>
      updateAccount(account.copy(
        password    = form.password.map(encrypt).getOrElse(account.password),
        mailAddress = form.mailAddress,
        url         = form.url))

      form.fileId.map { fileId =>
        val filename = app.FileUploadUtil.getFilename(fileId)
        org.apache.commons.io.FileUtils.moveFile(
          app.FileUploadUtil.getTemporaryFile(fileId),
          new java.io.File(getUserUploadDir(userName), filename.get)
        )
        updateAvatarImage(userName, filename)
      }

      redirect("/%s".format(userName))
    } getOrElse NotFound
  })

  get("/register"){
    if(loadSystemSettings().allowAccountRegistration){
      account.html.edit(None)
    } else NotFound
  }

  post("/register", newForm){ newForm =>
    if(loadSystemSettings().allowAccountRegistration){
      createAccount(newForm.userName, encrypt(newForm.password), newForm.mailAddress, false, newForm.url)
      redirect("/signin")
    } else NotFound
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
