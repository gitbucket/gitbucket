package gitbucket.core.service

import gitbucket.core.model.{Account, GroupMember}
import java.util.Date
import org.scalatest.FunSuite

class AccountServiceSpec extends FunSuite with ServiceSpecBase {

  val RootMailAddress = "root@localhost"

  test("getAllUsers") { withTestDB { implicit session =>
    assert(AccountService.getAllUsers() match {
      case List(Account("root", "root", RootMailAddress, _, true, _, _, _, None, None, false, false)) => true
      case _ => false
    })
  }}

  test("getAccountByUserName") { withTestDB { implicit session =>
    assert(AccountService.getAccountByUserName("root").get.userName == "root")
    assert(AccountService.getAccountByUserName("invalid user name").isEmpty)
  }}

  test("getAccountByMailAddress") { withTestDB { implicit session =>
    assert(AccountService.getAccountByMailAddress(RootMailAddress).isDefined)
  }}

  test("updateLastLoginDate") { withTestDB { implicit session =>
    val root = "root"
    def user() = AccountService.getAccountByUserName(root).getOrElse(sys.error(s"user $root does not exists"))

    assert(user().lastLoginDate.isEmpty)

    val date1 = new Date
    AccountService.updateLastLoginDate(root)
    assert(user().lastLoginDate.get.compareTo(date1) > 0)

    val date2 = new Date
    Thread.sleep(1000)
    AccountService.updateLastLoginDate(root)
    assert(user().lastLoginDate.get.compareTo(date2) > 0)
  }}

  test("updateAccount") { withTestDB { implicit session =>
    val root = "root"
    def user() = AccountService.getAccountByUserName(root).getOrElse(sys.error(s"user $root does not exists"))

    val newAddress = "new mail address"
    AccountService.updateAccount(user().copy(mailAddress = newAddress))
    assert(user().mailAddress == newAddress)
  }}

  test("group") { withTestDB { implicit session =>
    val group1 = "group1"
    val user1 = "root"
    AccountService.createGroup(group1, None)

    assert(AccountService.getGroupMembers(group1) == Nil)
    assert(AccountService.getGroupsByUserName(user1) == Nil)

    AccountService.updateGroupMembers(group1, List((user1, true)))

    assert(AccountService.getGroupMembers(group1) == List(GroupMember(group1, user1, true)))
    assert(AccountService.getGroupsByUserName(user1) == List(group1))

    AccountService.updateGroupMembers(group1, Nil)

    assert(AccountService.getGroupMembers(group1) == Nil)
    assert(AccountService.getGroupsByUserName(user1) == Nil)
  }}
}

