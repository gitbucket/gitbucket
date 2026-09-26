package gitbucket.core.controller.api
import gitbucket.core.api.{ApiError, ApiLabel, CreateALabel, JsonFormat}
import gitbucket.core.controller.ControllerBase
import gitbucket.core.service._
import gitbucket.core.util.Implicits._
import gitbucket.core.util._
import org.json4s.{JArray, JObject, JString, JValue}
import org.scalatra.{BadRequest, Created, NoContent, UnprocessableEntity}

trait ApiIssueLabelControllerBase extends ControllerBase {
  self: AccountService & IssuesService & LabelsService & ReferrerAuthenticator & WritableUsersAuthenticator =>

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
        getLabel(repository.owner, repository.name, params("labelName")).map { label =>
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
  get("/api/v3/repos/:owner/:repository/issues/:id/labels")(referrersOnly { repository =>
    JsonFormat(getIssueLabels(repository.owner, repository.name, params("id").toInt).map { l =>
      ApiLabel(l, RepositoryName(repository.owner, repository.name))
    })
  })

  /*
   * vii. Add labels to an issue
   * https://developer.github.com/v3/issues/labels/#add-labels-to-an-issue
   */
  post("/api/v3/repos/:owner/:repository/issues/:id/labels")(writableUsersOnly { repository =>
    (for {
      labels <- extractLabelNames()
      issueId <- params("id").toIntOpt
    } yield JsonFormat {
      labels.map { labelName =>
        val label = getLabel(repository.owner, repository.name, labelName).getOrElse(
          getLabel(
            repository.owner,
            repository.name,
            createLabel(repository.owner, repository.name, labelName)
          ).get
        )
        registerIssueLabel(repository.owner, repository.name, issueId, label.labelId, true)
        ApiLabel(label, RepositoryName(repository.owner, repository.name))
      }
    }) getOrElse BadRequest()
  })

  /*
   * viii. Remove a label from an issue
   * https://developer.github.com/v3/issues/labels/#remove-a-label-from-an-issue
   */
  delete("/api/v3/repos/:owner/:repository/issues/:id/labels/:name")(writableUsersOnly { repository =>
    val issueId = params("id").toInt
    val labelName = params("name")
    getLabel(repository.owner, repository.name, labelName) match {
      case Some(label) =>
        deleteIssueLabel(repository.owner, repository.name, issueId, label.labelId, true)
        JsonFormat(Seq(label))
      case None =>
        NotFound()
    }
  })

  /*
   * ix. Replace all labels for an issue
   * https://developer.github.com/v3/issues/labels/#replace-all-labels-for-an-issue
   */
  put("/api/v3/repos/:owner/:repository/issues/:id/labels")(writableUsersOnly { repository =>
    (for {
      labels <- extractLabelNames()
      issueId <- params("id").toIntOpt
    } yield JsonFormat {
      deleteAllIssueLabels(repository.owner, repository.name, issueId, true)
      labels.map { labelName =>
        val label = getLabel(repository.owner, repository.name, labelName).getOrElse(
          getLabel(
            repository.owner,
            repository.name,
            createLabel(repository.owner, repository.name, labelName)
          ).get
        )
        registerIssueLabel(repository.owner, repository.name, issueId, label.labelId, true)
        ApiLabel(label, RepositoryName(repository.owner, repository.name))
      }
    }) getOrElse BadRequest()
  })

  /*
   * x. Remove all labels from an issue
   * https://developer.github.com/v3/issues/labels/#remove-all-labels-from-an-issue
   */
  delete("/api/v3/repos/:owner/:repository/issues/:id/labels")(writableUsersOnly { repository =>
    val issueId = params("id").toInt
    deleteAllIssueLabels(repository.owner, repository.name, issueId, true)
    NoContent()
  })

  /*
   * xi Get labels for every issue in a milestone
   * https://developer.github.com/v3/issues/labels/#get-labels-for-every-issue-in-a-milestone
   */

  /**
   * Returns the label names of the request body in the documented forms: {"labels":["bug"]}, ["bug"] and "bug".
   * Any other body returns None, so that it is rejected rather than read as an empty list: for PUT, an empty list
   * removes all labels of the issue. The raw JSON is matched because json4s extracts almost any JSON into a case
   * class or a Seq without failing (e.g. {} into AddLabelsToAnIssue(Nil)).
   */
  private def extractLabelNames(): Option[Seq[String]] = {
    def names(values: List[JValue]): Option[Seq[String]] = {
      val strings = values.collect { case JString(name) => name }
      Option.when(strings.size == values.size)(strings)
    }
    extractFromJsonBody[JValue].flatMap {
      case JArray(values)  => names(values)
      case JObject(fields) => fields.collectFirst { case ("labels", JArray(values)) => values }.flatMap(names)
      case JString(name)   => Some(Seq(name))
      case _               => None
    }
  }
}
