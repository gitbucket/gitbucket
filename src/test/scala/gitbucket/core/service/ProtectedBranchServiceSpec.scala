package gitbucket.core.service

import gitbucket.core.util.GitSpecUtil.*
import org.eclipse.jgit.transport.{ReceivePack, ReceiveCommand}
import org.eclipse.jgit.lib.ObjectId
import gitbucket.core.model.CommitState
import gitbucket.core.service.ProtectedBranchService.{ProtectedBranchReceiveHook, ProtectedBranchInfo}
import org.scalatest.funspec.AnyFunSpec

class ProtectedBranchServiceSpec
    extends AnyFunSpec
    with ServiceSpecBase
    with ProtectedBranchService
    with CommitStatusService {

  val receiveHook = new ProtectedBranchReceiveHook()
  val now = new java.util.Date()
  val sha = "0c77148632618b59b6f70004e3084002be2b8804"
  val sha2 = "0c77148632618b59b6f70004e3084002be2b8805"

  describe("getProtectedBranchInfo") {
    it("should empty is disabled") {
      withTestDB { implicit session =>
        assert(
          getProtectedBranchInfo("user1", "repo1", "branch") == ProtectedBranchInfo.disabled("user1", "repo1", "branch")
        )
      }
    }
    it("should enable and update and disable") {
      withTestDB { implicit session =>
        generateNewUserWithDBRepository("user1", "repo1")
        enableBranchProtection("user1", "repo1", "branch", false, false, Nil, false, Nil)
        assert(
          getProtectedBranchInfo("user1", "repo1", "branch") == ProtectedBranchInfo(
            "user1",
            "repo1",
            "branch",
            true,
            None,
            false,
            None
          )
        )
        enableBranchProtection("user1", "repo1", "branch", true, true, Seq("hoge", "huge"), false, Nil)
        assert(
          getProtectedBranchInfo("user1", "repo1", "branch") == ProtectedBranchInfo(
            "user1",
            "repo1",
            "branch",
            enabled = true,
            contexts = Some(Seq("hoge", "huge")),
            enforceAdmins = true,
            restrictionsUsers = None
          )
        )
        disableBranchProtection("user1", "repo1", "branch")
        assert(
          getProtectedBranchInfo("user1", "repo1", "branch") == ProtectedBranchInfo.disabled("user1", "repo1", "branch")
        )
      }
    }
    it("should empty contexts is include-administrators") {
      withTestDB { implicit session =>
        generateNewUserWithDBRepository("user1", "repo1")
        enableBranchProtection("user1", "repo1", "branch", false, false, Nil, false, Nil)
        assert(!getProtectedBranchInfo("user1", "repo1", "branch").enforceAdmins)
        enableBranchProtection("user1", "repo1", "branch", true, false, Nil, false, Nil)
        assert(getProtectedBranchInfo("user1", "repo1", "branch").enforceAdmins)
      }
    }
    it("getProtectedBranchList") {
      withTestDB { implicit session =>
        generateNewUserWithDBRepository("user1", "repo1")
        enableBranchProtection("user1", "repo1", "branch", false, false, Nil, false, Nil)
        enableBranchProtection("user1", "repo1", "branch2", false, false, Seq("fuga"), false, Nil)
        enableBranchProtection("user1", "repo1", "branch3", true, false, Seq("hoge"), false, Nil)
        assert(getProtectedBranchList("user1", "repo1").toSet == Set("branch", "branch2", "branch3"))
      }
    }
    it("getBranchProtectedReason on force push from admin") {
      withTestDB { implicit session =>
        withTestRepository { git =>
          val rp = new ReceivePack(git.getRepository)
          rp.setAllowNonFastForwards(true)
          val rc = new ReceiveCommand(
            ObjectId.fromString(sha),
            ObjectId.fromString(sha2),
            "refs/heads/branch",
            ReceiveCommand.Type.UPDATE_NONFASTFORWARD
          )
          generateNewUserWithDBRepository("user1", "repo1")
          assert(receiveHook.preReceive("user1", "repo1", rp, rc, "user1", false).isEmpty)
          enableBranchProtection("user1", "repo1", "branch", false, false, Nil, false, Nil)
          assert(
            receiveHook
              .preReceive("user1", "repo1", rp, rc, "user1", false)
              .contains("Cannot force-push to a protected branch")
          )
        }
      }
    }
    it("getBranchProtectedReason on force push from other") {
      withTestDB { implicit session =>
        withTestRepository { git =>
          val rp = new ReceivePack(git.getRepository)
          rp.setAllowNonFastForwards(true)
          val rc = new ReceiveCommand(
            ObjectId.fromString(sha),
            ObjectId.fromString(sha2),
            "refs/heads/branch",
            ReceiveCommand.Type.UPDATE_NONFASTFORWARD
          )
          generateNewUserWithDBRepository("user1", "repo1")
          assert(receiveHook.preReceive("user1", "repo1", rp, rc, "user2", false).isEmpty)
          enableBranchProtection("user1", "repo1", "branch", false, false, Nil, false, Nil)
          assert(
            receiveHook
              .preReceive("user1", "repo1", rp, rc, "user2", false)
              .contains("Cannot force-push to a protected branch")
          )
        }
      }
    }
    it("getBranchProtectedReason check status on push from other") {
      withTestDB { implicit session =>
        withTestRepository { git =>
          val rp = new ReceivePack(git.getRepository)
          rp.setAllowNonFastForwards(false)
          val rc = new ReceiveCommand(
            ObjectId.fromString(sha),
            ObjectId.fromString(sha2),
            "refs/heads/branch",
            ReceiveCommand.Type.UPDATE
          )
          val user1 = generateNewUserWithDBRepository("user1", "repo1")
          assert(receiveHook.preReceive("user1", "repo1", rp, rc, "user2", false).isEmpty)
          enableBranchProtection("user1", "repo1", "branch", false, true, Seq("must"), false, Nil)
          assert(
            receiveHook
              .preReceive("user1", "repo1", rp, rc, "user2", false)
              .contains("Required status check \"must\" is expected")
          )
          enableBranchProtection("user1", "repo1", "branch", false, true, Seq("must", "must2"), false, Nil)
          assert(
            receiveHook
              .preReceive("user1", "repo1", rp, rc, "user2", false)
              .contains("2 of 2 required status checks are expected")
          )
          createCommitStatus("user1", "repo1", sha2, "context", CommitState.SUCCESS, None, None, now, user1)
          assert(
            receiveHook
              .preReceive("user1", "repo1", rp, rc, "user2", false)
              .contains("2 of 2 required status checks are expected")
          )
          createCommitStatus("user1", "repo1", sha2, "must", CommitState.SUCCESS, None, None, now, user1)
          assert(
            receiveHook
              .preReceive("user1", "repo1", rp, rc, "user2", false)
              .contains("Required status check \"must2\" is expected")
          )
          createCommitStatus("user1", "repo1", sha2, "must2", CommitState.SUCCESS, None, None, now, user1)
          assert(receiveHook.preReceive("user1", "repo1", rp, rc, "user2", false).isEmpty)
        }
      }
    }
    it("getBranchProtectedReason check status on push from admin") {
      withTestDB { implicit session =>
        withTestRepository { git =>
          val rp = new ReceivePack(git.getRepository)
          rp.setAllowNonFastForwards(false)
          val rc = new ReceiveCommand(
            ObjectId.fromString(sha),
            ObjectId.fromString(sha2),
            "refs/heads/branch",
            ReceiveCommand.Type.UPDATE
          )
          val user1 = generateNewUserWithDBRepository("user1", "repo1")
          assert(receiveHook.preReceive("user1", "repo1", rp, rc, "user1", false).isEmpty)
          enableBranchProtection("user1", "repo1", "branch", false, true, Seq("must"), false, Nil)
          assert(receiveHook.preReceive("user1", "repo1", rp, rc, "user1", false).isEmpty)
          enableBranchProtection("user1", "repo1", "branch", true, true, Seq("must"), false, Nil)
          assert(
            receiveHook
              .preReceive("user1", "repo1", rp, rc, "user1", false)
              .contains("Required status check \"must\" is expected")
          )
          enableBranchProtection("user1", "repo1", "branch", false, true, Seq("must", "must2"), false, Nil)
          assert(receiveHook.preReceive("user1", "repo1", rp, rc, "user1", false).isEmpty)
          enableBranchProtection("user1", "repo1", "branch", true, true, Seq("must", "must2"), false, Nil)
          assert(
            receiveHook
              .preReceive("user1", "repo1", rp, rc, "user1", false)
              .contains("2 of 2 required status checks are expected")
          )
          createCommitStatus("user1", "repo1", sha2, "context", CommitState.SUCCESS, None, None, now, user1)
          assert(
            receiveHook
              .preReceive("user1", "repo1", rp, rc, "user1", false)
              .contains("2 of 2 required status checks are expected")
          )
          createCommitStatus("user1", "repo1", sha2, "must", CommitState.SUCCESS, None, None, now, user1)
          assert(
            receiveHook
              .preReceive("user1", "repo1", rp, rc, "user1", false)
              .contains("Required status check \"must2\" is expected")
          )
          createCommitStatus("user1", "repo1", sha2, "must2", CommitState.SUCCESS, None, None, now, user1)
          assert(receiveHook.preReceive("user1", "repo1", rp, rc, "user1", false).isEmpty)
        }
      }
    }
    it("should restrict push to allowed users only") {
      withTestDB { implicit session =>
        withTestRepository { git =>
          val rp = new ReceivePack(git.getRepository)
          rp.setAllowNonFastForwards(true)
          val rc = new ReceiveCommand(
            ObjectId.fromString(sha),
            ObjectId.fromString(sha2),
            "refs/heads/branch",
            ReceiveCommand.Type.UPDATE
          )
          generateNewUserWithDBRepository("user1", "repo1")
          generateNewAccount("user2")
          enableBranchProtection("user1", "repo1", "branch", false, false, Nil, true, Seq("user2"))
          assert(receiveHook.preReceive("user1", "repo1", rp, rc, "user2", false).isEmpty)
          assert(
            receiveHook
              .preReceive("user1", "repo1", rp, rc, "user3", false)
              .contains("You do not have permission to push to this branch")
          )
        }
      }
    }
  }
  describe("ProtectedBranchInfo") {
    it("administrator is owner") {
      withTestDB { implicit session =>
        generateNewUserWithDBRepository("user1", "repo1")
        val x = ProtectedBranchInfo(
          "user1",
          "repo1",
          "branch",
          enabled = true,
          contexts = Some(Nil),
          enforceAdmins = false,
          restrictionsUsers = None
        )
        assert(x.isAdministrator("user1"))
        assert(!x.isAdministrator("user2"))
      }
    }
    it("administrator is manager") {
      withTestDB { implicit session =>
        val x = ProtectedBranchInfo(
          "grp1",
          "repo1",
          "branch",
          enabled = true,
          contexts = Some(Nil),
          enforceAdmins = false,
          restrictionsUsers = None
        )
        x.createGroup("grp1", None, None)
        generateNewAccount("user1")
        generateNewAccount("user2")
        generateNewAccount("user3")

        x.updateGroupMembers("grp1", List("user1" -> true, "user2" -> false))
        assert(x.isAdministrator("user1"))
        assert(!x.isAdministrator("user2"))
        assert(!x.isAdministrator("user3"))
      }
    }
    it("unSuccessedContexts") {
      withTestDB { implicit session =>
        val user1 = generateNewUserWithDBRepository("user1", "repo1")
        val x = ProtectedBranchInfo(
          "user1",
          "repo1",
          "branch",
          enabled = true,
          contexts = Some(List("must")),
          enforceAdmins = false,
          restrictionsUsers = None
        )
        assert(x.unSuccessedContexts(sha) == Set("must"))
        createCommitStatus("user1", "repo1", sha, "context", CommitState.SUCCESS, None, None, now, user1)
        assert(x.unSuccessedContexts(sha) == Set("must"))
        createCommitStatus("user1", "repo1", sha, "must", CommitState.ERROR, None, None, now, user1)
        assert(x.unSuccessedContexts(sha) == Set("must"))
        createCommitStatus("user1", "repo1", sha, "must", CommitState.PENDING, None, None, now, user1)
        assert(x.unSuccessedContexts(sha) == Set("must"))
        createCommitStatus("user1", "repo1", sha, "must", CommitState.FAILURE, None, None, now, user1)
        assert(x.unSuccessedContexts(sha) == Set("must"))
        createCommitStatus("user1", "repo1", sha, "must", CommitState.SUCCESS, None, None, now, user1)
        assert(x.unSuccessedContexts(sha) == Set())
      }
    }
    it("unSuccessedContexts when empty") {
      withTestDB { implicit session =>
        val user1 = generateNewUserWithDBRepository("user1", "repo1")
        val x = ProtectedBranchInfo(
          "user1",
          "repo1",
          "branch",
          enabled = true,
          contexts = Some(Nil),
          enforceAdmins = false,
          restrictionsUsers = None
        )
        val sha = "0c77148632618b59b6f70004e3084002be2b8804"
        assert(x.unSuccessedContexts(sha) == Set())
        createCommitStatus("user1", "repo1", sha, "context", CommitState.SUCCESS, None, None, now, user1)
        assert(x.unSuccessedContexts(sha) == Set())
      }
    }
    it("if disabled, needStatusCheck is false") {
      withTestDB { implicit session =>
        assert(
          !ProtectedBranchInfo(
            "user1",
            "repo1",
            "branch",
            enabled = false,
            contexts = Some(Seq("must")),
            enforceAdmins = true,
            restrictionsUsers = None
          ).needStatusCheck("user1")
        )
      }
    }
    it("needStatusCheck includeAdministrators") {
      withTestDB { implicit session =>
        assert(
          ProtectedBranchInfo(
            "user1",
            "repo1",
            "branch",
            enabled = true,
            contexts = Some(Seq("must")),
            enforceAdmins = false,
            restrictionsUsers = None
          ).needStatusCheck("user2")
        )
        assert(
          !ProtectedBranchInfo(
            "user1",
            "repo1",
            "branch",
            enabled = true,
            contexts = Some(Seq("must")),
            enforceAdmins = false,
            restrictionsUsers = None
          ).needStatusCheck("user1")
        )
        assert(
          ProtectedBranchInfo(
            "user1",
            "repo1",
            "branch",
            enabled = true,
            contexts = Some(Seq("must")),
            enforceAdmins = true,
            restrictionsUsers = None
          ).needStatusCheck("user2")
        )
        assert(
          ProtectedBranchInfo(
            "user1",
            "repo1",
            "branch",
            enabled = true,
            contexts = Some(Seq("must")),
            enforceAdmins = true,
            restrictionsUsers = None
          ).needStatusCheck("user1")
        )
      }
    }
  }
}
