package gitbucket.core.util

import gitbucket.core.model.{Session, Issue, Account}
import gitbucket.core.model.Profile.profile.blockingApi._
import gitbucket.core.service.{RepositoryService, AccountService, IssuesService, SystemSettingsService}
import gitbucket.core.servlet.Database
import gitbucket.core.view.Markdown

import scala.concurrent._
import scala.util.{Success, Failure}
import ExecutionContext.Implicits.global
import org.apache.commons.mail.{DefaultAuthenticator, HtmlEmail}
import org.slf4j.LoggerFactory
import gitbucket.core.controller.Context
import SystemSettingsService.Smtp

/**
 * The trait for notifications.
 * This is used by notifications plugin, which provides notifications feature on GitBucket.
 * Please see the plugin for details.
 */
trait Notifier {

  def toNotify(subject: String, msg: String)
              (recipients: Account => Session => Seq[String])(implicit context: Context): Unit

}

object Notifier {
  def apply(): Notifier = new SystemSettingsService {}.loadSystemSettings match {
    case settings if (settings.notification && settings.useSMTP) => new Mailer(settings.smtp.get)
    case _ => new MockMailer
  }


  // TODO This class is temporary keeping the current feature until Notifications Plugin is available.
  class IssueHook extends gitbucket.core.plugin.IssueHook
    with RepositoryService with AccountService with IssuesService {

    override def created(issue: Issue, r: RepositoryService.RepositoryInfo)(implicit context: Context): Unit = {
      Notifier().toNotify(
        subject(issue, r),
        message(issue.content getOrElse "", r)(content => s"""
          |$content<br/>
          |--<br/>
          |<a href="${s"${context.baseUrl}/${r.owner}/${r.name}/issues/${issue.issueId}"}">View it on GitBucket</a>
          """.stripMargin)
      )(recipients(issue))
    }

    override def addedComment(commentId: Int, content: String, issue: Issue, r: RepositoryService.RepositoryInfo)(implicit context: Context): Unit = {
      Notifier().toNotify(
        subject(issue, r),
        message(content, r)(content => s"""
          |$content<br/>
          |--<br/>
          |<a href="${s"${context.baseUrl}/${r.owner}/${r.name}/issues/${issue.issueId}#comment-$commentId"}">View it on GitBucket</a>
          """.stripMargin)
      )(recipients(issue))
    }

    override def closed(issue: Issue, r: RepositoryService.RepositoryInfo)(implicit context: Context): Unit = {
      Notifier().toNotify(
        subject(issue, r),
        message("close", r)(content => s"""
          |$content <a href="${s"${context.baseUrl}/${r.owner}/${r.name}/issues/${issue.issueId}"}">#${issue.issueId}</a>
          """.stripMargin)
      )(recipients(issue))
    }

    override def reopened(issue: Issue, r: RepositoryService.RepositoryInfo)(implicit context: Context): Unit = {
      Notifier().toNotify(
        subject(issue, r),
        message("reopen", r)(content => s"""
          |$content <a href="${s"${context.baseUrl}/${r.owner}/${r.name}/issues/${issue.issueId}"}">#${issue.issueId}</a>
          """.stripMargin)
      )(recipients(issue))
    }


    protected def subject(issue: Issue, r: RepositoryService.RepositoryInfo): String =
      s"[${r.owner}/${r.name}] ${issue.title} (#${issue.issueId})"

    protected def message(content: String, r: RepositoryService.RepositoryInfo)(msg: String => String)(implicit context: Context): String =
      msg(Markdown.toHtml(
        markdown         = content,
        repository       = r,
        enableWikiLink   = false,
        enableRefsLink   = true,
        enableAnchor     = false,
        enableLineBreaks = false
      ))

    protected val recipients: Issue => Account => Session => Seq[String] = {
      issue => loginAccount => implicit session =>
        (
          // individual repository's owner
          issue.userName ::
          // group members of group repository
          getGroupMembers(issue.userName).map(_.userName) :::
          // collaborators
          getCollaboratorUserNames(issue.userName, issue.repositoryName) :::
          // participants
          issue.openedUserName ::
          getComments(issue.userName, issue.repositoryName, issue.issueId).map(_.commentedUserName)
        )
        .distinct
        .withFilter ( _ != loginAccount.userName )  // the operation in person is excluded
        .flatMap (
          getAccountByUserName(_)
            .filterNot (_.isGroupAccount)
            .filterNot (LDAPUtil.isDummyMailAddress)
            .map (_.mailAddress)
        )
    }
  }

  // TODO This class is temporary keeping the current feature until Notifications Plugin is available.
  class PullRequestHook extends IssueHook with gitbucket.core.plugin.PullRequestHook {
    override def created(issue: Issue, r: RepositoryService.RepositoryInfo)(implicit context: Context): Unit = {
      val url = s"${context.baseUrl}/${r.owner}/${r.name}/pull/${issue.issueId}"
      Notifier().toNotify(
        subject(issue, r),
        message(issue.content getOrElse "", r)(content => s"""
          |$content<hr/>
          |View, comment on, or merge it at:<br/>
          |<a href="$url">$url</a>
          """.stripMargin)
      )(recipients(issue))
    }

    override def addedComment(commentId: Int, content: String, issue: Issue, r: RepositoryService.RepositoryInfo)(implicit context: Context): Unit = {
      Notifier().toNotify(
        subject(issue, r),
        message(content, r)(content => s"""
          |$content<br/>
          |--<br/>
          |<a href="${s"${context.baseUrl}/${r.owner}/${r.name}/pull/${issue.issueId}#comment-$commentId"}">View it on GitBucket</a>
          """.stripMargin)
      )(recipients(issue))
    }

    override def merged(issue: Issue, r: RepositoryService.RepositoryInfo)(implicit context: Context): Unit = {
      Notifier().toNotify(
        subject(issue, r),
        message("merge", r)(content => s"""
          |$content <a href="${s"${context.baseUrl}/${r.owner}/${r.name}/pull/${issue.issueId}"}">#${issue.issueId}</a>
          """.stripMargin)
      )(recipients(issue))
    }
  }

}

class Mailer(private val smtp: Smtp) extends Notifier {
  private val logger = LoggerFactory.getLogger(classOf[Mailer])

  def toNotify(subject: String, msg: String)
              (recipients: Account => Session => Seq[String])(implicit context: Context): Unit = {
    context.loginAccount.foreach { loginAccount =>
      val database = Database()

      val f = Future {
        database withSession { session =>
          recipients(loginAccount)(session) foreach { to =>
            send(to, subject, msg, loginAccount)
          }
        }
        "Notifications Successful."
      }
      f.onComplete {
        case Success(s) => logger.debug(s)
        case Failure(t) => logger.error("Notifications Failed.", t)
      }
    }
  }

  def send(to: String, subject: String, msg: String, loginAccount: Account): Unit = {
    val email = new HtmlEmail
    email.setHostName(smtp.host)
    email.setSmtpPort(smtp.port.get)
    smtp.user.foreach { user =>
      email.setAuthenticator(new DefaultAuthenticator(user, smtp.password.getOrElse("")))
    }
    smtp.ssl.foreach { ssl =>
      email.setSSLOnConnect(ssl)
      if(ssl == true) {
        email.setSslSmtpPort(smtp.port.get.toString)
      }
    }
    smtp.starttls.foreach { starttls =>
      email.setStartTLSEnabled(starttls)
      email.setStartTLSRequired(starttls)
    }
    smtp.fromAddress
      .map (_ -> smtp.fromName.getOrElse(loginAccount.userName))
      .orElse (Some("notifications@gitbucket.com" -> loginAccount.userName))
      .foreach { case (address, name) =>
        email.setFrom(address, name)
      }
    email.setCharset("UTF-8")
    email.setSubject(subject)
    email.setHtmlMsg(msg)

    email.addTo(to).send
  }

}
class MockMailer extends Notifier {
  def toNotify(subject: String, msg: String)
              (recipients: Account => Session => Seq[String])(implicit context: Context): Unit = ()
}
