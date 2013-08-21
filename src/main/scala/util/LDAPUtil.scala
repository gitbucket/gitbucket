package util

import service.SystemSettingsService.Ldap
import service.SystemSettingsService
import com.novell.ldap.{LDAPReferralException, LDAPEntry, LDAPConnection}

/**
 * Utility for LDAP authentication.
 */
object LDAPUtil {

  private val LDAP_VERSION: Int = 3

  /**
   * Try authentication by LDAP using given configuration.
   * Returns Right(mailAddress) if authentication is successful, otherwise  Left(errorMessage).
   */
  def authenticate(ldapSettings: Ldap, userName: String, password: String): Either[String, String] = {
    bind(
      ldapSettings.host,
      ldapSettings.port.getOrElse(SystemSettingsService.DefaultLdapPort),
      ldapSettings.bindDN,
      ldapSettings.bindPassword
    ) match {
      case Some(conn) => {
        withConnection(conn) { conn =>
          findUser(conn, userName, ldapSettings.baseDN, ldapSettings.userNameAttribute) match {
            case Some(userDN) => userAuthentication(ldapSettings, userDN, password)
            case None => Left("User does not exist")
          }
        }
      }
      case None => Left("System LDAP authentication failed.")
    }
  }

  private def userAuthentication(ldapSettings: Ldap, userDN: String, password: String): Either[String, String] = {
    bind(
      ldapSettings.host,
      ldapSettings.port.getOrElse(SystemSettingsService.DefaultLdapPort),
      userDN,
      password
    ) match {
      case Some(conn) => {
        withConnection(conn) { conn =>
          findMailAddress(conn, userDN, ldapSettings.mailAttribute) match {
            case Some(mailAddress) => Right(mailAddress)
            case None => Left("Can't find mail address.")
          }
        }
      }
      case None => Left("User LDAP Authentication Failed.")
    }
  }

  private def bind(host: String, port: Int, dn: String, password: String): Option[LDAPConnection] = {
    val conn: LDAPConnection = new LDAPConnection
    try {
      conn.connect(host, port)
      conn.bind(LDAP_VERSION, dn, password.getBytes)
      Some(conn)
    } catch {
      case e: Exception => {
        if (conn.isConnected) conn.disconnect()
        None
      }
    }
  }

  private def withConnection[T](conn: LDAPConnection)(f: LDAPConnection => T): T = {
    try {
      f(conn)
    } finally {
      conn.disconnect()
    }
  }

  private def findUser(conn: LDAPConnection, userName: String, baseDN: String, userNameAttribute: String): Option[String] = {
    val results = conn.search(baseDN, LDAPConnection.SCOPE_SUB, userNameAttribute + "=" + userName, null, false)
    while (results.hasMore) {
      var entry: LDAPEntry = null
      try {
        entry = results.next
      } catch {
        case lre: LDAPReferralException => // NOTE(tanacasino): Referral follow is off. so ignores it.(for AD)
      }
      if (entry != null) {
        return Some(entry.getDN)
      }
    }
    None
  }

  private def findMailAddress(conn: LDAPConnection, userDN: String, mailAttribute: String): Option[String] = {
    val attributes = Array[String](mailAttribute)
    val results = conn.search(userDN, LDAPConnection.SCOPE_BASE, null, attributes, false)
    if (results.hasMore) {
      val attr = results.next.getAttribute(mailAttribute)
      if (attr != null) {
        Some(attr.getStringValue)
      } else {
        None
      }
    } else {
      None
    }
  }
}
