package gitbucket.core.util

import gitbucket.core.model.{Session, Issue}
import gitbucket.core.service.{RepositoryService, AccountService, IssuesService, SystemSettingsService}
import gitbucket.core.servlet.Database
import gitbucket.core.view.Markdown

import scala.concurrent._
import ExecutionContext.Implicits.global
import org.apache.commons.mail.{DefaultAuthenticator, HtmlEmail}
import org.slf4j.LoggerFactory

import gitbucket.core.controller.Context
import SystemSettingsService.Smtp
import ControlUtil.defining

trait Notifier extends RepositoryService with AccountService with IssuesService {
  def toNotify(r: RepositoryService.RepositoryInfo, issue: Issue, content: String)
      (msg: String => String)(implicit context: Context): Unit

  protected def recipients(issue: Issue)(notify: String => Unit)(implicit session: Session, context: Context) =
    (
        // individual repository's owner
        issue.userName ::
        // collaborators
        getCollaborators(issue.userName, issue.repositoryName) :::
        // participants
        issue.openedUserName ::
        getComments(issue.userName, issue.repositoryName, issue.issueId).map(_.commentedUserName)
    )
    .distinct
    .withFilter ( _ != context.loginAccount.get.userName )  // the operation in person is excluded
    .foreach ( getAccountByUserName(_) filterNot (_.isGroupAccount) filterNot (LDAPUtil.isDummyMailAddress(_)) foreach (x => notify(x.mailAddress)) )

}

object Notifier {
  // TODO We want to be able to switch to mock.
  def apply(): Notifier = new SystemSettingsService {}.loadSystemSettings match {
    case settings if settings.notification => new Mailer(settings.smtp.get)
    case _ => new MockMailer
  }

  def msgIssue(url: String) = (content: String) => s"""
    |${content}<br/>
    |--<br/>
    |<a href="${url}">View it on GitBucket</a>
    """.stripMargin

  def msgPullRequest(url: String) = (content: String) => s"""
    |${content}<hr/>
    |View, comment on, or merge it at:<br/>
    |<a href="${url}">${url}</a>
    """.stripMargin

  def msgComment(url: String) = (content: String) => s"""
    |${content}<br/>
    |--<br/>
    |<a href="${url}">View it on GitBucket</a>
    """.stripMargin

  def msgStatus(url: String) = (content: String) => s"""
    |${content} <a href="${url}">#${url split('/') last}</a>
    """.stripMargin
}

class Mailer(private val smtp: Smtp) extends Notifier {
  private val logger = LoggerFactory.getLogger(classOf[Mailer])

  def toNotify(r: RepositoryService.RepositoryInfo, issue: Issue, content: String)
      (msg: String => String)(implicit context: Context) = {
    val database = Database()

    val f = Future {
      database withSession { implicit session =>
        defining(
          s"[${r.name}] ${issue.title} (#${issue.issueId})" ->
            msg(Markdown.toHtml(content, r, false, true, false))) { case (subject, msg) =>
            recipients(issue) { to =>
              val email = new HtmlEmail
              email.setHostName(smtp.host)
              email.setSmtpPort(smtp.port.get)
              smtp.user.foreach { user =>
                email.setAuthenticator(new DefaultAuthenticator(user, smtp.password.getOrElse("")))
              }
              smtp.ssl.foreach { ssl =>
                email.setSSLOnConnect(ssl)
              }
              smtp.fromAddress
                .map (_ -> smtp.fromName.getOrElse(context.loginAccount.get.userName))
                .orElse (Some("notifications@gitbucket.com" -> context.loginAccount.get.userName))
                .foreach { case (address, name) =>
                  email.setFrom(address, name)
              }
              email.setCharset("UTF-8")
              email.setSubject(subject)
              email.setHtmlMsg(msg)

              email.addTo(to).send
            }
        }
      }
      "Notifications Successful."
    }
    f onSuccess {
      case s => logger.debug(s)
    }
    f onFailure {
      case t => logger.error("Notifications Failed.", t)
    }
  }
}
class MockMailer extends Notifier {
  def toNotify(r: RepositoryService.RepositoryInfo, issue: Issue, content: String)
      (msg: String => String)(implicit context: Context): Unit = {}
}
