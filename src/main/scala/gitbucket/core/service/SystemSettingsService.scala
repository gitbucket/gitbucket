package gitbucket.core.service

import javax.servlet.http.HttpServletRequest
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.oauth2.sdk.auth.Secret
import com.nimbusds.oauth2.sdk.id.{ClientID, Issuer}
import gitbucket.core.service.SystemSettingsService.{getOptionValue, _}
import gitbucket.core.util.ConfigUtil._
import gitbucket.core.util.Directory._

import scala.util.Using

trait SystemSettingsService {

  def baseUrl(implicit request: HttpServletRequest): String = loadSystemSettings().baseUrl(request)

  def saveSystemSettings(settings: SystemSettings): Unit = {
    val props = new java.util.Properties()
    settings.baseUrl.foreach(x => props.setProperty(BaseURL, x.replaceFirst("/\\Z", "")))
    settings.information.foreach(x => props.setProperty(Information, x))
    props.setProperty(AllowAccountRegistration, settings.basicBehavior.allowAccountRegistration.toString)
    props.setProperty(AllowResetPassword, settings.basicBehavior.allowResetPassword.toString)
    props.setProperty(AllowAnonymousAccess, settings.basicBehavior.allowAnonymousAccess.toString)
    props.setProperty(IsCreateRepoOptionPublic, settings.basicBehavior.isCreateRepoOptionPublic.toString)
    props.setProperty(RepositoryOperationCreate, settings.basicBehavior.repositoryOperation.create.toString)
    props.setProperty(RepositoryOperationDelete, settings.basicBehavior.repositoryOperation.delete.toString)
    props.setProperty(RepositoryOperationRename, settings.basicBehavior.repositoryOperation.rename.toString)
    props.setProperty(RepositoryOperationTransfer, settings.basicBehavior.repositoryOperation.transfer.toString)
    props.setProperty(RepositoryOperationFork, settings.basicBehavior.repositoryOperation.fork.toString)
    props.setProperty(Gravatar, settings.basicBehavior.gravatar.toString)
    props.setProperty(Notification, settings.basicBehavior.notification.toString)
    props.setProperty(LimitVisibleRepositories, settings.basicBehavior.limitVisibleRepositories.toString)
    props.setProperty(SshEnabled, settings.ssh.enabled.toString)
    settings.ssh.bindAddress.foreach { bindAddress =>
      props.setProperty(SshBindAddressHost, bindAddress.host.trim())
      props.setProperty(SshBindAddressPort, bindAddress.port.toString)
    }
    settings.ssh.publicAddress.foreach { publicAddress =>
      props.setProperty(SshPublicAddressHost, publicAddress.host.trim())
      props.setProperty(SshPublicAddressPort, publicAddress.port.toString)
    }
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
    props.setProperty(SkinName, settings.skinName)
    settings.userDefinedCss.foreach(x => props.setProperty(UserDefinedCss, x))
    props.setProperty(ShowMailAddress, settings.showMailAddress.toString)
    props.setProperty(WebHookBlockPrivateAddress, settings.webHook.blockPrivateAddress.toString)
    props.setProperty(WebHookWhitelist, settings.webHook.whitelist.mkString("\n"))
    props.setProperty(UploadMaxFileSize, settings.upload.maxFileSize.toString)
    props.setProperty(UploadTimeout, settings.upload.timeout.toString)
    props.setProperty(UploadLargeMaxFileSize, settings.upload.largeMaxFileSize.toString)
    props.setProperty(UploadLargeTimeout, settings.upload.largeTimeout.toString)
    props.setProperty(RepositoryViewerMaxFiles, settings.repositoryViewer.maxFiles.toString)
    props.setProperty(RepositoryViewerMaxDiffFiles, settings.repositoryViewer.maxDiffFiles.toString)
    props.setProperty(RepositoryViewerMaxDiffLines, settings.repositoryViewer.maxDiffLines.toString)
    props.setProperty(DefaultBranch, settings.defaultBranch)

    Using.resource(new java.io.FileOutputStream(GitBucketConf)) { out =>
      props.store(out, null)
    }
  }

  def loadSystemSettings(): SystemSettings = {
    val props = new java.util.Properties()
    if (GitBucketConf.exists) {
      Using.resource(new java.io.FileInputStream(GitBucketConf)) { in =>
        props.load(in)
      }
    }
    loadSystemSettings(props)
  }

  def loadSystemSettings(props: java.util.Properties): SystemSettings = {
    SystemSettings(
      getOptionValue[String](props, BaseURL, None).map(x => x.replaceFirst("/\\Z", "")),
      getOptionValue(props, Information, None),
      BasicBehavior(
        getValue(props, AllowAccountRegistration, false),
        getValue(props, AllowResetPassword, false),
        getValue(props, AllowAnonymousAccess, true),
        getValue(props, IsCreateRepoOptionPublic, true),
        RepositoryOperation(
          create = getValue(props, RepositoryOperationCreate, true),
          delete = getValue(props, RepositoryOperationDelete, true),
          rename = getValue(props, RepositoryOperationRename, true),
          transfer = getValue(props, RepositoryOperationTransfer, true),
          fork = getValue(props, RepositoryOperationFork, true)
        ),
        getValue(props, Gravatar, false),
        getValue(props, Notification, false),
        getValue(props, LimitVisibleRepositories, false)
      ),
      Ssh(
        enabled = getValue(props, SshEnabled, false),
        bindAddress = {
          // try the new-style configuration first
          getOptionValue[String](props, SshBindAddressHost, None)
            .map(h => SshAddress(h, getValue(props, SshBindAddressPort, DefaultSshPort), GenericSshUser))
            .orElse(
              // otherwise try to get old-style configuration
              getOptionValue[String](props, SshHost, None)
                .map(_.trim)
                .map(h => SshAddress(h, getValue(props, SshPort, DefaultSshPort), GenericSshUser))
            )
        },
        publicAddress = getOptionValue[String](props, SshPublicAddressHost, None)
          .map(h => SshAddress(h, getValue(props, SshPublicAddressPort, PublicSshPort), GenericSshUser))
      ),
      getValue(
        props,
        UseSMTP,
        getValue(props, Notification, false)
      ), // handle migration scenario from only notification to useSMTP
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
      getOptionValue(props, UserDefinedCss, None),
      getValue(props, ShowMailAddress, false),
      WebHook(getValue(props, WebHookBlockPrivateAddress, false), getSeqValue(props, WebHookWhitelist, "")),
      Upload(
        getValue(props, UploadMaxFileSize, 3 * 1024 * 1024),
        getValue(props, UploadTimeout, 3 * 10000),
        getValue(props, UploadLargeMaxFileSize, 3 * 1024 * 1024),
        getValue(props, UploadLargeTimeout, 3 * 10000)
      ),
      RepositoryViewerSettings(
        getValue(props, RepositoryViewerMaxFiles, 0),
        getValue(props, RepositoryViewerMaxDiffFiles, 100),
        getValue(props, RepositoryViewerMaxDiffLines, 1000)
      ),
      getValue(props, DefaultBranch, "main")
    )
  }
}

object SystemSettingsService {
  import scala.reflect.ClassTag

  private val HttpProtocols = Vector("http", "https")

  case class SystemSettings(
    baseUrl: Option[String],
    information: Option[String],
    basicBehavior: BasicBehavior,
    ssh: Ssh,
    useSMTP: Boolean,
    smtp: Option[Smtp],
    ldapAuthentication: Boolean,
    ldap: Option[Ldap],
    oidcAuthentication: Boolean,
    oidc: Option[OIDC],
    skinName: String,
    userDefinedCss: Option[String],
    showMailAddress: Boolean,
    webHook: WebHook,
    upload: Upload,
    repositoryViewer: RepositoryViewerSettings,
    defaultBranch: String
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

    def sshBindAddress: Option[SshAddress] =
      ssh.bindAddress

    def sshPublicAddress: Option[SshAddress] =
      ssh.publicAddress.orElse(ssh.bindAddress)

    def sshUrl: Option[String] =
      ssh.getUrl

    def sshUrl(owner: String, name: String): Option[String] =
      ssh.getUrl(owner: String, name: String)
  }

  case class BasicBehavior(
    allowAccountRegistration: Boolean,
    allowResetPassword: Boolean,
    allowAnonymousAccess: Boolean,
    isCreateRepoOptionPublic: Boolean,
    repositoryOperation: RepositoryOperation,
    gravatar: Boolean,
    notification: Boolean,
    limitVisibleRepositories: Boolean,
  )

  case class RepositoryOperation(
    create: Boolean,
    delete: Boolean,
    rename: Boolean,
    transfer: Boolean,
    fork: Boolean
  )

  case class Ssh(
    enabled: Boolean,
    bindAddress: Option[SshAddress],
    publicAddress: Option[SshAddress]
  ) {

    def getUrl: Option[String] =
      if (enabled) {
        publicAddress.map(_.getUrl).orElse(bindAddress.map(_.getUrl))
      } else {
        None
      }

    def getUrl(owner: String, name: String): Option[String] =
      if (enabled) {
        publicAddress
          .map(_.getUrl(owner, name))
          .orElse(bindAddress.map(_.getUrl(owner, name)))
      } else {
        None
      }
  }

  object Ssh {
    def apply(
      enabled: Boolean,
      bindAddress: Option[SshAddress],
      publicAddress: Option[SshAddress]
    ): Ssh =
      new Ssh(enabled, bindAddress, publicAddress.orElse(bindAddress))
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
    password: Option[String]
  )

  case class SshAddress(host: String, port: Int, genericUser: String) {

    def isDefaultPort: Boolean =
      port == PublicSshPort

    def getUrl: String =
      if (isDefaultPort) {
        s"${genericUser}@${host}"
      } else {
        s"ssh://${genericUser}@${host}:${port}"
      }

    def getUrl(owner: String, name: String): String =
      if (isDefaultPort) {
        s"${genericUser}@${host}:${owner}/${name}.git"
      } else {
        s"ssh://${genericUser}@${host}:${port}/${owner}/${name}.git"
      }
  }

  case class WebHook(blockPrivateAddress: Boolean, whitelist: Seq[String])

  case class Upload(maxFileSize: Long, timeout: Long, largeMaxFileSize: Long, largeTimeout: Long)

  case class RepositoryViewerSettings(maxFiles: Int, maxDiffFiles: Int, maxDiffLines: Int)

  val GenericSshUser = "git"
  val PublicSshPort = 22
  val DefaultSshPort = 29418
  val DefaultSmtpPort = 25
  val DefaultLdapPort = 389

  private val BaseURL = "base_url"
  private val Information = "information"
  private val AllowAccountRegistration = "allow_account_registration"
  private val AllowResetPassword = "allow_reset_password"
  private val AllowAnonymousAccess = "allow_anonymous_access"
  private val IsCreateRepoOptionPublic = "is_create_repository_option_public"
  private val RepositoryOperationCreate = "repository_operation_create"
  private val RepositoryOperationDelete = "repository_operation_delete"
  private val RepositoryOperationRename = "repository_operation_rename"
  private val RepositoryOperationTransfer = "repository_operation_transfer"
  private val RepositoryOperationFork = "repository_operation_fork"
  private val Gravatar = "gravatar"
  private val Notification = "notification"
  private val LimitVisibleRepositories = "limitVisibleRepositories"
  private val SshEnabled = "ssh"
  private val SshHost = "ssh.host"
  private val SshPort = "ssh.port"
  private val SshBindAddressHost = "ssh.bindAddress.host"
  private val SshBindAddressPort = "ssh.bindAddress.port"
  private val SshPublicAddressHost = "ssh.publicAddress.host"
  private val SshPublicAddressPort = "ssh.publicAddress.port"
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
  private val UserDefinedCss = "userDefinedCss"
  private val ShowMailAddress = "showMailAddress"
  private val WebHookBlockPrivateAddress = "webhook.block_private_address"
  private val WebHookWhitelist = "webhook.whitelist"
  private val UploadMaxFileSize = "upload.maxFileSize"
  private val UploadTimeout = "upload.timeout"
  private val UploadLargeMaxFileSize = "upload.largeMaxFileSize"
  private val UploadLargeTimeout = "upload.largeTimeout"
  private val RepositoryViewerMaxFiles = "repository_viewer_max_files"
  private val RepositoryViewerMaxDiffFiles = "repository_viewer_max_diff_files"
  private val RepositoryViewerMaxDiffLines = "repository_viewer_max_diff_lines"
  private val DefaultBranch = "default_branch"

  private def getValue[A: ClassTag](props: java.util.Properties, key: String, default: A): A = {
    getConfigValue(key).getOrElse {
      val value = props.getProperty(key)
      if (value == null || value.isEmpty) {
        default
      } else {
        convertType(value).asInstanceOf[A]
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
      val value = props.getProperty(key)
      if (value == null || value.isEmpty) {
        default
      } else {
        Some(convertType(value)).asInstanceOf[Option[A]]
      }
    }
  }

}
