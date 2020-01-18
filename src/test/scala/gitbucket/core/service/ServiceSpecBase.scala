package gitbucket.core.service

import gitbucket.core.GitBucketCoreModule
import gitbucket.core.util.{DatabaseConfig, Directory, FileUtil, JGitUtil}
import io.github.gitbucket.solidbase.Solidbase
import liquibase.database.core.H2Database
import liquibase.database.jvm.JdbcConnection
import gitbucket.core.model._
import gitbucket.core.model.Profile.profile.blockingApi._
import org.apache.commons.io.FileUtils
import java.sql.DriverManager
import java.io.File

import gitbucket.core.controller.Context
import gitbucket.core.service.SystemSettingsService.{Ssh, SystemSettings}
import javax.servlet.http.{HttpServletRequest, HttpSession}
import org.scalatestplus.mockito.MockitoSugar
import org.mockito.Mockito._

import scala.util.Random
import scala.util.Using

trait ServiceSpecBase extends MockitoSugar {

  val request = mock[HttpServletRequest]
  val session = mock[HttpSession]
  when(request.getRequestURL).thenReturn(new StringBuffer("http://localhost:8080/path.html"))
  when(request.getRequestURI).thenReturn("/path.html")
  when(request.getContextPath).thenReturn("")
  when(request.getSession).thenReturn(session)

  private def createSystemSettings() =
    SystemSettings(
      baseUrl = None,
      information = None,
      allowAccountRegistration = false,
      allowAnonymousAccess = true,
      isCreateRepoOptionPublic = true,
      gravatar = false,
      notification = false,
      activityLogLimit = None,
      limitVisibleRepositories = false,
      ssh = Ssh(
        enabled = false,
        sshHost = None,
        sshPort = None
      ),
      useSMTP = false,
      smtp = None,
      ldapAuthentication = false,
      ldap = None,
      oidcAuthentication = false,
      oidc = None,
      skinName = "skin-blue",
      showMailAddress = false,
      webHook = SystemSettingsService.WebHook(
        blockPrivateAddress = false,
        whitelist = Nil
      ),
      upload = SystemSettingsService.Upload(
        maxFileSize = 3 * 1024 * 1024,
        timeout = 30 * 10000,
        largeMaxFileSize = 3 * 1024 * 1024,
        largeTimeout = 30 * 10000
      )
    )

  def withTestDB[A](action: (Session) => A): A = {
    FileUtil.withTmpDir(new File(FileUtils.getTempDirectory(), Random.alphanumeric.take(10).mkString)) { dir =>
      val (url, user, pass) = (DatabaseConfig.url(Some(dir.toString)), DatabaseConfig.user, DatabaseConfig.password)
      org.h2.Driver.load()
      Using.resource(DriverManager.getConnection(url, user, pass)) { conn =>
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

  def generateNewAccount(name: String)(implicit s: Session): Account = {
    AccountService.createAccount(name, name, name, s"${name}@example.com", false, None, None)
    user(name)
  }

  def user(name: String)(implicit s: Session): Account = AccountService.getAccountByUserName(name).get

  lazy val dummyService = new RepositoryService with AccountService with ActivityService with IssuesService
  with MergeService with PullRequestService with CommitsService with CommitStatusService with LabelsService
  with MilestonesService with PrioritiesService with WebHookService with WebHookPullRequestService
  with WebHookPullRequestReviewCommentService {
    override def fetchAsPullRequest(
      userName: String,
      repositoryName: String,
      requestUserName: String,
      requestRepositoryName: String,
      requestBranch: String,
      issueId: Int
    ): Unit = {}
  }

  def generateNewUserWithDBRepository(userName: String, repositoryName: String)(implicit s: Session): Account = {
    val ac = AccountService.getAccountByUserName(userName).getOrElse(generateNewAccount(userName))
    val dir = Directory.getRepositoryDir(userName, repositoryName)
    if (dir.exists()) {
      FileUtils.deleteQuietly(dir)
    }
    JGitUtil.initRepository(dir)
    dummyService.insertRepository(repositoryName, userName, None, false)
    ac
  }

  def generateNewIssue(userName: String, repositoryName: String, loginUser: String = "root")(
    implicit s: Session
  ): Int = {
    dummyService.insertIssue(
      owner = userName,
      repository = repositoryName,
      loginUser = loginUser,
      title = "issue title",
      content = None,
      assignedUserName = None,
      milestoneId = None,
      priorityId = None,
      isPullRequest = true
    )
  }

  def generateNewPullRequest(base: String, request: String, loginUser: String)(
    implicit s: Session
  ): (Issue, PullRequest) = {
    implicit val context = Context(createSystemSettings(), None, this.request)
    val Array(baseUserName, baseRepositoryName, baesBranch) = base.split("/")
    val Array(requestUserName, requestRepositoryName, requestBranch) = request.split("/")
    val issueId = generateNewIssue(baseUserName, baseRepositoryName, Option(loginUser).getOrElse(requestUserName))
    val baseRepository = dummyService.getRepository(baseUserName, baseRepositoryName)
    val loginAccount = dummyService.getAccountByUserName(loginUser)
    dummyService.createPullRequest(
      originRepository = baseRepository.get,
      issueId = issueId,
      originBranch = baesBranch,
      requestUserName = requestUserName,
      requestRepositoryName = requestRepositoryName,
      requestBranch = requestBranch,
      commitIdFrom = baesBranch,
      commitIdTo = requestBranch,
      isDraft = false,
      loginAccount = loginAccount.get,
      settings = createSystemSettings()
    )
    dummyService.getPullRequest(baseUserName, baseRepositoryName, issueId).get
  }
}
