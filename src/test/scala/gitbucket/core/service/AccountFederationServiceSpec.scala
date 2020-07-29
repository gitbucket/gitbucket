package gitbucket.core.service

import org.scalatest.funspec.AnyFunSpec

class AccountFederationServiceSpec extends AnyFunSpec with ServiceSpecBase {

  describe("getOrCreateFederatedUser") {
    it("should create a federated account if it does not exist") {
      withTestDB { implicit session =>
        val actual = AccountFederationService.getOrCreateFederatedUser(
          "someIssuer",
          "someSubject",
          "dummy@example.com",
          Some("foo"),
          Some("Foo")
        )
        assert(actual.get.userName == "foo")
        assert(actual.get.password == "[DUMMY]")
        assert(actual.get.fullName == "Foo")
        assert(actual.get.mailAddress == "dummy@example.com")
        assert(!actual.get.isAdmin)
        assert(!actual.get.isGroupAccount)
        assert(!actual.get.isRemoved)
      }
    }
    it("should return the federated account") {
      withTestDB { implicit session =>
        generateNewAccount("someUser")
        AccountFederationService.createAccountFederation("someIssuer", "someSubject", "someUser")

        val actual = AccountFederationService.getOrCreateFederatedUser(
          "someIssuer",
          "someSubject",
          "dummy@example.com",
          Some("dummy"),
          Some("dummy")
        )
        assert(actual.get.userName == "someUser")
      }
    }
    it("should return None if the federated account is disabled") {
      withTestDB { implicit session =>
        val user = generateNewAccount("someUser")
        AccountFederationService.createAccountFederation("someIssuer", "someSubject", "someUser")
        AccountService.updateAccount(user.copy(isRemoved = true))

        val actual = AccountFederationService.getOrCreateFederatedUser(
          "someIssuer",
          "someSubject",
          "dummy@example.com",
          Some("dummy"),
          Some("dummy")
        )
        assert(actual.isEmpty)
      }
    }
  }

  describe("getAccountByFederation") {
    it("should return the federated account") {
      withTestDB { implicit session =>
        generateNewAccount("someUser")
        AccountFederationService.createAccountFederation("someIssuer", "someSubject", "someUser")

        val actual = AccountFederationService.getAccountByFederation("someIssuer", "someSubject")
        assert(actual.get.userName == "someUser")
      }
    }
    it("should return the federated account regardless of disabled") {
      withTestDB { implicit session =>
        val user = generateNewAccount("someUser")
        AccountFederationService.createAccountFederation("someIssuer", "someSubject", "someUser")
        AccountService.updateAccount(user.copy(isRemoved = true))

        val actual = AccountFederationService.getAccountByFederation("someIssuer", "someSubject")
        assert(actual.get.userName == "someUser")
      }
    }
    it("should return None if the issuer and the subject did not match") {
      withTestDB { implicit session =>
        val actual = AccountFederationService.getAccountByFederation("someIssuer", "anotherSubject")
        assert(actual.isEmpty)
      }
    }
  }

  describe("findAvailableUserName") {
    it("should return the preferredUserName if it is available") {
      withTestDB { implicit session =>
        assert(AccountFederationService.findAvailableUserName(Some("foo"), "dummy@example.com").contains("foo"))
      }
    }
    it("should return guessed username if only email is given") {
      withTestDB { implicit session =>
        assert(AccountFederationService.findAvailableUserName(None, "bar@example.com").contains("bar"))
      }
    }
    it("should return None if the preferredUserName is already taken") {
      withTestDB { implicit session =>
        generateNewAccount("foo")
        assert(AccountFederationService.findAvailableUserName(Some("foo"), "dummy@example.com").isEmpty)
      }
    }
    it("should return None if guessed username is already taken") {
      withTestDB { implicit session =>
        generateNewAccount("bar")
        assert(AccountFederationService.findAvailableUserName(None, "bar@example.com").isEmpty)
      }
    }
  }

}
