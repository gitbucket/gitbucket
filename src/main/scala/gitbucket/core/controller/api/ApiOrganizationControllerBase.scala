package gitbucket.core.controller.api
import gitbucket.core.api.{ApiGroup, CreateAGroup, ApiRepository, ApiUser, JsonFormat}
import gitbucket.core.controller.ControllerBase
import gitbucket.core.service.{AccountService, RepositoryService}
import gitbucket.core.util.Implicits._
import gitbucket.core.util.{AdminAuthenticator, UsersAuthenticator}

trait ApiOrganizationControllerBase extends ControllerBase {
  self: RepositoryService with AccountService with AdminAuthenticator with UsersAuthenticator =>

  /*
   * i. List your organizations
   * https://developer.github.com/v3/orgs/#list-your-organizations
   */
  get("/api/v3/user/orgs")(usersOnly {
    JsonFormat(getGroupsByUserName(context.loginAccount.get.userName).flatMap(getAccountByUserName(_)).map(ApiGroup(_)))
  })

  /*
   * ii. List all organizations
   * https://developer.github.com/v3/orgs/#list-all-organizations
   */
  get("/api/v3/organizations") {
    JsonFormat(getAllUsers(false, true).filter(a => a.isGroupAccount).map(ApiGroup(_)))
  }

  /*
   * iii. List user organizations
   * https://developer.github.com/v3/orgs/#list-user-organizations
   */
  get("/api/v3/users/:userName/orgs") {
    JsonFormat(getGroupsByUserName(params("userName")).flatMap(getAccountByUserName(_)).map(ApiGroup(_)))
  }

  /**
   * iv. Get an organization
   * https://developer.github.com/v3/orgs/#get-an-organization
   */
  get("/api/v3/orgs/:groupName") {
    getAccountByUserName(params("groupName")).filter(account => account.isGroupAccount).map { account =>
      JsonFormat(ApiGroup(account))
    } getOrElse NotFound()
  }

  /*
   * v. Edit an organization
   * https://developer.github.com/v3/orgs/#edit-an-organization
   */

  /*
   * ghe: i. Create an organization
   * https://developer.github.com/enterprise/2.14/v3/enterprise-admin/orgs/#create-an-organization
   */
  post("/api/v3/admin/organizations")(adminOnly {
    for {
      data <- extractFromJsonBody[CreateAGroup]
    } yield {
      val group = createGroup(
        data.login,
        data.profile_name,
        data.url
      )
      updateGroupMembers(data.login, List(data.admin -> true))
      JsonFormat(ApiGroup(group))
    }
  })

  /*
   * ghe: ii. Rename an organization
   * https://developer.github.com/enterprise/2.14/v3/enterprise-admin/orgs/#rename-an-organization
   */

  /*
 * should implement delete an organization API?
 */
}
