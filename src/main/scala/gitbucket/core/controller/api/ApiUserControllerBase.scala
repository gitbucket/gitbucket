package gitbucket.core.controller.api
import gitbucket.core.api.{ApiUser, CreateAUser, JsonFormat}
import gitbucket.core.controller.ControllerBase
import gitbucket.core.service.{AccountService, RepositoryService}
import gitbucket.core.util.AdminAuthenticator
import gitbucket.core.util.Implicits._

trait ApiUserControllerBase extends ControllerBase {
  self: RepositoryService with AccountService with AdminAuthenticator =>

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

  /*
   * iv. Get contextual information about a user
   * https://developer.github.com/v3/users/#get-contextual-information-about-a-user
   */

  /*
   * v. Get all users
   * https://developer.github.com/v3/users/#get-all-users
   */

  /*
   * ghe: i. Create a new use
   * https://developer.github.com/enterprise/2.14/v3/enterprise-admin/users/#create-a-new-user
   */
  post("/api/v3/admin/users")(adminOnly {
    for {
      data <- extractFromJsonBody[CreateAUser]
    } yield {
      val user = createAccount(
        data.login,
        data.password,
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
  /*
 * ghe: vii. Unsuspend a user
 * https://developer.github.com/enterprise/2.14/v3/enterprise-admin/users/#unsuspend-a-user
 */
}
