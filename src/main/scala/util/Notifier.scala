package util

import org.apache.commons.mail.{DefaultAuthenticator, SimpleEmail}

import service.SystemSettingsService.{SystemSettings, Smtp}

trait Notifier {

}

object Notifier {
  def apply(settings: SystemSettings) = {
    new Mailer(settings.smtp.get)
  }

}

class Mailer(val smtp: Smtp) extends Notifier {
  def notifyTo(issue: model.Issue) = {
    val email = new SimpleEmail
    email.setHostName(smtp.host)
    email.setSmtpPort(smtp.port.get)
    smtp.user.foreach { user =>
      email.setAuthenticator(new DefaultAuthenticator(user, smtp.password.getOrElse("")))
    }
    smtp.ssl.foreach { ssl =>
      email.setSSLOnConnect(ssl)
    }
    email.setFrom("TODO address", "TODO　name")
    email.addTo("TODO")
    email.setSubject(s"[${issue.repositoryName}] ${issue.title} (#${issue.issueId})")
    email.setMsg("TODO")

    email.send
  }
}
class MockMailer extends Notifier