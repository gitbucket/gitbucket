package gitbucket.core.service

import gitbucket.core.model._
import gitbucket.core.service.IssuesService._
import org.scalatest.FunSuite

class IssuesServiceSpec extends FunSuite with ServiceSpecBase {
  test("getCommitStatues") {
    withTestDB { implicit session =>
      val user1 = generateNewUserWithDBRepository("user1", "repo1")

      def getCommitStatues(issueId: Int) = dummyService.getCommitStatues("user1", "repo1", issueId)

      assert(getCommitStatues(1) == None)

      val now = new java.util.Date()
      val issueId = generateNewIssue("user1", "repo1")
      assert(issueId == 1)

      assert(getCommitStatues(1) == None)

      val cs = dummyService.createCommitStatus(
        "user1",
        "repo1",
        "shasha",
        "default",
        CommitState.SUCCESS,
        Some("http://exmple.com/ci"),
        Some("exampleService"),
        now,
        user1
      )

      assert(getCommitStatues(1) == None)

      val (is2, pr2) = generateNewPullRequest("user1/repo1/master", "user1/repo1/feature1", loginUser = "root")
      assert(pr2.issueId == 2)

      // if there are no statuses, state is none
      assert(getCommitStatues(2) == None)

      // if there is a status, state is that
      val cs2 = dummyService.createCommitStatus(
        "user1",
        "repo1",
        "feature1",
        "default",
        CommitState.SUCCESS,
        Some("http://exmple.com/ci"),
        Some("exampleService"),
        now,
        user1
      )
      assert(
        getCommitStatues(2) == Some(
          CommitStatusInfo(
            1,
            1,
            Some("default"),
            Some(CommitState.SUCCESS),
            Some("http://exmple.com/ci"),
            Some("exampleService")
          )
        )
      )

      // if there are two statuses, state is none
      val cs3 = dummyService.createCommitStatus(
        "user1",
        "repo1",
        "feature1",
        "pend",
        CommitState.PENDING,
        Some("http://exmple.com/ci"),
        Some("exampleService"),
        now,
        user1
      )
      assert(getCommitStatues(2) == Some(CommitStatusInfo(2, 1, None, None, None, None)))

      // get only statuses in query issues
      val (is3, pr3) = generateNewPullRequest("user1/repo1/master", "user1/repo1/feature3", loginUser = "root")
      val cs4 = dummyService.createCommitStatus(
        "user1",
        "repo1",
        "feature3",
        "none",
        CommitState.PENDING,
        None,
        None,
        now,
        user1
      )
      assert(getCommitStatues(2) == Some(CommitStatusInfo(2, 1, None, None, None, None)))
    }
  }
}
