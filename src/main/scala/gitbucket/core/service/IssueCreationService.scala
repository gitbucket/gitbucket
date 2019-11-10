package gitbucket.core.service

import gitbucket.core.controller.Context
import gitbucket.core.model.{Account, Issue}
import gitbucket.core.model.Profile.profile.blockingApi._
import gitbucket.core.plugin.PluginRegistry
import gitbucket.core.service.RepositoryService.RepositoryInfo
import gitbucket.core.util.Implicits._

trait IssueCreationService {

  self: RepositoryService with WebHookIssueCommentService with LabelsService with IssuesService with ActivityService =>

  def createIssue(
    repository: RepositoryInfo,
    title: String,
    body: Option[String],
    assignee: Option[String],
    milestoneId: Option[Int],
    priorityId: Option[Int],
    labelNames: Seq[String],
    loginAccount: Account
  )(implicit context: Context, s: Session): Issue = {

    val owner = repository.owner
    val name = repository.name
    val userName = loginAccount.userName
    val manageable = isIssueManageable(repository)

    // insert issue
    val issueId = insertIssue(
      owner,
      name,
      userName,
      title,
      body,
      if (manageable) assignee else None,
      if (manageable) milestoneId else None,
      if (manageable) priorityId else None
    )
    val issue: Issue = getIssue(owner, name, issueId.toString).get

    // insert labels
    if (manageable) {
      val labels = getLabels(owner, name)
      labelNames.map { labelName =>
        labels.find(_.labelName == labelName).map { label =>
          registerIssueLabel(owner, name, issueId, label.labelId)
        }
      }
    }

    // record activity
    recordCreateIssueActivity(owner, name, userName, issueId, title)

    // extract references and create refer comment
    createReferComment(owner, name, issue, title + " " + body.getOrElse(""), loginAccount)

    // call web hooks
    callIssuesWebHook("opened", repository, issue, loginAccount, context.settings)

    // call hooks
    PluginRegistry().getIssueHooks.foreach(_.created(issue, repository))

    issue
  }

  /**
   * Tests whether an logged-in user can manage issues.
   */
  protected def isIssueManageable(repository: RepositoryInfo)(implicit context: Context, s: Session): Boolean = {
    hasDeveloperRole(repository.owner, repository.name, context.loginAccount)
  }

  /**
   * Tests whether an logged-in user can post issues.
   */
  protected def isIssueEditable(repository: RepositoryInfo)(implicit context: Context, s: Session): Boolean = {
    repository.repository.options.issuesOption match {
      case "ALL"     => !repository.repository.isPrivate && context.loginAccount.isDefined
      case "PUBLIC"  => hasGuestRole(repository.owner, repository.name, context.loginAccount)
      case "PRIVATE" => hasDeveloperRole(repository.owner, repository.name, context.loginAccount)
      case "DISABLE" => false
    }
  }
}
