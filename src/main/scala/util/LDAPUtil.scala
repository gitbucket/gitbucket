package util

import service.SystemSettingsService.Ldap
import service.SystemSettingsService
import com.novell.ldap.LDAPConnection

/**
 * Utility for LDAP authentication.
 */
object LDAPUtil extends App {

  /**
   * Try authentication by LDAP using given configuration.
   * Returns Right(mailAddress) if authentication is successful, otherwise  Left(errorMessage).
   */
  def authenticate(ldapSettings: Ldap, userName: String, password: String): Either[String, String] = {
    var conn: LDAPConnection = null
    try {
      conn = new LDAPConnection()
      conn.connect(ldapSettings.host, ldapSettings.port.getOrElse(SystemSettingsService.DefaultLdapPort))
      val userDN = ldapSettings.userNameAttribute + "=" + userName + ",ou=Users," + ldapSettings.baseDN
      conn.bind(3, userDN, password.getBytes)
      if(conn.isBound){
        val results = conn.search(userDN, LDAPConnection.SCOPE_BASE, "", Array[String](ldapSettings.mailAttribute), false)
        var mailAddress: String = null
        while(results.hasMore){
          mailAddress = results.next.getAttribute(ldapSettings.mailAttribute).getStringValue
        }
        if(mailAddress != null){
          Right(mailAddress)
        } else {
          Left("Can't find mail address.")
        }
      } else {
        Left("Authentication failed.")
      }
    } catch {
      case ex: Exception => Left(ex.getMessage)
    } finally {
      if(conn != null){
        conn.disconnect()
      }
    }
  }

//  val ldapSettings = Ldap("192.168.159.128", 389, "dc=unix-power,dc=net", "uid", "mail")
//
//  println(authenticate(ldapSettings, "tanaka", "password"))

}
