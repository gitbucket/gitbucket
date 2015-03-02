package gitbucket.core.util

import gitbucket.core.model.Account
import ControlUtil._
import gitbucket.core.service.SystemSettingsService
import gitbucket.core.service.SystemSettingsService.Ldap
import com.novell.ldap._
import java.security.Security
import org.slf4j.LoggerFactory
import scala.annotation.tailrec

/**
 * Utility for LDAP authentication.
 */
object LDAPUtil {

  private val LDAP_VERSION: Int = LDAPConnection.LDAP_V3
  private val logger = LoggerFactory.getLogger(getClass().getName())

  private val LDAP_DUMMY_MAL = "@ldap-devnull"

  /**
   * Returns true if mail address ends with "@ldap-devnull"
   */
  def isDummyMailAddress(account: Account): Boolean = {
    account.mailAddress.endsWith(LDAP_DUMMY_MAL)
  }

  /**
   * Creates dummy address (userName@ldap-devnull) for LDAP login.
   *
   * If  mail address is not managed in LDAP server, GitBucket stores this dummy address in first LDAP login.
   * GitBucket does not send any mails to this dummy address. And these users must input their mail address
   * at the first step after LDAP authentication.
   */
  def createDummyMailAddress(userName: String): String = {
    userName + LDAP_DUMMY_MAL
  }

  /**
   * Try authentication by LDAP using given configuration.
   * Returns Right(LDAPUserInfo) if authentication is successful, otherwise  Left(errorMessage).
   */
  def authenticate(ldapSettings: Ldap, userName: String, password: String): Either[String, LDAPUserInfo] = {
    bind(
      host     = ldapSettings.host,
      port     = ldapSettings.port.getOrElse(SystemSettingsService.DefaultLdapPort),
      dn       = ldapSettings.bindDN.getOrElse(""),
      password = ldapSettings.bindPassword.getOrElse(""),
      tls      = ldapSettings.tls.getOrElse(false),
      ssl      = ldapSettings.ssl.getOrElse(false),
      keystore = ldapSettings.keystore.getOrElse(""),
      error    = "System LDAP authentication failed."
    ){ conn =>
      findUser(conn, userName, ldapSettings.baseDN, ldapSettings.userNameAttribute, ldapSettings.additionalFilterCondition) match {
        case Some(userDN) => userAuthentication(ldapSettings, userDN, userName, password)
        case None         => Left("User does not exist.")
      }
    }
  }

  private def userAuthentication(ldapSettings: Ldap, userDN: String, userName: String, password: String): Either[String, LDAPUserInfo] = {
    bind(
      host     = ldapSettings.host,
      port     = ldapSettings.port.getOrElse(SystemSettingsService.DefaultLdapPort),
      dn       = userDN,
      password = password,
      tls      = ldapSettings.tls.getOrElse(false),
      ssl      = ldapSettings.ssl.getOrElse(false),
      keystore = ldapSettings.keystore.getOrElse(""),
      error    = "User LDAP Authentication Failed."
    ){ conn =>
      if(ldapSettings.mailAttribute.getOrElse("").isEmpty) {
        Right(LDAPUserInfo(
          userName    = userName,
          fullName    = ldapSettings.fullNameAttribute.flatMap { fullNameAttribute =>
            findFullName(conn, userDN, ldapSettings.userNameAttribute, userName, fullNameAttribute)
          }.getOrElse(userName),
          mailAddress = createDummyMailAddress(userName)))
      } else {
        findMailAddress(conn, userDN, ldapSettings.userNameAttribute, userName, ldapSettings.mailAttribute.get) match {
          case Some(mailAddress) => Right(LDAPUserInfo(
            userName    = getUserNameFromMailAddress(userName),
            fullName    = ldapSettings.fullNameAttribute.flatMap { fullNameAttribute =>
              findFullName(conn, userDN, ldapSettings.userNameAttribute, userName, fullNameAttribute)
            }.getOrElse(userName),
            mailAddress = mailAddress))
          case None => Left("Can't find mail address.")
        }
      }
    }
  }

  private def getUserNameFromMailAddress(userName: String): String = {
    (userName.indexOf('@') match {
      case i if i >= 0 => userName.substring(0, i)
      case i           => userName
    }).replaceAll("[^a-zA-Z0-9\\-_.]", "").replaceAll("^[_\\-]", "")
  }

  private def bind[A](host: String, port: Int, dn: String, password: String, tls: Boolean, ssl: Boolean, keystore: String, error: String)
                  (f: LDAPConnection => Either[String, A]): Either[String, A] = {
    if (tls) {
      // Dynamically set Sun as the security provider
      Security.addProvider(new com.sun.net.ssl.internal.ssl.Provider())

      if (keystore.compareTo("") != 0) {
        // Dynamically set the property that JSSE uses to identify
        // the keystore that holds trusted root certificates
        System.setProperty("javax.net.ssl.trustStore", keystore)
      }
    }

    val conn: LDAPConnection = 
	if(ssl) {
		new LDAPConnection(new LDAPJSSESecureSocketFactory()) 
	}else {
		new LDAPConnection(new LDAPJSSEStartTLSFactory())
	}

    try {
      // Connect to the server
      conn.connect(host, port)

      if (tls) {
        // Secure the connection
        conn.startTLS()
      }

      // Bind to the server
      conn.bind(LDAP_VERSION, dn, password.getBytes)

      // Execute a given function and returns a its result
      f(conn)

    } catch {
      case e: Exception => {
        // Provide more information if something goes wrong
        logger.info("" + e)

        if (conn.isConnected) {
          conn.disconnect()
        }
        // Returns an error message
        Left(error)
      }
    }
  }

  /**
   * Search a specified user and returns userDN if exists.
   */
  private def findUser(conn: LDAPConnection, userName: String, baseDN: String, userNameAttribute: String, additionalFilterCondition: Option[String]): Option[String] = {
    @tailrec
    def getEntries(results: LDAPSearchResults, entries: List[Option[LDAPEntry]] = Nil): List[LDAPEntry] = {
      if(results.hasMore){
        getEntries(results, entries :+ (try {
          Option(results.next)
        } catch {
          case ex: LDAPReferralException => None // NOTE(tanacasino): Referral follow is off. so ignores it.(for AD)
        }))
      } else {
        entries.flatten
      }
    }

    val filterCond = additionalFilterCondition.getOrElse("") match {
      case "" => userNameAttribute + "=" + userName
      case x => "(&(" + x + ")(" + userNameAttribute + "=" + userName + "))"
    }

    getEntries(conn.search(baseDN, LDAPConnection.SCOPE_SUB, filterCond, null, false)).collectFirst {
      case x => x.getDN
    }
  }

  private def findMailAddress(conn: LDAPConnection, userDN: String, userNameAttribute: String, userName: String, mailAttribute: String): Option[String] =
    defining(conn.search(userDN, LDAPConnection.SCOPE_BASE, userNameAttribute + "=" + userName, Array[String](mailAttribute), false)){ results =>
      if(results.hasMore) {
        Option(results.next.getAttribute(mailAttribute)).map(_.getStringValue)
      } else None
    }

  private def findFullName(conn: LDAPConnection, userDN: String, userNameAttribute: String, userName: String, nameAttribute: String): Option[String] =
    defining(conn.search(userDN, LDAPConnection.SCOPE_BASE, userNameAttribute + "=" + userName, Array[String](nameAttribute), false)){ results =>
      if(results.hasMore) {
        Option(results.next.getAttribute(nameAttribute)).map(_.getStringValue)
      } else None
    }

  case class LDAPUserInfo(userName: String, fullName: String, mailAddress: String)

}
