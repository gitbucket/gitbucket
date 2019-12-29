package gitbucket.core.controller.api
import gitbucket.core.api.{ApiUser, CreateAUser, JsonFormat, UpdateAUser}
import gitbucket.core.controller.ControllerBase
import gitbucket.core.service.{AccountService, RepositoryService}
import gitbucket.core.util.{AdminAuthenticator, UsersAuthenticator}
import gitbucket.core.util.Implicits._
import gitbucket.core.util.StringUtil._
import org.scalatra.NoContent

trait ApiUserControllerBase extends ControllerBase {
  self: RepositoryService with AccountService with AdminAuthenticator with UsersAuthenticator =>

  /**
   * i. Get a single user
   * https://developer.github.com/v3/users/#get-a-single-user
   * This API also returns group information (as GitHub).
   */
  get("/api/v3/users/:userName") {
    getAccountByUserName(params("userName")).map { account =>
      JsonFormat(ApiUser(account))
    } getOrElse NotFound()
  }

  /**
   * ii. Get the authenticated user
   * https://developer.github.com/v3/users/#get-the-authenticated-user
   */
  get("/api/v3/user") {
    context.loginAccount.map { account =>
      JsonFormat(ApiUser(account))
    } getOrElse Unauthorized()
  }

  /*
   * iii. Update the authenticated user
   * https://developer.github.com/v3/users/#update-the-authenticated-user
   */
  patch("/api/v3/user")(usersOnly {
    (for {
      data <- extractFromJsonBody[UpdateAUser]
    } yield {
      val loginAccount = context.loginAccount.get
      val updatedAccount = loginAccount.copy(
        mailAddress = data.email.getOrElse(loginAccount.mailAddress)
      )
      updateAccount(updatedAccount)
      JsonFormat(ApiUser(updatedAccount))
    })
  })

  /*
   * iv. Get contextual information about a user
   * https://developer.github.com/v3/users/#get-contextual-information-about-a-user
   */

  /*
   * v. Get all users
   * https://developer.github.com/v3/users/#get-all-users
   */
  get("/api/v3/users") {
    JsonFormat(getAllUsers(false, false).map(a => ApiUser(a)))
  }

  /*
   * ghe: i. Create a new user
   * https://developer.github.com/enterprise/2.14/v3/enterprise-admin/users/#create-a-new-user
   */
  post("/api/v3/admin/users")(adminOnly {
    for {
      data <- extractFromJsonBody[CreateAUser]
    } yield {
      val user = createAccount(
        data.login,
        pbkdf2_sha256(data.password),
        data.fullName.getOrElse(data.login),
        data.email,
        data.isAdmin.getOrElse(false),
        data.description,
        data.url
      )
      JsonFormat(ApiUser(user))
    }
  })

  /*
   * ghe: vii. Suspend a user
   * https://developer.github.com/enterprise/2.14/v3/enterprise-admin/users/#suspend-a-user
   */
  put("/api/v3/users/:userName/suspended")(adminOnly {
    val userName = params("userName")
    getAccountByUserName(userName) match {
      case Some(targetAccount) =>
        removeUserRelatedData(userName)
        updateAccount(targetAccount.copy(isRemoved = true))
        NoContent()
      case None =>
        NotFound()
    }
  })

  /*
   * ghe: vii. Unsuspend a user
   * https://developer.github.com/enterprise/2.14/v3/enterprise-admin/users/#unsuspend-a-user
   */
  delete("/api/v3/users/:userName/suspended")(adminOnly {
    val userName = params("userName")
    getAccountByUserName(userName) match {
      case Some(targetAccount) =>
        updateAccount(targetAccount.copy(isRemoved = false))
        NoContent()
      case None =>
        NotFound()
    }
  })
}
