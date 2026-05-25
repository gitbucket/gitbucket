package gitbucket.core.service

import gitbucket.core.model.*
import gitbucket.core.model.Profile.*
import gitbucket.core.model.Profile.profile.blockingApi.*
import org.scalatest.funsuite.AnyFunSuite

class RepositoryServiceSpec extends AnyFunSuite with ServiceSpecBase with RepositoryService with AccountService {
  test("renameRepository can rename CommitState, ProtectedBranches") {
    withTestDB { implicit session =>
      val tester = generateNewAccount("tester")
      insertRepository("repo", "root", None, false, "main")
      val service = new CommitStatusService with ProtectedBranchService {}
      val id = service.createCommitStatus(
        userName = "root",
        repositoryName = "repo",
        sha = "0e97b8f59f7cdd709418bb59de53f741fd1c1bd7",
        context = "jenkins/test",
        state = CommitState.PENDING,
        targetUrl = Some("http://example.com/target"),
        description = Some("description"),
        creator = tester,
        now = new java.util.Date
      )

      service.enableBranchProtection("root", "repo", "branch", true, true, Seq("must1", "must2"), false, Nil)

      val orgPbi = service.getProtectedBranchInfo("root", "repo", "branch")
      val org = service.getCommitStatus("root", "repo", id).get

      renameRepository("root", "repo", "tester", "repo2")

      val neo = service.getCommitStatus("tester", "repo2", org.commitId, org.context).get
      assert(neo == org.copy(commitStatusId = neo.commitStatusId, repositoryName = "repo2", userName = "tester"))
      assert(
        service.getProtectedBranchInfo("tester", "repo2", "branch") == orgPbi
          .copy(owner = "tester", repository = "repo2")
      )
    }
  }

  test("renameRepository preserves issue assignees") {
    withTestDB { implicit session =>
      generateNewAccount("tester")
      dummyService.insertRepository("repo", "root", None, false, "main")

      val issueId = dummyService.insertIssue(
        owner = "root",
        repository = "repo",
        loginUser = "root",
        title = "issue title",
        content = None,
        milestoneId = None,
        priorityId = None,
        isPullRequest = false
      )

      IssueAssignees insert IssueAssignee("root", "repo", issueId, "tester")

      renameRepository("root", "repo", "root", "repo2")

      assert(dummyService.getIssueAssignees("root", "repo", issueId).isEmpty)
      assert(
        dummyService.getIssueAssignees("root", "repo2", issueId) == List(
          IssueAssignee("root", "repo2", issueId, "tester")
        )
      )
    }
  }

  test("renameRepository preserves issue assignees when changing owners") {
    withTestDB { implicit session =>
      generateNewAccount("tester")
      dummyService.insertRepository("repo", "root", None, false, "main")

      val issueId = dummyService.insertIssue(
        owner = "root",
        repository = "repo",
        loginUser = "root",
        title = "issue title",
        content = None,
        milestoneId = None,
        priorityId = None,
        isPullRequest = false
      )

      IssueAssignees insert IssueAssignee("root", "repo", issueId, "tester")

      renameRepository("root", "repo", "tester", "repo2")

      assert(dummyService.getIssueAssignees("root", "repo", issueId).isEmpty)
      assert(
        dummyService.getIssueAssignees("tester", "repo2", issueId) == List(
          IssueAssignee("tester", "repo2", issueId, "tester")
        )
      )
    }
  }

  test("renameRepository preserves the repository id") {
    withTestDB { implicit session =>
      generateNewAccount("tester")
      insertRepository("repo", "root", None, false, "main")

      val originalId = Repositories.filter(_.byRepository("root", "repo")).first.repositoryId
      assert(originalId != 0)

      renameRepository("root", "repo", "tester", "repo2")

      val renamedId = Repositories.filter(_.byRepository("tester", "repo2")).first.repositoryId
      assert(renamedId == originalId)
    }
  }

  test("new repository created after rename does not reuse the renamed repository's id") {
    withTestDB { implicit session =>
      generateNewAccount("tester")
      insertRepository("repo", "root", None, false, "main")

      val originalId = Repositories.filter(_.byRepository("root", "repo")).first.repositoryId

      renameRepository("root", "repo", "tester", "repo2")

      insertRepository("repo3", "root", None, false, "main")
      val newId = Repositories.filter(_.byRepository("root", "repo3")).first.repositoryId

      assert(newId != 0)
      assert(newId != originalId)
    }
  }
}
