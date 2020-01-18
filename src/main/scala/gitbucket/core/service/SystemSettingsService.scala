package gitbucket.core.service

import javax.servlet.http.HttpServletRequest
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.oauth2.sdk.auth.Secret
import com.nimbusds.oauth2.sdk.id.{ClientID, Issuer}
import gitbucket.core.service.SystemSettingsService._
import gitbucket.core.util.ConfigUtil._
import gitbucket.core.util.Directory._
import gitbucket.core.util.SyntaxSugars._
import scala.util.Using

trait SystemSettingsService {

  def baseUrl(implicit request: HttpServletRequest): String = loadSystemSettings().baseUrl(request)

  def saveSystemSettings(settings: SystemSettings): Unit = {
    defining(new java.util.Properties()) { props =>
      settings.baseUrl.foreach(x => props.setProperty(BaseURL, x.replaceFirst("/\\Z", "")))
      settings.information.foreach(x => props.setProperty(Information, x))
      props.setProperty(AllowAccountRegistration, settings.allowAccountRegistration.toString)
      props.setProperty(AllowAnonymousAccess, settings.allowAnonymousAccess.toString)
      props.setProperty(IsCreateRepoOptionPublic, settings.isCreateRepoOptionPublic.toString)
      props.setProperty(Gravatar, settings.gravatar.toString)
      props.setProperty(Notification, settings.notification.toString)
      settings.activityLogLimit.foreach(x => props.setProperty(ActivityLogLimit, x.toString))
      props.setProperty(LimitVisibleRepositories, settings.limitVisibleRepositories.toString)
      props.setProperty(SshEnabled, settings.ssh.enabled.toString)
      settings.ssh.sshHost.foreach(x => props.setProperty(SshHost, x.trim))
      settings.ssh.sshPort.foreach(x => props.setProperty(SshPort, x.toString))
      props.setProperty(UseSMTP, settings.useSMTP.toString)
      if (settings.useSMTP) {
        settings.smtp.foreach { smtp =>
          props.setProperty(SmtpHost, smtp.host)
          smtp.port.foreach(x => props.setProperty(SmtpPort, x.toString))
          smtp.user.foreach(props.setProperty(SmtpUser, _))
          smtp.password.foreach(props.setProperty(SmtpPassword, _))
          smtp.ssl.foreach(x => props.setProperty(SmtpSsl, x.toString))
          smtp.starttls.foreach(x => props.setProperty(SmtpStarttls, x.toString))
          smtp.fromAddress.foreach(props.setProperty(SmtpFromAddress, _))
          smtp.fromName.foreach(props.setProperty(SmtpFromName, _))
        }
      }
      props.setProperty(LdapAuthentication, settings.ldapAuthentication.toString)
      if (settings.ldapAuthentication) {
        settings.ldap.foreach { ldap =>
          props.setProperty(LdapHost, ldap.host)
          ldap.port.foreach(x => props.setProperty(LdapPort, x.toString))
          ldap.bindDN.foreach(x => props.setProperty(LdapBindDN, x))
          ldap.bindPassword.foreach(x => props.setProperty(LdapBindPassword, x))
          props.setProperty(LdapBaseDN, ldap.baseDN)
          props.setProperty(LdapUserNameAttribute, ldap.userNameAttribute)
          ldap.additionalFilterCondition.foreach(x => props.setProperty(LdapAdditionalFilterCondition, x))
          ldap.fullNameAttribute.foreach(x => props.setProperty(LdapFullNameAttribute, x))
          ldap.mailAttribute.foreach(x => props.setProperty(LdapMailAddressAttribute, x))
          ldap.tls.foreach(x => props.setProperty(LdapTls, x.toString))
          ldap.ssl.foreach(x => props.setProperty(LdapSsl, x.toString))
          ldap.keystore.foreach(x => props.setProperty(LdapKeystore, x))
        }
      }
      props.setProperty(OidcAuthentication, settings.oidcAuthentication.toString)
      if (settings.oidcAuthentication) {
        settings.oidc.foreach { oidc =>
          props.setProperty(OidcIssuer, oidc.issuer.getValue)
          props.setProperty(OidcClientId, oidc.clientID.getValue)
          props.setProperty(OidcClientSecret, oidc.clientSecret.getValue)
          oidc.jwsAlgorithm.foreach { x =>
            props.setProperty(OidcJwsAlgorithm, x.getName)
          }
        }
      }
      props.setProperty(SkinName, settings.skinName.toString)
      props.setProperty(ShowMailAddress, settings.showMailAddress.toString)
      props.setProperty(WebHookBlockPrivateAddress, settings.webHook.blockPrivateAddress.toString)
      props.setProperty(WebHookWhitelist, settings.webHook.whitelist.mkString("\n"))
      props.setProperty(UploadMaxFileSize, settings.upload.maxFileSize.toString)
      props.setProperty(UploadTimeout, settings.upload.timeout.toString)
      props.setProperty(UploadLargeMaxFileSize, settings.upload.largeMaxFileSize.toString)
      props.setProperty(UploadLargeTimeout, settings.upload.largeTimeout.toString)

      Using.resource(new java.io.FileOutputStream(GitBucketConf)) { out =>
        props.store(out, null)
      }
    }
  }

  def loadSystemSettings(): SystemSettings = {
    defining(new java.util.Properties()) { props =>
      if (GitBucketConf.exists) {
        Using.resource(new java.io.FileInputStream(GitBucketConf)) { in =>
          props.load(in)
        }
      }
      SystemSettings(
        getOptionValue[String](props, BaseURL, None).map(x => x.replaceFirst("/\\Z", "")),
        getOptionValue(props, Information, None),
        getValue(props, AllowAccountRegistration, false),
        getValue(props, AllowAnonymousAccess, true),
        getValue(props, IsCreateRepoOptionPublic, true),
        getValue(props, Gravatar, false),
        getValue(props, Notification, false),
        getOptionValue[Int](props, ActivityLogLimit, None),
        getValue(props, LimitVisibleRepositories, false),
        Ssh(
          getValue(props, SshEnabled, false),
          getOptionValue[String](props, SshHost, None).map(_.trim),
          getOptionValue(props, SshPort, Some(DefaultSshPort))
        ),
        getValue(props, UseSMTP, getValue(props, Notification, false)), // handle migration scenario from only notification to useSMTP
        if (getValue(props, UseSMTP, getValue(props, Notification, false))) {
          Some(
            Smtp(
              getValue(props, SmtpHost, ""),
              getOptionValue(props, SmtpPort, Some(DefaultSmtpPort)),
              getOptionValue(props, SmtpUser, None),
              getOptionValue(props, SmtpPassword, None),
              getOptionValue[Boolean](props, SmtpSsl, None),
              getOptionValue[Boolean](props, SmtpStarttls, None),
              getOptionValue(props, SmtpFromAddress, None),
              getOptionValue(props, SmtpFromName, None)
            )
          )
        } else None,
        getValue(props, LdapAuthentication, false),
        if (getValue(props, LdapAuthentication, false)) {
          Some(
            Ldap(
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
              getOptionValue[Boolean](props, LdapSsl, None),
              getOptionValue(props, LdapKeystore, None)
            )
          )
        } else None,
        getValue(props, OidcAuthentication, false),
        if (getValue(props, OidcAuthentication, false)) {
          Some(
            OIDC(
              getValue(props, OidcIssuer, ""),
              getValue(props, OidcClientId, ""),
              getValue(props, OidcClientSecret, ""),
              getOptionValue(props, OidcJwsAlgorithm, None)
            )
          )
        } else {
          None
        },
        getValue(props, SkinName, "skin-blue"),
        getValue(props, ShowMailAddress, false),
        WebHook(getValue(props, WebHookBlockPrivateAddress, false), getSeqValue(props, WebHookWhitelist, "")),
        Upload(
          getValue(props, UploadMaxFileSize, 3 * 1024 * 1024),
          getValue(props, UploadTimeout, 3 * 10000),
          getValue(props, UploadLargeMaxFileSize, 3 * 1024 * 1024),
          getValue(props, UploadLargeTimeout, 3 * 10000)
        )
      )
    }
  }

}

object SystemSettingsService {
  import scala.reflect.ClassTag

  private val HttpProtocols = Vector("http", "https")

  case class SystemSettings(
    baseUrl: Option[String],
    information: Option[String],
    allowAccountRegistration: Boolean,
    allowAnonymousAccess: Boolean,
    isCreateRepoOptionPublic: Boolean,
    gravatar: Boolean,
    notification: Boolean,
    activityLogLimit: Option[Int],
    limitVisibleRepositories: Boolean,
    ssh: Ssh,
    useSMTP: Boolean,
    smtp: Option[Smtp],
    ldapAuthentication: Boolean,
    ldap: Option[Ldap],
    oidcAuthentication: Boolean,
    oidc: Option[OIDC],
    skinName: String,
    showMailAddress: Boolean,
    webHook: WebHook,
    upload: Upload
  ) {

    def baseUrl(request: HttpServletRequest): String =
      baseUrl.getOrElse(parseBaseUrl(request)).stripSuffix("/")

    def parseBaseUrl(req: HttpServletRequest): String = {
      val url = req.getRequestURL.toString
      val path = req.getRequestURI
      val contextPath = req.getContextPath
      val len = url.length - path.length + contextPath.length

      val base = url.substring(0, len).stripSuffix("/")
      Option(req.getHeader("X-Forwarded-Proto"))
        .map(_.toLowerCase())
        .filter(HttpProtocols.contains)
        .fold(base)(_ + base.dropWhile(_ != ':'))
    }

    def sshAddress: Option[SshAddress] = ssh.sshHost.collect {
      case host if ssh.enabled =>
        SshAddress(host, ssh.sshPort.getOrElse(DefaultSshPort), "git")
    }
  }

  case class Ssh(
    enabled: Boolean,
    sshHost: Option[String],
    sshPort: Option[Int]
  )

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
    ssl: Option[Boolean],
    keystore: Option[String]
  )

  case class OIDC(issuer: Issuer, clientID: ClientID, clientSecret: Secret, jwsAlgorithm: Option[JWSAlgorithm])
  object OIDC {
    def apply(issuer: String, clientID: String, clientSecret: String, jwsAlgorithm: Option[String]): OIDC =
      new OIDC(
        new Issuer(issuer),
        new ClientID(clientID),
        new Secret(clientSecret),
        jwsAlgorithm.map(JWSAlgorithm.parse)
      )
  }

  case class Smtp(
    host: String,
    port: Option[Int],
    user: Option[String],
    password: Option[String],
    ssl: Option[Boolean],
    starttls: Option[Boolean],
    fromAddress: Option[String],
    fromName: Option[String]
  )

  case class Proxy(
    host: String,
    port: Int,
    user: Option[String],
    password: Option[String],
  )

  case class SshAddress(host: String, port: Int, genericUser: String)

  case class WebHook(blockPrivateAddress: Boolean, whitelist: Seq[String])

  case class Upload(maxFileSize: Long, timeout: Long, largeMaxFileSize: Long, largeTimeout: Long)

  val DefaultSshPort = 29418
  val DefaultSmtpPort = 25
  val DefaultLdapPort = 389

  private val BaseURL = "base_url"
  private val Information = "information"
  private val AllowAccountRegistration = "allow_account_registration"
  private val AllowAnonymousAccess = "allow_anonymous_access"
  private val IsCreateRepoOptionPublic = "is_create_repository_option_public"
  private val Gravatar = "gravatar"
  private val Notification = "notification"
  private val ActivityLogLimit = "activity_log_limit"
  private val LimitVisibleRepositories = "limitVisibleRepositories"
  private val SshEnabled = "ssh"
  private val SshHost = "ssh.host"
  private val SshPort = "ssh.port"
  private val UseSMTP = "useSMTP"
  private val SmtpHost = "smtp.host"
  private val SmtpPort = "smtp.port"
  private val SmtpUser = "smtp.user"
  private val SmtpPassword = "smtp.password"
  private val SmtpSsl = "smtp.ssl"
  private val SmtpStarttls = "smtp.starttls"
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
  private val LdapSsl = "ldap.ssl"
  private val LdapKeystore = "ldap.keystore"
  private val OidcAuthentication = "oidc_authentication"
  private val OidcIssuer = "oidc.issuer"
  private val OidcClientId = "oidc.client_id"
  private val OidcClientSecret = "oidc.client_secret"
  private val OidcJwsAlgorithm = "oidc.jws_algorithm"
  private val SkinName = "skinName"
  private val ShowMailAddress = "showMailAddress"
  private val WebHookBlockPrivateAddress = "webhook.block_private_address"
  private val WebHookWhitelist = "webhook.whitelist"
  private val UploadMaxFileSize = "upload.maxFileSize"
  private val UploadTimeout = "upload.timeout"
  private val UploadLargeMaxFileSize = "upload.largeMaxFileSize"
  private val UploadLargeTimeout = "upload.largeTimeout"

  private def getValue[A: ClassTag](props: java.util.Properties, key: String, default: A): A = {
    getConfigValue(key).getOrElse {
      defining(props.getProperty(key)) { value =>
        if (value == null || value.isEmpty) {
          default
        } else {
          convertType(value).asInstanceOf[A]
        }
      }
    }
  }

  private def getSeqValue[A: ClassTag](props: java.util.Properties, key: String, default: A): Seq[A] = {
    getValue[String](props, key, "").split("\n").toIndexedSeq.map { value =>
      if (value == null || value.isEmpty) {
        default
      } else {
        convertType(value).asInstanceOf[A]
      }
    }
  }

  private def getOptionValue[A: ClassTag](props: java.util.Properties, key: String, default: Option[A]): Option[A] = {
    getConfigValue(key).orElse {
      defining(props.getProperty(key)) { value =>
        if (value == null || value.isEmpty) {
          default
        } else {
          Some(convertType(value)).asInstanceOf[Option[A]]
        }
      }
    }
  }

}
