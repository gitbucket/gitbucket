package gitbucket.core.controller.api
import gitbucket.core.api.{ApiUser, JsonFormat}
import gitbucket.core.controller.ControllerBase
import gitbucket.core.service.{AccountService, RepositoryService}
import gitbucket.core.util.Implicits._

trait ApiUserControllerBase extends ControllerBase {
  self: RepositoryService with AccountService =>

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

}
