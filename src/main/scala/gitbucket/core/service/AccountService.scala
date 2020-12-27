package gitbucket.core.service

import org.slf4j.LoggerFactory
import gitbucket.core.model.{Account, AccountExtraMailAddress, AccountPreference, GroupMember}
import gitbucket.core.model.Profile._
import gitbucket.core.model.Profile.profile.blockingApi._
import gitbucket.core.model.Profile.dateColumnType
import gitbucket.core.util.{LDAPUtil, StringUtil}
import StringUtil._
import gitbucket.core.plugin.PluginRegistry
import gitbucket.core.service.SystemSettingsService.SystemSettings

trait AccountService {

  private val logger = LoggerFactory.getLogger(classOf[AccountService])

  def authenticate(settings: SystemSettings, userName: String, password: String)(
    implicit s: Session
  ): Option[Account] = {
    val account = if (password.isEmpty) {
      None
    } else if (settings.ldapAuthentication) {
      ldapAuthentication(settings, userName, password)
    } else {
      defaultAuthentication(userName, password)
    }

    if (account.isEmpty) {
      logger.info(s"Failed to authenticate: $userName")
    }

    account
  }

  /**
   * Authenticate by internal database.
   */
  private def defaultAuthentication(userName: String, password: String)(implicit s: Session) = {
    val pbkdf2re = """^\$pbkdf2-sha256\$(\d+)\$([0-9a-zA-Z+/=]+)\$([0-9a-zA-Z+/=]+)$""".r
    getAccountByUserName(userName).collect {
      case account if !account.isGroupAccount =>
        account.password match {
          case pbkdf2re(iter, salt, hash) if (pbkdf2_sha256(iter.toInt, salt, password) == hash) => Some(account)
          case p if p == sha1(password) =>
            updateAccount(account.copy(password = pbkdf2_sha256(password)))
            Some(account)
          case _ => None
        }
      case account if (!account.isGroupAccount && account.password == sha1(password)) => Some(account)
    } getOrElse None
  }

  /**
   * Authenticate by LDAP.
   */
  private def ldapAuthentication(settings: SystemSettings, userName: String, password: String)(
    implicit s: Session
  ): Option[Account] = {
    LDAPUtil.authenticate(settings.ldap.get, userName, password) match {
      case Right(ldapUserInfo) => {
        // Create or update account by LDAP information
        getAccountByUserName(ldapUserInfo.userName, true) match {
          case Some(x) if (!x.isRemoved) => {
            if (settings.ldap.get.mailAttribute.getOrElse("").isEmpty) {
              updateAccount(x.copy(fullName = ldapUserInfo.fullName))
            } else {
              updateAccount(x.copy(mailAddress = ldapUserInfo.mailAddress, fullName = ldapUserInfo.fullName))
            }
            getAccountByUserName(ldapUserInfo.userName)
          }
          case Some(x) if (x.isRemoved) => {
            logger.info("LDAP Authentication Failed: Account is already registered but disabled.")
            defaultAuthentication(userName, password)
          }
          case None =>
            getAccountByMailAddress(ldapUserInfo.mailAddress, true) match {
              case Some(x) if (!x.isRemoved) => {
                updateAccount(x.copy(fullName = ldapUserInfo.fullName))
                getAccountByUserName(ldapUserInfo.userName)
              }
              case Some(x) if (x.isRemoved) => {
                logger.info("LDAP Authentication Failed: Account is already registered but disabled.")
                defaultAuthentication(userName, password)
              }
              case None => {
                createAccount(
                  ldapUserInfo.userName,
                  "",
                  ldapUserInfo.fullName,
                  ldapUserInfo.mailAddress,
                  false,
                  None,
                  None
                )
                getAccountByUserName(ldapUserInfo.userName)
              }
            }
        }
      }
      case Left(errorMessage) => {
        logger.info(s"LDAP error: ${errorMessage}")
        defaultAuthentication(userName, password)
      }
    }
  }

  def getAccountByUserName(userName: String, includeRemoved: Boolean = false)(implicit s: Session): Option[Account] =
    Accounts filter (t => (t.userName === userName.bind).&&(t.removed === false.bind, !includeRemoved)) firstOption

  def getAccountByUserNameIgnoreCase(userName: String, includeRemoved: Boolean = false)(
    implicit s: Session
  ): Option[Account] =
    Accounts filter (
      t => (t.userName.toLowerCase === userName.toLowerCase.bind).&&(t.removed === false.bind, !includeRemoved)
    ) firstOption

  def getAccountsByUserNames(userNames: Set[String], knowns: Set[Account], includeRemoved: Boolean = false)(
    implicit s: Session
  ): Map[String, Account] = {
    val map = knowns.map(a => a.userName -> a).toMap
    val needs = userNames -- map.keySet
    if (needs.isEmpty) {
      map
    } else {
      map ++ Accounts
        .filter(t => (t.userName inSetBind needs).&&(t.removed === false.bind, !includeRemoved))
        .list
        .map(a => a.userName -> a)
        .toMap
    }
  }

  def getAccountByMailAddress(mailAddress: String, includeRemoved: Boolean = false)(
    implicit s: Session
  ): Option[Account] =
    (Accounts joinLeft AccountExtraMailAddresses on { case (a, e) => a.userName === e.userName })
      .filter {
        case (a, x) =>
          ((a.mailAddress.toLowerCase === mailAddress.toLowerCase.bind) ||
            (x.map { e =>
                e.extraMailAddress.toLowerCase === mailAddress.toLowerCase.bind
              }
              .getOrElse(false.bind))).&&(a.removed === false.bind, !includeRemoved)
      }
      .map { case (a, e) => a } firstOption

  def getAllUsers(includeRemoved: Boolean = true, includeGroups: Boolean = true)(implicit s: Session): List[Account] = {
    Accounts filter { t =>
      (1.bind === 1.bind)
        .&&(t.groupAccount === false.bind, !includeGroups)
        .&&(t.removed === false.bind, !includeRemoved)
    } sortBy (_.userName) list
  }

  def isLastAdministrator(account: Account)(implicit s: Session): Boolean = {
    if (account.isAdmin) {
      (Accounts filter (_.removed === false.bind) filter (_.isAdmin === true.bind) map (_.userName.length)).first == 1
    } else false
  }

  def createAccount(
    userName: String,
    password: String,
    fullName: String,
    mailAddress: String,
    isAdmin: Boolean,
    description: Option[String],
    url: Option[String]
  )(implicit s: Session): Account = {
    val account = Account(
      userName = userName,
      password = password,
      fullName = fullName,
      mailAddress = mailAddress,
      isAdmin = isAdmin,
      url = url,
      registeredDate = currentDate,
      updatedDate = currentDate,
      lastLoginDate = None,
      image = None,
      isGroupAccount = false,
      isRemoved = false,
      description = description
    )
    Accounts insert account
    account
  }

  def suspendAccount(account: Account)(implicit s: Session): Unit = {
    // Remove from GROUP_MEMBER and COLLABORATOR
    removeUserRelatedData(account.userName)
    updateAccount(account.copy(isRemoved = true))

    // call hooks
    PluginRegistry().getAccountHooks.foreach(_.deleted(account.userName))
  }

  def updateAccount(account: Account)(implicit s: Session): Unit =
    Accounts
      .filter { a =>
        a.userName === account.userName.bind
      }
      .map { a =>
        (
          a.password,
          a.fullName,
          a.mailAddress,
          a.isAdmin,
          a.url.?,
          a.registeredDate,
          a.updatedDate,
          a.lastLoginDate.?,
          a.removed,
          a.description.?
        )
      }
      .update(
        account.password,
        account.fullName,
        account.mailAddress,
        account.isAdmin,
        account.url,
        account.registeredDate,
        currentDate,
        account.lastLoginDate,
        account.isRemoved,
        account.description
      )

  def updateAvatarImage(userName: String, image: Option[String])(implicit s: Session): Unit =
    Accounts.filter(_.userName === userName.bind).map(_.image.?).update(image)

  def getAccountExtraMailAddresses(userName: String)(implicit s: Session): List[String] = {
    AccountExtraMailAddresses.filter(_.userName === userName.bind).map(_.extraMailAddress) list
  }

  def updateAccountExtraMailAddresses(userName: String, mails: List[String])(implicit s: Session): Unit = {
    AccountExtraMailAddresses.filter(_.userName === userName.bind).delete
    mails.map(AccountExtraMailAddresses insert AccountExtraMailAddress(userName, _))
  }

  def updateLastLoginDate(userName: String)(implicit s: Session): Unit =
    Accounts.filter(_.userName === userName.bind).map(_.lastLoginDate).update(currentDate)

  def createGroup(groupName: String, description: Option[String], url: Option[String])(implicit s: Session): Account = {
    val group = Account(
      userName = groupName,
      password = "",
      fullName = groupName,
      mailAddress = groupName + "@devnull",
      isAdmin = false,
      url = url,
      registeredDate = currentDate,
      updatedDate = currentDate,
      lastLoginDate = None,
      image = None,
      isGroupAccount = true,
      isRemoved = false,
      description = description
    )
    Accounts insert group
    group
  }

  def updateGroup(groupName: String, description: Option[String], url: Option[String], removed: Boolean)(
    implicit s: Session
  ): Unit =
    Accounts
      .filter(_.userName === groupName.bind)
      .map(t => (t.url.?, t.description.?, t.updatedDate, t.removed))
      .update(url, description, currentDate, removed)

  def updateGroupMembers(groupName: String, members: List[(String, Boolean)])(implicit s: Session): Unit = {
    GroupMembers.filter(_.groupName === groupName.bind).delete
    members.foreach {
      case (userName, isManager) =>
        GroupMembers insert GroupMember(groupName, userName, isManager)
    }
  }

  def getGroupMembers(groupName: String)(implicit s: Session): List[GroupMember] =
    GroupMembers
      .filter(_.groupName === groupName.bind)
      .sortBy(_.userName)
      .list

  def getGroupsByUserName(userName: String)(implicit s: Session): List[String] =
    GroupMembers
      .filter(_.userName === userName.bind)
      .sortBy(_.groupName)
      .map(_.groupName)
      .list

  def removeUserRelatedData(userName: String)(implicit s: Session): Unit = {
    GroupMembers.filter(_.userName === userName.bind).delete
    Collaborators.filter(_.collaboratorName === userName.bind).delete
  }

  def removeUser(account: Account)(implicit s: Session): Unit = {
    // Remove from GROUP_MEMBER and COLLABORATOR
    removeUserRelatedData(account.userName)
    updateAccount(account.copy(isRemoved = true))

    // call hooks
    PluginRegistry().getAccountHooks.foreach(_.deleted(account.userName))
  }

  def getGroupNames(userName: String)(implicit s: Session): List[String] = {
    List(userName) ++
      Collaborators.filter(_.collaboratorName === userName.bind).sortBy(_.userName).map(_.userName).list.distinct
  }

  /*
   * For account preference
   */
  def getAccountPreference(userName: String)(
    implicit s: Session
  ): Option[AccountPreference] = {
    AccountPreferences filter (_.byPrimaryKey(userName)) firstOption
  }

  def addAccountPreference(userName: String, highlighterTheme: String)(implicit s: Session): Unit = {
    AccountPreferences insert AccountPreference(userName = userName, highlighterTheme = highlighterTheme)
  }

  def updateAccountPreference(userName: String, highlighterTheme: String)(implicit s: Session): Unit = {
    AccountPreferences
      .filter(_.byPrimaryKey(userName))
      .map(t => t.highlighterTheme)
      .update(highlighterTheme)
  }

  def addOrUpdateAccountPreference(userName: String, highlighterTheme: String)(implicit s: Session): Unit = {
    getAccountPreference(userName) match {
      case Some(_) => updateAccountPreference(userName, highlighterTheme)
      case _       => addAccountPreference(userName, highlighterTheme)
    }
  }

}

object AccountService extends AccountService
