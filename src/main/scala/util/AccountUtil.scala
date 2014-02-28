package util

import model.Account

/**
 * Utility for account model.
 */
object AccountUtil {
  private val LDAP_DUMMY_MAL = "@ldap-devnull"

  def hasLdapDummyMailAddress(account: Account): Boolean = {
    account.mailAddress.endsWith(LDAP_DUMMY_MAL)
  }

  def getLdapDummyMailAddress(userName: String): String = {
    userName + LDAP_DUMMY_MAL
  }
}
