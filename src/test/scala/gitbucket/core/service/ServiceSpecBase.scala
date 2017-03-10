package gitbucket.core.service

import gitbucket.core.GitBucketCoreModule
import gitbucket.core.util.{DatabaseConfig, FileUtil}
import gitbucket.core.util.SyntaxSugars._
import io.github.gitbucket.solidbase.Solidbase
import liquibase.database.core.H2Database
import liquibase.database.jvm.JdbcConnection
import gitbucket.core.model._
import gitbucket.core.model.Profile._
import gitbucket.core.model.Profile.profile._
import gitbucket.core.model.Profile.profile.blockingApi._

import org.apache.commons.io.FileUtils

import java.sql.DriverManager
import java.io.File

import scala.util.Random


trait ServiceSpecBase {

  def withTestDB[A](action: (Session) => A): A = {
    FileUtil.withTmpDir(new File(FileUtils.getTempDirectory(), Random.alphanumeric.take(10).mkString)){ dir =>
      val (url, user, pass) = (DatabaseConfig.url(Some(dir.toString)), DatabaseConfig.user, DatabaseConfig.password)
      org.h2.Driver.load()
      using(DriverManager.getConnection(url, user, pass)){ conn =>
        val solidbase = new Solidbase()
        val db = new H2Database()
        db.setConnection(new JdbcConnection(conn)) // TODO Remove setConnection in the future
        solidbase.migrate(conn, Thread.currentThread.getContextClassLoader, db, GitBucketCoreModule)
      }
      Database.forURL(url, user, pass).withSession { session =>
        action(session)
      }
    }
  }

  def generateNewAccount(name:String)(implicit s:Session):Account = {
    AccountService.createAccount(name, name, name, s"${name}@example.com", false, None, None)
    user(name)
  }

  def user(name:String)(implicit s:Session):Account = AccountService.getAccountByUserName(name).get

  lazy val dummyService = new RepositoryService with AccountService with IssuesService with PullRequestService
    with CommitsService with CommitStatusService with LabelsService (){}

  def generateNewUserWithDBRepository(userName:String, repositoryName:String)(implicit s:Session):Account = {
    val ac = AccountService.getAccountByUserName(userName).getOrElse(generateNewAccount(userName))
    dummyService.insertRepository(repositoryName, userName, None, false)
    ac
  }

  def generateNewIssue(userName:String, repositoryName:String, loginUser:String="root")(implicit s:Session): Int = {
    dummyService.insertIssue(
      owner            = userName,
      repository       = repositoryName,
      loginUser        = loginUser,
      title            = "issue title",
      content          = None,
      assignedUserName = None,
      milestoneId      = None,
      isPullRequest    = true)
  }

  def generateNewPullRequest(base:String, request:String, loginUser:String=null)(implicit s:Session):(Issue, PullRequest) = {
    val Array(baseUserName, baseRepositoryName, baesBranch)=base.split("/")
    val Array(requestUserName, requestRepositoryName, requestBranch)=request.split("/")
    val issueId = generateNewIssue(baseUserName, baseRepositoryName, Option(loginUser).getOrElse(requestUserName))
    dummyService.createPullRequest(
      originUserName        = baseUserName,
      originRepositoryName  = baseRepositoryName,
      issueId               = issueId,
      originBranch          = baesBranch,
      requestUserName       = requestUserName,
      requestRepositoryName = requestRepositoryName,
      requestBranch         = requestBranch,
      commitIdFrom          = baesBranch,
      commitIdTo            = requestBranch)
    dummyService.getPullRequest(baseUserName, baseRepositoryName, issueId).get
  }
}
