package service

import org.specs2.mutable.Specification
import java.util.Date

class AccountServiceSpec extends Specification with SpecBase {

  "AccountService" should {
    val RootMailAddress = "root@localhost"

    "getAllUsers" in { withTestDB{
      AccountService.getAllUsers must be like{
        case List(model.Account("root", RootMailAddress, _, true, _, _, _, None, None, false)) => ok
      }
    }}

    "getAccountByUserName" in { withTestDB{
      AccountService.getAccountByUserName("root") must beSome.like{
        case user => user.userName must_== "root"
      }

      AccountService.getAccountByUserName("invalid user name") must beNone
    }}

    "getAccountByMailAddress" in { withTestDB{
      AccountService.getAccountByMailAddress(RootMailAddress) must beSome
    }}

    "updateLastLoginDate" in { withTestDB{
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

    "updateAccount" in { withTestDB{
      val root = "root"
      def user() =
        AccountService.getAccountByUserName(root).getOrElse(sys.error(s"user $root does not exists"))

      val newAddress = "new mail address"
      AccountService.updateAccount(user().copy(mailAddress = newAddress))
      user().mailAddress must_== newAddress
    }}

    "group" in { withTestDB {
      val group1 = "group1"
      val user1 = "root"
      AccountService.createGroup(group1, None)

      AccountService.getGroupMembers(group1) must_== Nil
      AccountService.getGroupsByUserName(user1) must_== Nil

      AccountService.updateGroupMembers(group1, List(user1))

      AccountService.getGroupMembers(group1) must_== List(user1)
      AccountService.getGroupsByUserName(user1) must_== List(group1)

      AccountService.updateGroupMembers(group1, Nil)

      AccountService.getGroupMembers(group1) must_== Nil
      AccountService.getGroupsByUserName(user1) must_== Nil
    }}
  }
}
