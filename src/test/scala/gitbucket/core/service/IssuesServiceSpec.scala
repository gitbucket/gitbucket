package gitbucket.core.service

import gitbucket.core.model._
import gitbucket.core.service.IssuesService._

import org.specs2.mutable.Specification

import java.util.Date


class IssuesServiceSpec extends Specification with ServiceSpecBase {
  "IssuesService" should {
    "getCommitStatues" in { withTestDB { implicit session =>
      val user1 = generateNewUserWithDBRepository("user1","repo1")

      def getCommitStatues = dummyService.getCommitStatues(List(("user1","repo1",1),("user1","repo1",2)))

      getCommitStatues must_== Map.empty

      val now = new java.util.Date()
      val issueId = generateNewIssue("user1","repo1")
      issueId must_== 1

      getCommitStatues must_== Map.empty

      val cs = dummyService.createCommitStatus("user1","repo1","shasha", "default", CommitState.SUCCESS, Some("http://exmple.com/ci"), Some("exampleService"), now, user1)

      getCommitStatues must_== Map.empty

      val (is2, pr2) = generateNewPullRequest("user1/repo1/master","user1/repo1/feature1")
      pr2.issueId must_== 2

      // if there are no statuses, state is none
      getCommitStatues must_== Map.empty

      // if there is a status, state is that
      val cs2 = dummyService.createCommitStatus("user1","repo1","feature1", "default", CommitState.SUCCESS, Some("http://exmple.com/ci"), Some("exampleService"), now, user1)
      getCommitStatues must_== Map(("user1","repo1",2) -> CommitStatusInfo(1,1,Some("default"),Some(CommitState.SUCCESS),Some("http://exmple.com/ci"),Some("exampleService")))

      // if there are two statuses, state is none
      val cs3 = dummyService.createCommitStatus("user1","repo1","feature1", "pend", CommitState.PENDING, Some("http://exmple.com/ci"), Some("exampleService"), now, user1)
      getCommitStatues must_== Map(("user1","repo1",2) -> CommitStatusInfo(2,1,None,None,None,None))

      // get only statuses in query issues
      val (is3, pr3) = generateNewPullRequest("user1/repo1/master","user1/repo1/feature3")
      val cs4 = dummyService.createCommitStatus("user1","repo1","feature3", "none", CommitState.PENDING, None, None, now, user1)
      getCommitStatues must_== Map(("user1","repo1",2) -> CommitStatusInfo(2,1,None,None,None,None))
    } }
  }
}