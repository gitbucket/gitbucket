package app

import service._
import util.{FileUploadUtil, FileUtil, AdminAuthenticator}
import util.StringUtil._
import jp.sf.amateras.scalatra.forms._
import org.apache.commons.io.FileUtils
import util.Directory._
import scala.Some

class UserManagementController extends UserManagementControllerBase with AccountService with AdminAuthenticator

trait UserManagementControllerBase extends ControllerBase { self: AccountService with AdminAuthenticator =>
  
  case class UserNewForm(userName: String, password: String, mailAddress: String, isAdmin: Boolean,
                         url: Option[String], fileId: Option[String])

  case class UserEditForm(userName: String, password: Option[String], mailAddress: String, isAdmin: Boolean,
                          url: Option[String], fileId: Option[String], clearImage: Boolean)

  val newForm = mapping(
    "userName"    -> trim(label("Username"     , text(required, maxlength(100), identifier, uniqueUserName))),
    "password"    -> trim(label("Password"     , text(required, maxlength(20)))),
    "mailAddress" -> trim(label("Mail Address" , text(required, maxlength(100), uniqueMailAddress()))),
    "isAdmin"     -> trim(label("User Type"    , boolean())),
    "url"         -> trim(label("URL"          , optional(text(maxlength(200))))),
    "fileId"      -> trim(label("File ID"      , optional(text())))
  )(UserNewForm.apply)

  val editForm = mapping(
    "userName"    -> trim(label("Username"     , text(required, maxlength(100), identifier))),
    "password"    -> trim(label("Password"     , optional(text(maxlength(20))))),
    "mailAddress" -> trim(label("Mail Address" , text(required, maxlength(100), uniqueMailAddress("userName")))),
    "isAdmin"     -> trim(label("User Type"    , boolean())),
    "url"         -> trim(label("URL"          , optional(text(maxlength(200))))),
    "fileId"      -> trim(label("File ID"      , optional(text()))),
    "clearImage"  -> trim(label("Clear image"  , boolean()))
  )(UserEditForm.apply)
  
  get("/admin/users")(adminOnly {
    admin.users.html.list(getAllUsers())
  })
  
  get("/admin/users/_new")(adminOnly {
    admin.users.html.edit(None)
  })
  
  post("/admin/users/_new", newForm)(adminOnly { form =>
    createAccount(form.userName, encrypt(form.password), form.mailAddress, form.isAdmin, form.url)
    updateImage(form.userName, form.fileId)
    redirect("/admin/users")
  })
  
  get("/admin/users/:userName/_edit")(adminOnly {
    val userName = params("userName")
    admin.users.html.edit(getAccountByUserName(userName))
  })
  
  post("/admin/users/:name/_edit", editForm)(adminOnly { form =>
    val userName = params("userName")
    getAccountByUserName(userName).map { account =>
      updateAccount(getAccountByUserName(userName).get.copy(
        password     = form.password.map(encrypt).getOrElse(account.password),
        mailAddress  = form.mailAddress,
        isAdmin      = form.isAdmin,
        url          = form.url))

      if(form.clearImage){
        account.image.map { image =>
          new java.io.File(getUserUploadDir(userName), image).delete()
          updateAvatarImage(userName, None)
        }
      } else {
        updateImage(userName, form.fileId)
      }

      redirect("/admin/users")
    } getOrElse NotFound
  })

  // TODO Merge with AccountController?
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

  // TODO Merge with AccountController?
  private def uniqueUserName: Constraint = new Constraint(){
    def validate(name: String, value: String): Option[String] =
      getAccountByUserName(value).map { _ => "User already exists." }
  }

  // TODO Merge with AccountController?
  private def uniqueMailAddress(paramName: String = ""): Constraint = new Constraint(){
    def validate(name: String, value: String): Option[String] =
      getAccountByMailAddress(value)
        .filter { x => if(paramName.isEmpty) true else Some(x.userName) != params.get(paramName) }
        .map    { _ => "Mail address is already registered." }
  }
  
}