package app

import service._
import util.AdminAuthenticator
import util.StringUtil._
import jp.sf.amateras.scalatra.forms._

class UserManagementController extends UserManagementControllerBase
  with AccountService with RepositoryService with AdminAuthenticator

trait UserManagementControllerBase extends AccountManagementControllerBase {
  self: AccountService with RepositoryService with AdminAuthenticator =>
  
  case class NewUserForm(userName: String, password: String, mailAddress: String, isAdmin: Boolean,
                         url: Option[String], fileId: Option[String])

  case class EditUserForm(userName: String, password: Option[String], mailAddress: String, isAdmin: Boolean,
                          url: Option[String], fileId: Option[String], clearImage: Boolean)

  case class NewGroupForm(groupName: String, fileId: Option[String], memberNames: Option[String])

  case class EditGroupForm(groupName: String, fileId: Option[String], memberNames: Option[String], clearImage: Boolean)

  val newUserForm = mapping(
    "userName"    -> trim(label("Username"     , text(required, maxlength(100), identifier, uniqueUserName))),
    "password"    -> trim(label("Password"     , text(required, maxlength(20)))),
    "mailAddress" -> trim(label("Mail Address" , text(required, maxlength(100), uniqueMailAddress()))),
    "isAdmin"     -> trim(label("User Type"    , boolean())),
    "url"         -> trim(label("URL"          , optional(text(maxlength(200))))),
    "fileId"      -> trim(label("File ID"      , optional(text())))
  )(NewUserForm.apply)

  val editUserForm = mapping(
    "userName"    -> trim(label("Username"     , text(required, maxlength(100), identifier))),
    "password"    -> trim(label("Password"     , optional(text(maxlength(20))))),
    "mailAddress" -> trim(label("Mail Address" , text(required, maxlength(100), uniqueMailAddress("userName")))),
    "isAdmin"     -> trim(label("User Type"    , boolean())),
    "url"         -> trim(label("URL"          , optional(text(maxlength(200))))),
    "fileId"      -> trim(label("File ID"      , optional(text()))),
    "clearImage"  -> trim(label("Clear image"  , boolean()))
  )(EditUserForm.apply)

  val newGroupForm = mapping(
    "groupName"   -> trim(label("Group name"   , text(required, maxlength(100), identifier))),
    "fileId"      -> trim(label("File ID"      , optional(text()))),
    "memberNames" -> trim(label("Member Names" , optional(text())))
  )(NewGroupForm.apply)

  val editGroupForm = mapping(
    "groupName"   -> trim(label("Group name"   , text(required, maxlength(100), identifier))),
    "fileId"      -> trim(label("File ID"      , optional(text()))),
    "memberNames" -> trim(label("Member Names" , optional(text()))),
    "clearImage"  -> trim(label("Clear image"  , boolean()))
  )(EditGroupForm.apply)

  get("/admin/users")(adminOnly {
    val users = getAllUsers()
    val members = users.collect { case account if(account.isGroupAccount) =>
      account.userName -> getGroupMembers(account.userName)
    }.toMap
    admin.users.html.list(users, members)
  })
  
  get("/admin/users/_new")(adminOnly {
    admin.users.html.edit(None)
  })
  
  post("/admin/users/_new", newUserForm)(adminOnly { form =>
    createAccount(form.userName, sha1(form.password), form.mailAddress, form.isAdmin, form.url)
    updateImage(form.userName, form.fileId, false)
    redirect("/admin/users")
  })
  
  get("/admin/users/:userName/_edit")(adminOnly {
    val userName = params("userName")
    admin.users.html.edit(getAccountByUserName(userName))
  })
  
  post("/admin/users/:name/_edit", editUserForm)(adminOnly { form =>
    val userName = params("userName")
    getAccountByUserName(userName).map { account =>
      updateAccount(getAccountByUserName(userName).get.copy(
        password     = form.password.map(sha1).getOrElse(account.password),
        mailAddress  = form.mailAddress,
        isAdmin      = form.isAdmin,
        url          = form.url))

      updateImage(userName, form.fileId, form.clearImage)
      redirect("/admin/users")

    } getOrElse NotFound
  })

  get("/admin/users/_newgroup")(adminOnly {
    admin.users.html.group(None, Nil)
  })

  post("/admin/users/_newgroup", newGroupForm)(adminOnly { form =>
    createGroup(form.groupName)
    updateGroupMembers(form.groupName, form.memberNames.map(_.split(",").toList).getOrElse(Nil))
    updateImage(form.groupName, form.fileId, false)
    redirect("/admin/users")
  })

  get("/admin/users/:groupName/_editgroup")(adminOnly {
    val groupName = params("groupName")
    admin.users.html.group(getAccountByUserName(groupName), getGroupMembers(groupName))
  })

  post("/admin/users/:groupName/_editgroup", editGroupForm)(adminOnly { form =>
    val groupName = params("groupName")
    getAccountByUserName(groupName).map { account =>
      val memberNames = form.memberNames.map(_.split(",").toList).getOrElse(Nil)
      updateGroupMembers(form.groupName, memberNames)

      getRepositoryNamesOfUser(form.groupName).foreach { repositoryName =>
        removeCollaborators(form.groupName, repositoryName)
        memberNames.foreach { userName =>
          addCollaborator(form.groupName, repositoryName, userName)
        }
      }

      updateImage(form.groupName, form.fileId, form.clearImage)
      redirect("/admin/users")

    } getOrElse NotFound
  })

//  /**
//   * JSON API for collaborator completion.
//   */
//  // TODO Merge with RepositorySettingsController
//  get("/admin/users/_members"){
//    contentType = formats("json")
//    org.json4s.jackson.Serialization.write(Map("options" -> getAllUsers.filter(!_.isGroupAccount).map(_.userName).toArray))
//  }

}