package gitbucket.core.service

import gitbucket.core.model.Profile._
import profile.simple._

import gitbucket.core.model.{Account, AccessToken}
import gitbucket.core.util.StringUtil
import gitbucket.core.servlet.Database._
import io.getquill._

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
      hash  = tokenToHash(token)
    } while (
      db.run(quote { (hash: String) => query[AccessToken].filter(_.tokenHash == hash).nonEmpty })(hash).head
    )
    val newToken = AccessToken(
        userName = userName,
        note = note,
        tokenHash = hash)

    // TODO Remain Slick code
    val tokenId = (AccessTokens returning AccessTokens.map(_.accessTokenId)) += newToken
    (tokenId, token)
  }

  def getAccountByAccessToken(token: String): Option[Account] =
    db.run(quote { (tokenHash: String) =>
        query[AccessToken].filter(_.tokenHash == tokenHash)
          .join(query[Account]).on { (t, a) => t.userName == a.userName && a.registeredDate == false }
          .map { case (t, a) => a }
    })(tokenToHash(token)).headOption

  def getAccessTokens(userName: String): List[AccessToken] =
    db.run(quote { (userName: String) =>
      query[AccessToken].filter(_.userName == userName).sortBy(_.accessTokenId)(Ord.desc)
    })(userName)

  def deleteAccessToken(userName: String, accessTokenId: Int): Unit =
    db.run(quote { (userName: String, accessTokenId: Int) =>
      query[AccessToken].filter { t => t.userName == userName && t.accessTokenId == accessTokenId }.delete
    })(List((userName, accessTokenId)))

}

object AccessTokenService extends AccessTokenService
