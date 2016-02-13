package gitbucket.core.service

import gitbucket.core.model.Profile._
import profile.simple._

import gitbucket.core.model.{Account, AccessToken}
import gitbucket.core.util.StringUtil

import scala.util.Random


trait AccessTokenService {

  def makeAccessTokenString: String = {
    val bytes = new Array[Byte](20)
    Random.nextBytes(bytes)
    bytes.map("%02x".format(_)).mkString
  }

  def tokenToHash(token: String): String = StringUtil.sha1(token)

  /**
   * @retuen (TokenId, Token)
   */
  def generateAccessToken(userName: String, note: String)(implicit s: Session): (Int, String) = {
    var token: String = null
    var hash: String = null
    do{
      token = makeAccessTokenString
      hash = tokenToHash(token)
    }while(AccessTokens.filter(_.tokenHash === hash.bind).exists.run)
    val newToken = AccessToken(
        userName = userName,
        note = note,
        tokenHash = hash)
    val tokenId = (AccessTokens returning AccessTokens.map(_.accessTokenId)) += newToken
    (tokenId, token)
  }

  def getAccountByAccessToken(token: String)(implicit s: Session): Option[Account] =
    Accounts
      .innerJoin(AccessTokens)
      .filter{ case (ac, t) => (ac.userName === t.userName) && (t.tokenHash === tokenToHash(token).bind) && (ac.removed === false.bind) }
      .map{ case (ac, t) => ac }
      .firstOption

  def getAccessTokens(userName: String)(implicit s: Session): List[AccessToken] =
    AccessTokens.filter(_.userName === userName.bind).sortBy(_.accessTokenId.desc).list

  def deleteAccessToken(userName: String, accessTokenId: Int)(implicit s: Session): Unit =
    AccessTokens filter (t => t.userName === userName.bind && t.accessTokenId === accessTokenId) delete

}

object AccessTokenService extends AccessTokenService
