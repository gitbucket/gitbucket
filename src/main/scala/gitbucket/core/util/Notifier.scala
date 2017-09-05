package gitbucket.core.util

import gitbucket.core.model.{Session, Account}
import gitbucket.core.model.Profile.profile.blockingApi._
import gitbucket.core.service.SystemSettingsService
import gitbucket.core.servlet.Database

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
  def toNotify(subject: String, textMsg: String)
    (recipients: Account => Session => Seq[String])(implicit context: Context): Unit = {
    toNotify(subject, textMsg, None)(recipients)
  }

  def toNotify(subject: String, textMsg: String, htmlMsg: Option[String])
              (recipients: Account => Session => Seq[String])(implicit context: Context): Unit

}

object Notifier {
  def apply(): Notifier = new SystemSettingsService {}.loadSystemSettings match {
    case settings if (settings.notification && settings.useSMTP) => new Mailer(settings.smtp.get)
    case _ => new MockMailer
  }
}

class Mailer(private val smtp: Smtp) extends Notifier {
  private val logger = LoggerFactory.getLogger(classOf[Mailer])

  def toNotify(subject: String, textMsg: String, htmlMsg: Option[String] = None)
              (recipients: Account => Session => Seq[String])(implicit context: Context): Unit = {
    context.loginAccount.foreach { loginAccount =>
      val database = Database()

      val f = Future {
        database withSession { session =>
          recipients(loginAccount)(session) foreach { to =>
            send(to, subject, loginAccount, textMsg, htmlMsg)
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

  def send(to: String, subject: String, loginAccount: Account, textMsg: String, htmlMsg: Option[String] = None): Unit = {
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
    email.setTextMsg(textMsg)
    htmlMsg.foreach { msg =>
      email.setHtmlMsg(msg)
    }

    email.addTo(to).send
  }

}
class MockMailer extends Notifier {
  def toNotify(subject: String, textMsg: String, htmlMsg: Option[String] = None)
              (recipients: Account => Session => Seq[String])(implicit context: Context): Unit = ()
}
