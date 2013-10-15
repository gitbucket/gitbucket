package service

import util.Directory._
import util.ControlUtil._
import SystemSettingsService._

trait SystemSettingsService {

  def saveSystemSettings(settings: SystemSettings): Unit = {
    defining(new java.util.Properties()){ props =>
      props.setProperty(AllowAccountRegistration, settings.allowAccountRegistration.toString)
      props.setProperty(Gravatar, settings.gravatar.toString)
      props.setProperty(Notification, settings.notification.toString)
      if(settings.notification) {
        settings.smtp.foreach { smtp =>
          props.setProperty(SmtpHost, smtp.host)
          smtp.port.foreach(x => props.setProperty(SmtpPort, x.toString))
          smtp.user.foreach(props.setProperty(SmtpUser, _))
          smtp.password.foreach(props.setProperty(SmtpPassword, _))
          smtp.ssl.foreach(x => props.setProperty(SmtpSsl, x.toString))
          smtp.fromAddress.foreach(props.setProperty(SmtpFromAddress, _))
          smtp.fromName.foreach(props.setProperty(SmtpFromName, _))
        }
      }
      props.setProperty(LdapAuthentication, settings.ldapAuthentication.toString)
      if(settings.ldapAuthentication){
        settings.ldap.map { ldap =>
          props.setProperty(LdapHost, ldap.host)
          ldap.port.foreach(x => props.setProperty(LdapPort, x.toString))
          ldap.bindDN.foreach(x => props.setProperty(LdapBindDN, x))
          ldap.bindPassword.foreach(x => props.setProperty(LdapBindPassword, x))
          props.setProperty(LdapBaseDN, ldap.baseDN)
          props.setProperty(LdapUserNameAttribute, ldap.userNameAttribute)
          props.setProperty(LdapMailAddressAttribute, ldap.mailAttribute)
        }
      }
      props.store(new java.io.FileOutputStream(GitBucketConf), null)
    }
  }


  def loadSystemSettings(): SystemSettings = {
    defining(new java.util.Properties()){ props =>
      if(GitBucketConf.exists){
        props.load(new java.io.FileInputStream(GitBucketConf))
      }
      SystemSettings(
        getValue(props, AllowAccountRegistration, false),
        getValue(props, Gravatar, true),
        getValue(props, Notification, false),
        if(getValue(props, Notification, false)){
          Some(Smtp(
            getValue(props, SmtpHost, ""),
            getOptionValue(props, SmtpPort, Some(DefaultSmtpPort)),
            getOptionValue(props, SmtpUser, None),
            getOptionValue(props, SmtpPassword, None),
            getOptionValue[Boolean](props, SmtpSsl, None),
            getOptionValue(props, SmtpFromAddress, None),
            getOptionValue(props, SmtpFromName, None)))
        } else {
          None
        },
        getValue(props, LdapAuthentication, false),
        if(getValue(props, LdapAuthentication, false)){
          Some(Ldap(
            getValue(props, LdapHost, ""),
            getOptionValue(props, LdapPort, Some(DefaultLdapPort)),
            getOptionValue(props, LdapBindDN, None),
            getOptionValue(props, LdapBindPassword, None),
            getValue(props, LdapBaseDN, ""),
            getValue(props, LdapUserNameAttribute, ""),
            getValue(props, LdapMailAddressAttribute, "")))
        } else {
          None
        }
      )
    }
  }

}

object SystemSettingsService {
  import scala.reflect.ClassTag

  case class SystemSettings(
    allowAccountRegistration: Boolean,
    gravatar: Boolean,
    notification: Boolean,
    smtp: Option[Smtp],
    ldapAuthentication: Boolean,
    ldap: Option[Ldap])

  case class Ldap(
    host: String,
    port: Option[Int],
    bindDN: Option[String],
    bindPassword: Option[String],
    baseDN: String,
    userNameAttribute: String,
    mailAttribute: String)

  case class Smtp(
    host: String,
    port: Option[Int],
    user: Option[String],
    password: Option[String],
    ssl: Option[Boolean],
    fromAddress: Option[String],
    fromName: Option[String])

  val DefaultSmtpPort = 25
  val DefaultLdapPort = 389

  private val AllowAccountRegistration = "allow_account_registration"
  private val Gravatar = "gravatar"
  private val Notification = "notification"
  private val SmtpHost = "smtp.host"
  private val SmtpPort = "smtp.port"
  private val SmtpUser = "smtp.user"
  private val SmtpPassword = "smtp.password"
  private val SmtpSsl = "smtp.ssl"
  private val SmtpFromAddress = "smtp.from_address"
  private val SmtpFromName = "smtp.from_name"
  private val LdapAuthentication = "ldap_authentication"
  private val LdapHost = "ldap.host"
  private val LdapPort = "ldap.port"
  private val LdapBindDN = "ldap.bindDN"
  private val LdapBindPassword = "ldap.bind_password"
  private val LdapBaseDN = "ldap.baseDN"
  private val LdapUserNameAttribute = "ldap.username_attribute"
  private val LdapMailAddressAttribute = "ldap.mail_attribute"

  private def getValue[A: ClassTag](props: java.util.Properties, key: String, default: A): A =
    defining(props.getProperty(key)){ value =>
      if(value == null || value.isEmpty) default
      else convertType(value).asInstanceOf[A]
    }

  private def getOptionValue[A: ClassTag](props: java.util.Properties, key: String, default: Option[A]): Option[A] =
    defining(props.getProperty(key)){ value =>
      if(value == null || value.isEmpty) default
      else Some(convertType(value)).asInstanceOf[Option[A]]
    }

  private def convertType[A: ClassTag](value: String) =
    defining(implicitly[ClassTag[A]].runtimeClass){ c =>
      if(c == classOf[Boolean])  value.toBoolean
      else if(c == classOf[Int]) value.toInt
      else value
    }

}
