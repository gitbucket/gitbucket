package gitbucket.core.service

import gitbucket.core.model.{Account, GroupMember}
import java.util.Date
import org.scalatest.funsuite.AnyFunSuite

class AccountServiceSpec extends AnyFunSuite with ServiceSpecBase {

  val RootMailAddress = "root@localhost"

  test("getAllUsers") {
    withTestDB { implicit session =>
      assert(AccountService.getAllUsers() match {
        case List(Account(_, "root", "root", RootMailAddress, _, true, _, _, _, None, None, false, false, None)) =>
          true
        case _ => false
      })
    }
  }

  test("getAccountByUserName") {
    withTestDB { implicit session =>
      assert(AccountService.getAccountByUserName("root").get.userName == "root")
      assert(AccountService.getAccountByUserName("invalid user name").isEmpty)
    }
  }

  test("getAccountById") {
    withTestDB { implicit session =>
      val root = AccountService.getAccountByUserName("root").get

      assert(AccountService.getAccountById(root.accountId).get.userName == "root")
      assert(AccountService.getAccountById(-1L).isEmpty)
    }
  }

  test("getAccountById excludes removed accounts by default but can include them") {
    withTestDB { implicit session =>
      val created =
        AccountService.createAccount("removed_id_user", "password", "Removed", "removed@example.com", false, None, None)
      AccountService.updateAccount(created.copy(isRemoved = true))

      assert(AccountService.getAccountById(created.accountId).isEmpty)
      assert(AccountService.getAccountById(created.accountId, includeRemoved = true).isDefined)
    }
  }

  test("getAccountByMailAddress") {
    withTestDB { implicit session =>
      assert(AccountService.getAccountByMailAddress(RootMailAddress).isDefined)
    }
  }

  test("updateLastLoginDate") {
    withTestDB { implicit session =>
      val root = "root"
      def user() = AccountService.getAccountByUserName(root).getOrElse(sys.error(s"user $root does not exists"))

      assert(user().lastLoginDate.isEmpty)

      val date1 = new Date
      Thread.sleep(1000)
      AccountService.updateLastLoginDate(root)
      assert(user().lastLoginDate.get.compareTo(date1) > 0)

      val date2 = new Date
      Thread.sleep(1000)
      AccountService.updateLastLoginDate(root)
      assert(user().lastLoginDate.get.compareTo(date2) > 0)
    }
  }

  test("updateAccount") {
    withTestDB { implicit session =>
      val root = "root"
      def user() = AccountService.getAccountByUserName(root).getOrElse(sys.error(s"user $root does not exists"))

      val newAddress = "new mail address"
      AccountService.updateAccount(user().copy(mailAddress = newAddress))
      assert(user().mailAddress == newAddress)

      val newUrl = Some("http://new.url.example/path")
      AccountService.updateAccount(user().copy(url = newUrl))
      assert(user().url == newUrl)

      val newDescription = Some("http://new.url.example/path")
      AccountService.updateAccount(user().copy(description = newDescription))
      assert(user().description == newDescription)
    }
  }

  test("createAccount assigns a non-zero unique account id") {
    withTestDB { implicit session =>
      val created1 = AccountService.createAccount("user1", "password", "User 1", "user1@example.com", false, None, None)
      val created2 = AccountService.createAccount("user2", "password", "User 2", "user2@example.com", false, None, None)

      assert(created1.accountId != 0)
      assert(created2.accountId != 0)
      assert(created1.accountId != created2.accountId)

      assert(AccountService.getAccountByUserName("user1").get.accountId == created1.accountId)
      assert(AccountService.getAccountByUserName("user2").get.accountId == created2.accountId)
    }
  }

  test("updateAccount preserves the account id") {
    withTestDB { implicit session =>
      val created = AccountService.createAccount("user1", "password", "User 1", "user1@example.com", false, None, None)

      AccountService.updateAccount(created.copy(fullName = "Renamed"))

      val updated = AccountService.getAccountByUserName("user1").get
      assert(updated.fullName == "Renamed")
      assert(updated.accountId == created.accountId)
    }
  }

  test("createGroup assigns a non-zero account id") {
    withTestDB { implicit session =>
      val group = AccountService.createGroup("id-group", None, None)

      assert(group.accountId != 0)
      assert(AccountService.getAccountByUserName("id-group").get.accountId == group.accountId)
    }
  }

  test("group") {
    withTestDB { implicit session =>
      val group1 = "group1"
      val user1 = "root"
      AccountService.createGroup(group1, None, None)

      assert(AccountService.getGroupMembers(group1) == Nil)
      assert(AccountService.getGroupsByUserName(user1) == Nil)

      AccountService.updateGroupMembers(group1, List((user1, true)))

      assert(AccountService.getGroupMembers(group1) == List(GroupMember(group1, user1, true)))
      assert(AccountService.getGroupsByUserName(user1) == List(group1))

      AccountService.updateGroupMembers(group1, Nil)

      assert(AccountService.getGroupMembers(group1) == Nil)
      assert(AccountService.getGroupsByUserName(user1) == Nil)
    }
  }

  test("createGroup save description") {
    withTestDB { implicit session =>
      AccountService.createGroup("some-group", Some("some clever description"), None)
      val maybeGroup = AccountService.getAccountByUserName("some-group")

      assert(maybeGroup.flatMap(_.description) == Some("some clever description"))
    }
  }

  test("updateGroup save description") {
    withTestDB { implicit session =>
      AccountService.createGroup("a-group", None, None)

      AccountService.updateGroup("a-group", Some("new description"), None, false)

      val group = AccountService.getAccountByUserName("a-group")
      assert(group.flatMap(_.description) == Some("new description"))
    }
  }
}
