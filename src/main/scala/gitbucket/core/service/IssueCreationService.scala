package gitbucket.core.service

import gitbucket.core.controller.Context
import gitbucket.core.model.{Account, Issue}
import gitbucket.core.model.Profile.profile.simple.Session
import gitbucket.core.service.RepositoryService.RepositoryInfo
import gitbucket.core.util.Notifier
import gitbucket.core.util.Implicits._

// TODO: Merged with IssuesService?
trait IssueCreationService {

  self: RepositoryService with WebHookIssueCommentService with LabelsService with IssuesService with ActivityService =>

  def createIssue(repository: RepositoryInfo, title:String, body:Option[String],
                  assignee: Option[String], milestoneId: Option[Int], labelNames: Seq[String],
                  loginAccount: Account)(implicit context: Context, s: Session) : Issue = {

    val owner = repository.owner
    val name  = repository.name
    val userName   = loginAccount.userName
    val manageable = isManageable(repository)

    // insert issue
    val issueId = insertIssue(owner, name, userName, title, body,
      if (manageable) assignee else None,
      if (manageable) milestoneId else None)
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
    callIssuesWebHook("opened", repository, issue, context.baseUrl, loginAccount)

    // notifications
    Notifier().toNotify(repository, issue, body.getOrElse("")) {
      Notifier.msgIssue(s"${context.baseUrl}/${owner}/${name}/issues/${issueId}")
    }
    issue
  }

  /**
    * Tests whether an logged-in user can manage issues.
    */
  protected def isManageable(repository: RepositoryInfo)(implicit context: Context, s: Session): Boolean = {
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
