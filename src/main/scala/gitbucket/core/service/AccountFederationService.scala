package gitbucket.core.service

import gitbucket.core.model.Profile.profile.blockingApi._
import gitbucket.core.model.Profile.{AccountFederations, Accounts}
import gitbucket.core.model.{Account, AccountFederation}
import gitbucket.core.util.SyntaxSugars.~
import org.slf4j.LoggerFactory

trait AccountFederationService {
  self: AccountService =>

  private val logger = LoggerFactory.getLogger(classOf[AccountFederationService])

  /**
   * Get or create a user account federated with OIDC or SAML IdP.
   *
   * @param issuer            Issuer
   * @param subject           Subject
   * @param mailAddress       Mail address
   * @param preferredUserName Username (if this is none, username will be generated from the mail address)
   * @param fullName          Fullname (defaults to username)
   * @return Account
   */
  def getOrCreateFederatedUser(
    issuer: String,
    subject: String,
    mailAddress: String,
    preferredUserName: Option[String],
    fullName: Option[String]
  )(implicit s: Session): Option[Account] =
    getAccountByFederation(issuer, subject) match {
      case Some(account) if !account.isRemoved =>
        Some(account)
      case Some(account) =>
        logger.info(s"Federated user found but disabled: userName=${account.userName}, isRemoved=${account.isRemoved}")
        None
      case None =>
        findAvailableUserName(preferredUserName, mailAddress) flatMap { userName =>
          createAccount(userName, "[DUMMY]", fullName.getOrElse(userName), mailAddress, isAdmin = false, None, None)
          createAccountFederation(issuer, subject, userName)
          getAccountByUserName(userName)
        }
    }

  private def extractSafeStringForUserName(s: String) = """^[a-zA-Z0-9][a-zA-Z0-9\-_.]*""".r.findPrefixOf(s)

  /**
   * Find an available username from the preferred username or mail address.
   *
   * @param mailAddress       Mail address
   * @param preferredUserName Username
   * @return Available username
   */
  def findAvailableUserName(preferredUserName: Option[String], mailAddress: String)(
    implicit s: Session
  ): Option[String] = {
    preferredUserName
      .flatMap(n => extractSafeStringForUserName(n))
      .orElse(extractSafeStringForUserName(mailAddress)) match {
      case Some(safeUserName) =>
        getAccountByUserName(safeUserName, includeRemoved = true) match {
          case None => Some(safeUserName)
          case Some(_) =>
            logger.info(
              s"User ($safeUserName) already exists. preferredUserName=$preferredUserName, mailAddress=$mailAddress"
            )
            None
        }
      case None =>
        logger.info(s"Could not extract username from preferredUserName=$preferredUserName, mailAddress=$mailAddress")
        None
    }
  }

  def getAccountByFederation(issuer: String, subject: String)(implicit s: Session): Option[Account] =
    AccountFederations
      .filter(_.byPrimaryKey(issuer, subject))
      .join(Accounts)
      .on { case af ~ ac => af.userName === ac.userName }
      .map { case _ ~ ac => ac }
      .firstOption

  def hasAccountFederation(userName: String)(implicit s: Session): Boolean =
    AccountFederations.filter(_.userName === userName.bind).exists.run

  def createAccountFederation(issuer: String, subject: String, userName: String)(implicit s: Session): Unit =
    AccountFederations insert AccountFederation(issuer, subject, userName)
}

object AccountFederationService extends AccountFederationService with AccountService
