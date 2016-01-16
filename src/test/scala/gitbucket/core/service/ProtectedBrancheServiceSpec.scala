package gitbucket.core.service

import org.specs2.mutable.Specification
import org.eclipse.jgit.transport.ReceiveCommand
import org.eclipse.jgit.lib.ObjectId
import gitbucket.core.model.CommitState
import ProtectedBrancheService.ProtectedBranchInfo


class ProtectedBrancheServiceSpec extends Specification with ServiceSpecBase with ProtectedBrancheService with CommitStatusService {
  val now = new java.util.Date()
  val sha = "0c77148632618b59b6f70004e3084002be2b8804"
  val sha2 = "0c77148632618b59b6f70004e3084002be2b8805"
  "getProtectedBranchInfo" should {
    "empty is disabled" in {
      withTestDB { implicit session =>
        getProtectedBranchInfo("user1", "repo1", "branch") must_== ProtectedBranchInfo.disabled("user1", "repo1")
      }
    }
    "enable and update and disable" in {
      withTestDB { implicit session =>
        generateNewUserWithDBRepository("user1", "repo1")
        enableBranchProtection("user1", "repo1", "branch", false, Nil)
        getProtectedBranchInfo("user1", "repo1", "branch") must_== ProtectedBranchInfo("user1", "repo1", true, Nil, false)
        enableBranchProtection("user1", "repo1", "branch", true, Seq("hoge","huge"))
        getProtectedBranchInfo("user1", "repo1", "branch") must_== ProtectedBranchInfo("user1", "repo1", true, Seq("hoge","huge"), true)
        disableBranchProtection("user1", "repo1", "branch")
        getProtectedBranchInfo("user1", "repo1", "branch") must_== ProtectedBranchInfo.disabled("user1", "repo1")
      }
    }
    "empty contexts is no-include-administrators" in {
      withTestDB { implicit session =>
        generateNewUserWithDBRepository("user1", "repo1")
        enableBranchProtection("user1", "repo1", "branch", false, Nil)
        getProtectedBranchInfo("user1", "repo1", "branch").includeAdministrators must_== false
        enableBranchProtection("user1", "repo1", "branch", true, Nil)
        getProtectedBranchInfo("user1", "repo1", "branch").includeAdministrators must_== false
      }
    }
    "getProtectedBranchList" in {
      withTestDB { implicit session =>
        generateNewUserWithDBRepository("user1", "repo1")
        enableBranchProtection("user1", "repo1", "branch", false, Nil)
        enableBranchProtection("user1", "repo1", "branch2", false, Seq("fuga"))
        enableBranchProtection("user1", "repo1", "branch3", true, Seq("hoge"))
        getProtectedBranchList("user1", "repo1").toSet must_== Set("branch", "branch2", "branch3")
      }
    }
    "getBranchProtectedReason on force push from admin" in {
      withTestDB { implicit session =>
        val rc = new ReceiveCommand(ObjectId.fromString(sha), ObjectId.fromString(sha2), "refs/heads/branch", ReceiveCommand.Type.UPDATE_NONFASTFORWARD)
        generateNewUserWithDBRepository("user1", "repo1")
        getBranchProtectedReason("user1", "repo1", true, rc, "user1") must_== None
        enableBranchProtection("user1", "repo1", "branch", false, Nil)
        getBranchProtectedReason("user1", "repo1", true, rc, "user1") must_== Some("Cannot force-push to a protected branch")
      }
    }
    "getBranchProtectedReason on force push from othre" in {
      withTestDB { implicit session =>
        val rc = new ReceiveCommand(ObjectId.fromString(sha), ObjectId.fromString(sha2), "refs/heads/branch", ReceiveCommand.Type.UPDATE_NONFASTFORWARD)
        generateNewUserWithDBRepository("user1", "repo1")
        getBranchProtectedReason("user1", "repo1", true, rc, "user2") must_== None
        enableBranchProtection("user1", "repo1", "branch", false, Nil)
        getBranchProtectedReason("user1", "repo1", true, rc, "user2") must_== Some("Cannot force-push to a protected branch")
      }
    }
    "getBranchProtectedReason check status on push from othre" in {
      withTestDB { implicit session =>
        val rc = new ReceiveCommand(ObjectId.fromString(sha), ObjectId.fromString(sha2), "refs/heads/branch", ReceiveCommand.Type.UPDATE)
        val user1 = generateNewUserWithDBRepository("user1", "repo1")
        getBranchProtectedReason("user1", "repo1", false, rc, "user2") must_== None
        enableBranchProtection("user1", "repo1", "branch", false, Seq("must"))
        getBranchProtectedReason("user1", "repo1", false, rc, "user2") must_== Some("Required status check \"must\" is expected")
        enableBranchProtection("user1", "repo1", "branch", false, Seq("must", "must2"))
        getBranchProtectedReason("user1", "repo1", false, rc, "user2") must_== Some("2 of 2 required status checks are expected")
        createCommitStatus("user1", "repo1", sha2, "context", CommitState.SUCCESS, None, None, now, user1)
        getBranchProtectedReason("user1", "repo1", false, rc, "user2") must_== Some("2 of 2 required status checks are expected")
        createCommitStatus("user1", "repo1", sha2, "must", CommitState.SUCCESS, None, None, now, user1)
        getBranchProtectedReason("user1", "repo1", false, rc, "user2") must_== Some("Required status check \"must2\" is expected")
        createCommitStatus("user1", "repo1", sha2, "must2", CommitState.SUCCESS, None, None, now, user1)
        getBranchProtectedReason("user1", "repo1", false, rc, "user2") must_== None
      }
    }
    "getBranchProtectedReason check status on push from admin" in {
      withTestDB { implicit session =>
        val rc = new ReceiveCommand(ObjectId.fromString(sha), ObjectId.fromString(sha2), "refs/heads/branch", ReceiveCommand.Type.UPDATE)
        val user1 = generateNewUserWithDBRepository("user1", "repo1")
        getBranchProtectedReason("user1", "repo1", false, rc, "user1") must_== None
        enableBranchProtection("user1", "repo1", "branch", false, Seq("must"))
        getBranchProtectedReason("user1", "repo1", false, rc, "user1") must_== None
        enableBranchProtection("user1", "repo1", "branch", true, Seq("must"))
        getBranchProtectedReason("user1", "repo1", false, rc, "user1") must_== Some("Required status check \"must\" is expected")
        enableBranchProtection("user1", "repo1", "branch", false, Seq("must", "must2"))
        getBranchProtectedReason("user1", "repo1", false, rc, "user1") must_== None
        enableBranchProtection("user1", "repo1", "branch", true, Seq("must", "must2"))
        getBranchProtectedReason("user1", "repo1", false, rc, "user1") must_== Some("2 of 2 required status checks are expected")
        createCommitStatus("user1", "repo1", sha2, "context", CommitState.SUCCESS, None, None, now, user1)
        getBranchProtectedReason("user1", "repo1", false, rc, "user1") must_== Some("2 of 2 required status checks are expected")
        createCommitStatus("user1", "repo1", sha2, "must", CommitState.SUCCESS, None, None, now, user1)
        getBranchProtectedReason("user1", "repo1", false, rc, "user1") must_== Some("Required status check \"must2\" is expected")
        createCommitStatus("user1", "repo1", sha2, "must2", CommitState.SUCCESS, None, None, now, user1)
        getBranchProtectedReason("user1", "repo1", false, rc, "user1") must_== None
      }
    }
  }
  "ProtectedBranchInfo" should {
    "administrator is owner" in {
      withTestDB { implicit session =>
        generateNewUserWithDBRepository("user1", "repo1")
        var x = ProtectedBranchInfo("user1", "repo1", true, Nil, false)
        x.isAdministrator("user1") must_== true
        x.isAdministrator("user2") must_== false
      }
    }
    "administrator is manager" in {
      withTestDB { implicit session =>
        var x = ProtectedBranchInfo("grp1", "repo1", true, Nil, false)
        x.createGroup("grp1", None)
        generateNewAccount("user1")
        generateNewAccount("user2")
        generateNewAccount("user3")

        x.updateGroupMembers("grp1", List("user1"->true, "user2"->false))
        x.isAdministrator("user1") must_== true
        x.isAdministrator("user2") must_== false
        x.isAdministrator("user3") must_== false
      }
    }
    "unSuccessedContexts" in {
      withTestDB { implicit session =>
        val user1 = generateNewUserWithDBRepository("user1", "repo1")
        var x = ProtectedBranchInfo("user1", "repo1", true, List("must"), false)
        x.unSuccessedContexts(sha) must_== Set("must")
        createCommitStatus("user1", "repo1", sha, "context", CommitState.SUCCESS, None, None, now, user1)
        x.unSuccessedContexts(sha) must_== Set("must")
        createCommitStatus("user1", "repo1", sha, "must", CommitState.ERROR, None, None, now, user1)
        x.unSuccessedContexts(sha) must_== Set("must")
        createCommitStatus("user1", "repo1", sha, "must", CommitState.PENDING, None, None, now, user1)
        x.unSuccessedContexts(sha) must_== Set("must")
        createCommitStatus("user1", "repo1", sha, "must", CommitState.FAILURE, None, None, now, user1)
        x.unSuccessedContexts(sha) must_== Set("must")
        createCommitStatus("user1", "repo1", sha, "must", CommitState.SUCCESS, None, None, now, user1)
        x.unSuccessedContexts(sha) must_== Set()
      }
    }
    "unSuccessedContexts when empty" in {
      withTestDB { implicit session =>
        val user1 = generateNewUserWithDBRepository("user1", "repo1")
        var x = ProtectedBranchInfo("user1", "repo1", true, Nil, false)
        val sha = "0c77148632618b59b6f70004e3084002be2b8804"
        x.unSuccessedContexts(sha) must_== Nil
        createCommitStatus("user1", "repo1", sha, "context", CommitState.SUCCESS, None, None, now, user1)
        x.unSuccessedContexts(sha) must_== Nil
      }
    }
    "if disabled, needStatusCheck is false" in {
      withTestDB { implicit session =>
        ProtectedBranchInfo("user1", "repo1", false, Seq("must"), true).needStatusCheck("user1") must_== false
      }
    }
    "needStatusCheck includeAdministrators" in {
      withTestDB { implicit session =>
        ProtectedBranchInfo("user1", "repo1", true, Seq("must"), false).needStatusCheck("user2") must_== true
        ProtectedBranchInfo("user1", "repo1", true, Seq("must"), false).needStatusCheck("user1") must_== false
        ProtectedBranchInfo("user1", "repo1", true, Seq("must"), true ).needStatusCheck("user2") must_== true
        ProtectedBranchInfo("user1", "repo1", true, Seq("must"), true ).needStatusCheck("user1") must_== true
      }
    }
  }
}