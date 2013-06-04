package app

import model._
import service._
import jp.sf.amateras.scalatra.forms._

class UsersController extends UsersControllerBase with AccountService

trait UsersControllerBase extends ControllerBase { self: AccountService =>
  
  // TODO ユーザ名の先頭に_は使えないようにする＆利用可能文字チェック
  case class UserForm(userName: String, password: String, mailAddress: String, userType: Int, url: Option[String])
  
  val newForm = mapping(
    "userName"    -> trim(label("Username"     , text(required, maxlength(100), unique))), 
    "password"    -> trim(label("Password"     , text(required, maxlength(100)))),
    "mailAddress" -> trim(label("Mail Address" , text(required, maxlength(100)))),
    "userType"    -> trim(label("User Type"    , number())),
    "url"         -> trim(label("URL"          , optional(text(maxlength(200)))))
  )(UserForm.apply)

  val editForm = mapping(
    "userName"    -> trim(label("Username"     , text())), 
    "password"    -> trim(label("Password"     , text(required, maxlength(100)))),
    "mailAddress" -> trim(label("Mail Address" , text(required, maxlength(100)))),
    "userType"    -> trim(label("User Type"    , number())),
    "url"         -> trim(label("URL"          , optional(text(maxlength(200)))))
  )(UserForm.apply)
  
  get("/admin/users"){
    admin.html.userlist(getAllUsers())
  }
  
  get("/admin/users/_new"){
    admin.html.useredit(None)
  }
  
  post("/admin/users/_new", newForm){ form =>
    val currentDate = new java.sql.Date(System.currentTimeMillis)
    createAccount(Account(
        userName       = form.userName, 
        password       = form.password, 
        mailAddress    = form.mailAddress, 
        userType       = form.userType, 
        url            = form.url, 
        registeredDate = currentDate, 
        updatedDate    = currentDate, 
        lastLoginDate  = None))
        
    redirect("/admin/users")
  }
  
  get("/admin/users/:userName/_edit"){
    val userName = params("userName")
    admin.html.useredit(getAccountByUserName(userName))
  }
  
  post("/admin/users/:name/_edit", editForm){ form =>
    val userName = params("userName")
    val currentDate = new java.sql.Date(System.currentTimeMillis)
    updateAccount(getAccountByUserName(userName).get.copy(
        password       = form.password, 
        mailAddress    = form.mailAddress, 
        userType       = form.userType, 
        url            = form.url, 
        updatedDate    = currentDate))
    
    redirect("/admin/users")
  }
  
  def unique: Constraint = new Constraint(){
    def validate(name: String, value: String): Option[String] =
      getAccountByUserName(value).map { _ => "User already exists." }
  }  
  
}