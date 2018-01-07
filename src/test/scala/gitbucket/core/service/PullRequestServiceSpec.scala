package gitbucket.core.service

import gitbucket.core.model._
import org.scalatest.FunSpec

class PullRequestServiceSpec extends FunSpec with ServiceSpecBase
  with PullRequestService with IssuesService with AccountService with RepositoryService with CommitsService {

  def swap(r: (Issue, PullRequest)) = (r._2 -> r._1)

  describe("PullRequestService.getPullRequestFromBranch") {
    it("""should
    |return pull request if exists pull request from `branch` to `defaultBranch` and not closed.
    |return pull request if exists pull request from `branch` to other branch and not closed.
    |return None if all pull request is closed""".stripMargin.trim) { withTestDB { implicit se =>
      generateNewUserWithDBRepository("user1", "repo1")
      generateNewUserWithDBRepository("user1", "repo2")
      generateNewUserWithDBRepository("user2", "repo1")
      generateNewPullRequest("user1/repo1/master", "user1/repo1/head2") // not target branch
      generateNewPullRequest("user1/repo1/head1", "user1/repo1/master") // not target branch ( swap from, to )
      generateNewPullRequest("user1/repo1/master", "user2/repo1/head1") // other user
      generateNewPullRequest("user1/repo1/master", "user1/repo2/head1") // other repository
      val r1 = swap(generateNewPullRequest("user1/repo1/master2", "user1/repo1/head1"))
      val r2 = swap(generateNewPullRequest("user1/repo1/master", "user1/repo1/head1"))
      val r3 = swap(generateNewPullRequest("user1/repo1/master4", "user1/repo1/head1"))
      assert(getPullRequestFromBranch("user1", "repo1", "head1", "master") == Some(r2))
      updateClosed("user1", "repo1", r2._1.issueId, true)
      assert(Seq(r1, r2).contains(getPullRequestFromBranch("user1", "repo1", "head1", "master").get))
      updateClosed("user1", "repo1", r1._1.issueId, true)
      updateClosed("user1", "repo1", r3._1.issueId, true)
      assert(getPullRequestFromBranch("user1", "repo1", "head1", "master") == None)
    } }
  }
}
