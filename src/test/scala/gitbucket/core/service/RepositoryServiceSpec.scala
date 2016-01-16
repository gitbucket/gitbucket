package gitbucket.core.service

import gitbucket.core.model._
import org.specs2.mutable.Specification


class RepositoryServiceSpec extends Specification with ServiceSpecBase with RepositoryService with AccountService{
  "RepositoryService" should {
    "renameRepository can rename CommitState, ProtectedBranches" in { withTestDB { implicit session =>
      val tester = generateNewAccount("tester")
      createRepository("repo","root",None,false)
      val service = new CommitStatusService with ProtectedBranchService {}
      val id = service.createCommitStatus(
        userName    = "root",
        repositoryName = "repo",
        sha         = "0e97b8f59f7cdd709418bb59de53f741fd1c1bd7",
        context     = "jenkins/test",
        state       = CommitState.PENDING,
        targetUrl   = Some("http://example.com/target"),
        description = Some("description"),
        creator     = tester,
        now         = new java.util.Date)
      service.enableBranchProtection("root", "repo", "branch", true, Seq("must1", "must2"))
      var orgPbi = service.getProtectedBranchInfo("root", "repo", "branch")
      val org = service.getCommitStatus("root","repo", id).get

      renameRepository("root","repo","tester","repo2")

	  val neo = service.getCommitStatus("tester","repo2", org.commitId, org.context).get
      neo must_==
        org.copy(
          commitStatusId=neo.commitStatusId,
          repositoryName="repo2",
          userName="tester")
      service.getProtectedBranchInfo("tester", "repo2", "branch") must_==
        orgPbi.copy(owner="tester", repository="repo2")
    }}
  }
}
