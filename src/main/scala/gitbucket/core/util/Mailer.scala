package gitbucket.core.util

import gitbucket.core.model.Account
import gitbucket.core.service.SystemSettingsService

import org.apache.commons.mail.{DefaultAuthenticator, HtmlEmail}
import SystemSettingsService.SystemSettings

class Mailer(settings: SystemSettings) {

  def send(
    to: String,
    subject: String,
    textMsg: String,
    htmlMsg: Option[String] = None,
    loginAccount: Option[Account] = None
  ): Unit = {
    createMail(subject, textMsg, htmlMsg, loginAccount).foreach { email =>
      email.addTo(to).send
    }
  }

  def sendBcc(
    bcc: Seq[String],
    subject: String,
    textMsg: String,
    htmlMsg: Option[String] = None,
    loginAccount: Option[Account] = None
  ): Unit = {
    createMail(subject, textMsg, htmlMsg, loginAccount).foreach { email =>
      bcc.foreach { address =>
        email.addBcc(address)
      }
      email.send()
    }
  }

  def createMail(
    subject: String,
    textMsg: String,
    htmlMsg: Option[String] = None,
    loginAccount: Option[Account] = None
  ): Option[HtmlEmail] = {
    if (settings.notification == true) {
      settings.smtp.map { smtp =>
        val email = new HtmlEmail
        email.setHostName(smtp.host)
        email.setSmtpPort(smtp.port.get)
        smtp.user.foreach { user =>
          email.setAuthenticator(new DefaultAuthenticator(user, smtp.password.getOrElse("")))
        }
        smtp.ssl.foreach { ssl =>
          email.setSSLOnConnect(ssl)
          if (ssl == true) {
            email.setSslSmtpPort(smtp.port.get.toString)
          }
        }
        smtp.starttls.foreach { starttls =>
          email.setStartTLSEnabled(starttls)
          email.setStartTLSRequired(starttls)
        }
        smtp.fromAddress
          .map(_ -> smtp.fromName.getOrElse(loginAccount.map(_.userName).getOrElse("GitBucket")))
          .orElse(Some("notifications@gitbucket.com" -> loginAccount.map(_.userName).getOrElse("GitBucket")))
          .foreach {
            case (address, name) =>
              email.setFrom(address, name)
          }
        email.setCharset("UTF-8")
        email.setSubject(subject)
        email.setTextMsg(textMsg)
        htmlMsg.foreach { msg =>
          email.setHtmlMsg(msg)
        }

        email
      }
    } else None
  }

}

//class MockMailer extends Notifier {
//  def toNotify(subject: String, textMsg: String, htmlMsg: Option[String] = None)
//              (recipients: Account => Session => Seq[String])(implicit context: Context): Unit = ()
//}
