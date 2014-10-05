package service

import util.Directory._
import util.ControlUtil._
import SystemSettingsService._
import javax.servlet.http.HttpServletRequest

trait SystemSettingsService {

  def baseUrl(implicit request: HttpServletRequest): String = loadSystemSettings().baseUrl(request)

  def saveSystemSettings(settings: SystemSettings): Unit = {
    defining(new java.util.Properties()){ props =>
      settings.baseUrl.foreach(x => props.setProperty(BaseURL, x.replaceFirst("/\\Z", "")))
      props.setProperty(AllowAccountRegistration, settings.allowAccountRegistration.toString)
      props.setProperty(Gravatar, settings.gravatar.toString)
      props.setProperty(Notification, settings.notification.toString)
      props.setProperty(Ssh, settings.ssh.toString)
      settings.sshPort.foreach(x => props.setProperty(SshPort, x.toString))
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
          ldap.additionalFilterCondition.foreach(x => props.setProperty(LdapAdditionalFilterCondition, x))
          ldap.fullNameAttribute.foreach(x => props.setProperty(LdapFullNameAttribute, x))
          ldap.mailAttribute.foreach(x => props.setProperty(LdapMailAddressAttribute, x.toString))
          ldap.tls.foreach(x => props.setProperty(LdapTls, x.toString))
          ldap.keystore.foreach(x => props.setProperty(LdapKeystore, x))
        }
      }
      using(new java.io.FileOutputStream(GitBucketConf)){ out =>
        props.store(out, null)
      }
    }
  }


  def loadSystemSettings(): SystemSettings = {
    defining(new java.util.Properties()){ props =>
      if(GitBucketConf.exists){
        using(new java.io.FileInputStream(GitBucketConf)){ in =>
          props.load(in)
        }
      }
      SystemSettings(
        getOptionValue[String](props, BaseURL, None).map(x => x.replaceFirst("/\\Z", "")),
        getValue(props, AllowAccountRegistration, false),
        getValue(props, Gravatar, true),
        getValue(props, Notification, false),
        getValue(props, Ssh, false),
        getOptionValue(props, SshPort, Some(DefaultSshPort)),
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
            getOptionValue(props, LdapAdditionalFilterCondition, None),
            getOptionValue(props, LdapFullNameAttribute, None),
            getOptionValue(props, LdapMailAddressAttribute, None),
            getOptionValue[Boolean](props, LdapTls, None),
            getOptionValue(props, LdapKeystore, None)))
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
    baseUrl: Option[String],
    allowAccountRegistration: Boolean,
    gravatar: Boolean,
    notification: Boolean,
    ssh: Boolean,
    sshPort: Option[Int],
    smtp: Option[Smtp],
    ldapAuthentication: Boolean,
    ldap: Option[Ldap]){
    def baseUrl(request: HttpServletRequest): String = baseUrl.getOrElse {
      defining(request.getRequestURL.toString){ url =>
        url.substring(0, url.length - (request.getRequestURI.length - request.getContextPath.length))
      }
    }.stripSuffix("/")
  }

  case class Ldap(
    host: String,
    port: Option[Int],
    bindDN: Option[String],
    bindPassword: Option[String],
    baseDN: String,
    userNameAttribute: String,
    additionalFilterCondition: Option[String],
    fullNameAttribute: Option[String],
    mailAttribute: Option[String],
    tls: Option[Boolean],
    keystore: Option[String])

  case class Smtp(
    host: String,
    port: Option[Int],
    user: Option[String],
    password: Option[String],
    ssl: Option[Boolean],
    fromAddress: Option[String],
    fromName: Option[String])

  val DefaultSshPort = 29418
  val DefaultSmtpPort = 25
  val DefaultLdapPort = 389

  private val BaseURL = "base_url"
  private val AllowAccountRegistration = "allow_account_registration"
  private val Gravatar = "gravatar"
  private val Notification = "notification"
  private val Ssh = "ssh"
  private val SshPort = "ssh.port"
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
  private val LdapAdditionalFilterCondition = "ldap.additional_filter_condition"
  private val LdapFullNameAttribute = "ldap.fullname_attribute"
  private val LdapMailAddressAttribute = "ldap.mail_attribute"
  private val LdapTls = "ldap.tls"
  private val LdapKeystore = "ldap.keystore"

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

  // TODO temporary flag
  val enablePluginSystem = Option(System.getProperty("enable.plugin")).getOrElse("false").toBoolean

}
