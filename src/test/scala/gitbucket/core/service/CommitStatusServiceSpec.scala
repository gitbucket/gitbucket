package gitbucket.core.service

import gitbucket.core.model._
import gitbucket.core.model.Profile._
import gitbucket.core.model.Profile.profile._
import gitbucket.core.model.Profile.profile.blockingApi._
import org.scalatest.funsuite.AnyFunSuite

class CommitStatusServiceSpec
    extends AnyFunSuite
    with ServiceSpecBase
    with CommitStatusService
    with RepositoryService
    with AccountService {
  val now = new java.util.Date()
  val fixture1 = CommitStatus(
    userName = "root",
    repositoryName = "repo",
    commitId = "0e97b8f59f7cdd709418bb59de53f741fd1c1bd7",
    context = "jenkins/test",
    creator = "tester",
    state = CommitState.PENDING,
    targetUrl = Some("http://example.com/target"),
    description = Some("description"),
    updatedDate = now,
    registeredDate = now
  )
  def findById(id: Int)(implicit s: Session) = CommitStatuses.filter(_.byPrimaryKey(id)).firstOption
  def generateFixture1(tester: Account)(implicit s: Session) =
    createCommitStatus(
      userName = fixture1.userName,
      repositoryName = fixture1.repositoryName,
      sha = fixture1.commitId,
      context = fixture1.context,
      state = fixture1.state,
      targetUrl = fixture1.targetUrl,
      description = fixture1.description,
      creator = tester,
      now = fixture1.registeredDate
    )
  test("createCommitState can insert and update") {
    withTestDB { implicit session =>
      val tester = generateNewAccount(fixture1.creator)
      insertRepository(fixture1.repositoryName, fixture1.userName, None, false)
      val id = generateFixture1(tester: Account)
      assert(
        getCommitStatus(fixture1.userName, fixture1.repositoryName, id) == Some(fixture1.copy(commitStatusId = id))
      )
      // other one can update
      val tester2 = generateNewAccount("tester2")
      val time2 = new java.util.Date()
      val id2 = createCommitStatus(
        userName = fixture1.userName,
        repositoryName = fixture1.repositoryName,
        sha = fixture1.commitId,
        context = fixture1.context,
        state = CommitState.SUCCESS,
        targetUrl = Some("http://example.com/target2"),
        description = Some("description2"),
        creator = tester2,
        now = time2
      )
      assert(
        getCommitStatus(fixture1.userName, fixture1.repositoryName, id2) == Some(
          fixture1.copy(
            commitStatusId = id,
            creator = "tester2",
            state = CommitState.SUCCESS,
            targetUrl = Some("http://example.com/target2"),
            description = Some("description2"),
            updatedDate = time2
          )
        )
      )
    }
  }

  test("getCommitStatus can find by commitId and context") {
    withTestDB { implicit session =>
      val tester = generateNewAccount(fixture1.creator)
      insertRepository(fixture1.repositoryName, fixture1.userName, None, false)
      val id = generateFixture1(tester: Account)
      assert(
        getCommitStatus(fixture1.userName, fixture1.repositoryName, fixture1.commitId, fixture1.context) == Some(
          fixture1.copy(commitStatusId = id)
        )
      )
    }
  }

  test("getCommitStatus can find by commitStatusId") {
    withTestDB { implicit session =>
      val tester = generateNewAccount(fixture1.creator)
      insertRepository(fixture1.repositoryName, fixture1.userName, None, false)
      val id = generateFixture1(tester: Account)
      assert(
        getCommitStatus(fixture1.userName, fixture1.repositoryName, id) == Some(fixture1.copy(commitStatusId = id))
      )
    }
  }
}
