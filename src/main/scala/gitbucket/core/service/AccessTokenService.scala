package gitbucket.core.service

import gitbucket.core.model.Profile._
import gitbucket.core.model.Profile.profile.blockingApi._
import gitbucket.core.model.{AccessToken, Account}
import gitbucket.core.util.StringUtil

import java.security.SecureRandom

trait AccessTokenService {

  def makeAccessTokenString: String = {
    val bytes = new Array[Byte](20)
    AccessTokenService.secureRandom.nextBytes(bytes)
    bytes.map("%02x".format(_)).mkString
  }

  def tokenToHash(token: String): String = StringUtil.sha1(token)

  /**
   * @return (TokenId, Token)
   */
  def generateAccessToken(userName: String, note: String)(implicit s: Session): (Int, String) = {
    var token: String = null
    var hash: String = null

    do {
      token = makeAccessTokenString
      hash = tokenToHash(token)
    } while (AccessTokens.filter(_.tokenHash === hash.bind).exists.run)

    val newToken = AccessToken(userName = userName, note = note, tokenHash = hash)
    val tokenId = (AccessTokens returning AccessTokens.map(_.accessTokenId)) insert newToken
    (tokenId, token)
  }

  def getAccountByAccessToken(token: String)(implicit s: Session): Option[Account] =
    Accounts
      .join(AccessTokens)
      .filter {
        case (ac, t) =>
          (ac.userName === t.userName) && (t.tokenHash === tokenToHash(token).bind) && (ac.removed === false.bind)
      }
      .map { case (ac, t) => ac }
      .firstOption

  def getAccessTokens(userName: String)(implicit s: Session): List[AccessToken] =
    AccessTokens.filter(_.userName === userName.bind).sortBy(_.accessTokenId.desc).list

  def hasAccessToken(userName: String)(implicit s: Session): Boolean =
    AccessTokens.filter(_.userName === userName.bind).exists.run

  def deleteAccessToken(userName: String, accessTokenId: Int)(implicit s: Session): Unit =
    AccessTokens filter (t => t.userName === userName.bind && t.accessTokenId === accessTokenId) delete

}

object AccessTokenService extends AccessTokenService {
  private val secureRandom = new SecureRandom()
}
