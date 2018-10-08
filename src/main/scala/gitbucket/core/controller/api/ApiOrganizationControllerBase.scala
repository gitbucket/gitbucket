package gitbucket.core.controller.api
import gitbucket.core.api.{ApiRepository, ApiUser, JsonFormat}
import gitbucket.core.controller.ControllerBase
import gitbucket.core.service.{AccountService, RepositoryService}
import gitbucket.core.util.Implicits._

trait ApiOrganizationControllerBase extends ControllerBase {
  self: RepositoryService with AccountService =>

  /*
   * i. List your organizations
   * https://developer.github.com/v3/orgs/#list-your-organizations
   */

  /*
   * ii. List all organizations
   * https://developer.github.com/v3/orgs/#list-all-organizations
   */

  /*
   * iii. List user organizations
   * https://developer.github.com/v3/orgs/#list-user-organizations
   */

  /**
   * iv. Get an organization
   * https://developer.github.com/v3/orgs/#get-an-organization
   */
  get("/api/v3/orgs/:groupName") {
    getAccountByUserName(params("groupName")).filter(account => account.isGroupAccount).map { account =>
      JsonFormat(ApiUser(account))
    } getOrElse NotFound()
  }
}
