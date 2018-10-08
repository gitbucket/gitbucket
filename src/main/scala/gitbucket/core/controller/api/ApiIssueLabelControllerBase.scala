package gitbucket.core.controller.api
import gitbucket.core.api.{ApiError, ApiLabel, CreateALabel, JsonFormat}
import gitbucket.core.controller.ControllerBase
import gitbucket.core.service._
import gitbucket.core.util.Implicits._
import gitbucket.core.util._
import org.scalatra.{Created, NoContent, UnprocessableEntity}

trait ApiIssueLabelControllerBase extends ControllerBase {
  self: AccountService
    with IssuesService
    with LabelsService
    with ReferrerAuthenticator
    with WritableUsersAuthenticator =>

  /*
   * i. List all labels for this repository
   * https://developer.github.com/v3/issues/labels/#list-all-labels-for-this-repository
   */
  get("/api/v3/repos/:owner/:repository/labels")(referrersOnly { repository =>
    JsonFormat(getLabels(repository.owner, repository.name).map { label =>
      ApiLabel(label, RepositoryName(repository))
    })
  })

  /*
   * ii. Get a single label
   * https://developer.github.com/v3/issues/labels/#get-a-single-label
   */
  get("/api/v3/repos/:owner/:repository/labels/:labelName")(referrersOnly { repository =>
    getLabel(repository.owner, repository.name, params("labelName")).map { label =>
      JsonFormat(ApiLabel(label, RepositoryName(repository)))
    } getOrElse NotFound()
  })

  /*
   * iii. Create a label
   * https://developer.github.com/v3/issues/labels/#create-a-label
   */
  post("/api/v3/repos/:owner/:repository/labels")(writableUsersOnly { repository =>
    (for {
      data <- extractFromJsonBody[CreateALabel] if data.isValid
    } yield {
      LockUtil.lock(RepositoryName(repository).fullName) {
        if (getLabel(repository.owner, repository.name, data.name).isEmpty) {
          val labelId = createLabel(repository.owner, repository.name, data.name, data.color)
          getLabel(repository.owner, repository.name, labelId).map { label =>
            Created(JsonFormat(ApiLabel(label, RepositoryName(repository))))
          } getOrElse NotFound()
        } else {
          // TODO ApiError should support errors field to enhance compatibility of GitHub API
          UnprocessableEntity(
            ApiError(
              "Validation Failed",
              Some("https://developer.github.com/v3/issues/labels/#create-a-label")
            )
          )
        }
      }
    }) getOrElse NotFound()
  })

  /*
   * iv. Update a label
   * https://developer.github.com/v3/issues/labels/#update-a-label
   */
  patch("/api/v3/repos/:owner/:repository/labels/:labelName")(writableUsersOnly { repository =>
    (for {
      data <- extractFromJsonBody[CreateALabel] if data.isValid
    } yield {
      LockUtil.lock(RepositoryName(repository).fullName) {
        getLabel(repository.owner, repository.name, params("labelName")).map {
          label =>
            if (getLabel(repository.owner, repository.name, data.name).isEmpty) {
              updateLabel(repository.owner, repository.name, label.labelId, data.name, data.color)
              JsonFormat(
                ApiLabel(
                  getLabel(repository.owner, repository.name, label.labelId).get,
                  RepositoryName(repository)
                )
              )
            } else {
              // TODO ApiError should support errors field to enhance compatibility of GitHub API
              UnprocessableEntity(
                ApiError(
                  "Validation Failed",
                  Some("https://developer.github.com/v3/issues/labels/#create-a-label")
                )
              )
            }
        } getOrElse NotFound()
      }
    }) getOrElse NotFound()
  })

  /*
   * v. Delete a label
   * https://developer.github.com/v3/issues/labels/#delete-a-label
   */
  delete("/api/v3/repos/:owner/:repository/labels/:labelName")(writableUsersOnly { repository =>
    LockUtil.lock(RepositoryName(repository).fullName) {
      getLabel(repository.owner, repository.name, params("labelName")).map { label =>
        deleteLabel(repository.owner, repository.name, label.labelId)
        NoContent()
      } getOrElse NotFound()
    }
  })
  /*
   * vi. List labels on an issue
   * https://developer.github.com/v3/issues/labels/#list-labels-on-an-issue
   */

  /*
   * vii. Add labels to an issue
   * https://developer.github.com/v3/issues/labels/#add-labels-to-an-issue
   */

  /*
   * viii. Remove a label from an issue
   * https://developer.github.com/v3/issues/labels/#remove-a-label-from-an-issue
   */

  /*
   * ix. Replace all labels for an issue
   * https://developer.github.com/v3/issues/labels/#replace-all-labels-for-an-issue
   */

  /*
   * x. Remove all labels from an issue
   * https://developer.github.com/v3/issues/labels/#remove-all-labels-from-an-issue
   */

  /*
 * xi Get labels for every issue in a milestone
 * https://developer.github.com/v3/issues/labels/#get-labels-for-every-issue-in-a-milestone
 */
}
