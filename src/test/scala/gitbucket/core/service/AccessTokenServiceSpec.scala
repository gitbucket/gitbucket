package gitbucket.core.service

import gitbucket.core.model._
import org.scalatest.FunSuite
import gitbucket.core.model.Profile._
import gitbucket.core.model.Profile.profile.blockingApi._

class AccessTokenServiceSpec extends FunSuite with ServiceSpecBase {

  test("generateAccessToken") {
    withTestDB { implicit session =>
      assert(AccessTokenService.generateAccessToken("root", "note") match {
        case (id, token) => id != 0
      })
    }
  }

  test("getAccessTokens") {
    withTestDB { implicit session =>
      val (id, token) = AccessTokenService.generateAccessToken("root", "note")
      val tokenHash = AccessTokenService.tokenToHash(token)

      assert(AccessTokenService.getAccessTokens("root") == List(AccessToken(`id`, "root", `tokenHash`, "note")))
    }
  }

  test("getAccessTokens(root) get root's tokens") {
    withTestDB { implicit session =>
      val (id, token) = AccessTokenService.generateAccessToken("root", "note")
      val tokenHash = AccessTokenService.tokenToHash(token)
      val user2 = generateNewAccount("user2")
      AccessTokenService.generateAccessToken("user2", "note2")

      assert(AccessTokenService.getAccessTokens("root") == List(AccessToken(`id`, "root", `tokenHash`, "note")))
    }
  }

  test("deleteAccessToken") {
    withTestDB { implicit session =>
      val (id, token) = AccessTokenService.generateAccessToken("root", "note")
      val user2 = generateNewAccount("user2")
      AccessTokenService.generateAccessToken("user2", "note2")

      AccessTokenService.deleteAccessToken("root", id)

      assert(AccessTokenService.getAccessTokens("root").isEmpty)
    }
  }

  test("getAccountByAccessToken") {
    withTestDB { implicit session =>
      val (id, token) = AccessTokenService.generateAccessToken("root", "note")
      assert(AccessTokenService.getAccountByAccessToken(token) match {
        case Some(user) => user.userName == "root"
        case _          => fail()
      })
    }
  }

  test("getAccountByAccessToken don't get removed account") {
    withTestDB { implicit session =>
      val user2 = generateNewAccount("user2")
      val (id, token) = AccessTokenService.generateAccessToken("user2", "note")
      AccountService.updateAccount(user2.copy(isRemoved = true))

      assert(AccessTokenService.getAccountByAccessToken(token).isEmpty)
    }
  }

  test("generateAccessToken create uniq token") {
    withTestDB { implicit session =>
      val tokenIt = List("token1", "token1", "token1", "token2").iterator
      val service = new AccessTokenService {
        override def makeAccessTokenString: String = tokenIt.next
      }

      assert(service.generateAccessToken("root", "note1")._2 == "token1")
      assert(service.generateAccessToken("root", "note2")._2 == "token2")
    }
  }

  test("when update Account.userName then AccessToken.userName changed") {
    withTestDB { implicit session =>
      val user2 = generateNewAccount("user2")
      val (id, token) = AccessTokenService.generateAccessToken("user2", "note")

      Accounts.filter(_.userName === "user2".bind).map(_.userName).update("user3")

      assert(AccessTokenService.getAccountByAccessToken(token) match {
        case Some(user) => user.userName == "user3"
        case _          => fail()
      })
    }
  }
}
