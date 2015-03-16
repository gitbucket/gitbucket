package gitbucket.core.service

import gitbucket.core.model._

import org.specs2.mutable.Specification

import java.util.Date


class AccessTokenServiceSpec extends Specification with ServiceSpecBase {

  "AccessTokenService" should {
    "generateAccessToken" in { withTestDB { implicit session =>
      AccessTokenService.generateAccessToken("root", "note") must be like{
        case (id, token) if id != 0 => ok
      }
    }}

    "getAccessTokens" in { withTestDB { implicit session =>
      val (id, token) = AccessTokenService.generateAccessToken("root", "note")
      val tokenHash = AccessTokenService.tokenToHash(token)

      AccessTokenService.getAccessTokens("root") must be like{
        case List(AccessToken(`id`, "root", `tokenHash`, "note")) => ok
      }
    }}

    "getAccessTokens(root) get root's tokens" in { withTestDB { implicit session =>
      val (id, token) = AccessTokenService.generateAccessToken("root", "note")
      val tokenHash = AccessTokenService.tokenToHash(token)
      val user2 = generateNewAccount("user2")
      AccessTokenService.generateAccessToken("user2", "note2")

      AccessTokenService.getAccessTokens("root") must be like{
        case List(AccessToken(`id`, "root", `tokenHash`, "note")) => ok
      }
    }}

    "deleteAccessToken" in { withTestDB { implicit session =>
      val (id, token) = AccessTokenService.generateAccessToken("root", "note")
      val user2 = generateNewAccount("user2")
      AccessTokenService.generateAccessToken("user2", "note2")

      AccessTokenService.deleteAccessToken("root", id)

      AccessTokenService.getAccessTokens("root") must beEmpty
    }}

    "getAccountByAccessToken" in { withTestDB { implicit session =>
      val (id, token) = AccessTokenService.generateAccessToken("root", "note")
      AccessTokenService.getAccountByAccessToken(token) must beSome.like {
        case user => user.userName must_== "root"
      }
    }}

    "getAccountByAccessToken don't get removed account" in { withTestDB { implicit session =>
      val user2 = generateNewAccount("user2")
      val (id, token) = AccessTokenService.generateAccessToken("user2", "note")
      AccountService.updateAccount(user2.copy(isRemoved=true))

      AccessTokenService.getAccountByAccessToken(token) must beEmpty
    }}

    "generateAccessToken create uniq token" in { withTestDB { implicit session =>
      val tokenIt = List("token1","token1","token1","token2").iterator
      val service = new AccessTokenService{
        override def makeAccessTokenString:String = tokenIt.next
      }

      service.generateAccessToken("root", "note1") must like{
        case (_, "token1") => ok
      }
      service.generateAccessToken("root", "note2") must like{
        case (_, "token2") => ok
      }
    }}

    "when update Account.userName then AccessToken.userName changed" in { withTestDB { implicit session =>
      val user2 = generateNewAccount("user2")
      val (id, token) = AccessTokenService.generateAccessToken("user2", "note")
      import gitbucket.core.model.Profile._
      import profile.simple._
      Accounts.filter(_.userName === "user2".bind).map(_.userName).update("user3")

      AccessTokenService.getAccountByAccessToken(token) must beSome.like {
        case user => user.userName must_== "user3"
      }
    }}
  }
}

