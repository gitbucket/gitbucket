package util

import util.ControlUtil._
import service.SystemSettingsService
import com.novell.ldap._
import service.SystemSettingsService.Ldap
import scala.annotation.tailrec

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
      ldapSettings.bindDN.getOrElse(""),
      ldapSettings.bindPassword.getOrElse("")
    ) match {
      case Some(conn) => {
        withConnection(conn) { conn =>
          findUser(conn, userName, ldapSettings.baseDN, ldapSettings.userNameAttribute) match {
            case Some(userDN) => userAuthentication(ldapSettings, userDN, password)
            case None => Left("User does not exist.")
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
    getEntries(conn.search(baseDN, LDAPConnection.SCOPE_SUB, userNameAttribute + "=" + userName, null, false)).collectFirst {
      case x => x.getDN
    }
  }

  private def findMailAddress(conn: LDAPConnection, userDN: String, mailAttribute: String): Option[String] =
    defining(conn.search(userDN, LDAPConnection.SCOPE_BASE, null, Array[String](mailAttribute), false)){ results =>
      optionIf (results.hasMore) {
        Option(results.next.getAttribute(mailAttribute)).map(_.getStringValue)
      }
    }
}
