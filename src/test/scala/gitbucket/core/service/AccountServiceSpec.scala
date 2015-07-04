package gitbucket.core.service

import gitbucket.core.model.{Account, GroupMember}
import org.specs2.mutable.Specification
import java.util.Date

class AccountServiceSpec extends Specification with ServiceSpecBase {

  "AccountService" should {
    val RootMailAddress = "root@localhost"

    "getAllUsers" in { withTestDB { implicit session =>
      AccountService.getAllUsers() must be like{
        case List(Account("root", "root", RootMailAddress, _, true, _, _, _, None, None, false, false, None)) => ok
      }
    }}

    "getAccountByUserName" in { withTestDB { implicit session =>
      AccountService.getAccountByUserName("root") must beSome.like {
        case user => user.userName must_== "root"
      }

      AccountService.getAccountByUserName("invalid user name") must beNone
    }}

    "getAccountByMailAddress" in { withTestDB { implicit session =>
      AccountService.getAccountByMailAddress(RootMailAddress) must beSome
    }}

    "updateLastLoginDate" in { withTestDB { implicit session =>
      val root = "root"
      def user() =
        AccountService.getAccountByUserName(root).getOrElse(sys.error(s"user $root does not exists"))

      user().lastLoginDate must beNone
      val date1 = new Date
      AccountService.updateLastLoginDate(root)
      user().lastLoginDate must beSome.like{ case date =>
        date must be_>(date1)
      }
      val date2 = new Date
      Thread.sleep(1000)
      AccountService.updateLastLoginDate(root)
      user().lastLoginDate must beSome.like{ case date =>
        date must be_>(date2)
      }
    }}

    "updateAccount" in { withTestDB { implicit session =>
      val root = "root"
      def user() =
        AccountService.getAccountByUserName(root).getOrElse(sys.error(s"user $root does not exists"))

      val newAddress = "new mail address"
      AccountService.updateAccount(user().copy(mailAddress = newAddress))
      user().mailAddress must_== newAddress
    }}

    "group" in { withTestDB { implicit session =>
      val group1 = "group1"
      val user1 = "root"
      AccountService.createGroup(group1, None, None)

      AccountService.getGroupMembers(group1) must_== Nil
      AccountService.getGroupsByUserName(user1) must_== Nil

      AccountService.updateGroupMembers(group1, List((user1, true)))

      AccountService.getGroupMembers(group1) must_== List(GroupMember(group1, user1, true))
      AccountService.getGroupsByUserName(user1) must_== List(group1)

      AccountService.updateGroupMembers(group1, Nil)

      AccountService.getGroupMembers(group1) must_== Nil
      AccountService.getGroupsByUserName(user1) must_== Nil
    }}

    "createGroup" should { withTestDB { implicit session =>
      "save description" in {
        AccountService.createGroup("some-group", None, Some("some clever description"))
        val maybeGroup = AccountService.getAccountByUserName("some-group")

        maybeGroup must beSome.like {
          case account => account.groupDescription must beSome("some clever description")
        }
      }
    }}

    "updateGroup" should { withTestDB { implicit session =>
      "save description" in {
        AccountService.createGroup("a-group", None, None)

        AccountService.updateGroup("a-group", None, Some("new description"), false)

        val group = AccountService.getAccountByUserName("a-group")
        group must beSome.like {
          case account => account.groupDescription must beSome("new description")
        }
      }
    }}
  }
}
