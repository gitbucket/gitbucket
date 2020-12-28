package gitbucket.core.service

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.revwalk._
import org.eclipse.jetty.server.handler.AbstractHandler
import org.scalatest.funspec.AnyFunSpec
import java.io.File
import java.util.Date
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

import scala.util.Using
import scala.jdk.CollectionConverters._
import gitbucket.core.controller.Context
import gitbucket.core.plugin.ReceiveHook
import gitbucket.core.util.Directory._
import gitbucket.core.util.GitSpecUtil._
import gitbucket.core.service.RepositoryService.RepositoryInfo
import gitbucket.core.model._
import gitbucket.core.model.Profile._
import gitbucket.core.model.Profile.profile.blockingApi._
import gitbucket.core.service.WebHookService.WebHookPushPayload
import org.eclipse.jetty.webapp.WebAppContext
import org.eclipse.jetty.server.{Request, Server}
import org.json4s.jackson.JsonMethods._
import MergeServiceSpec._
import org.json4s.JsonAST.{JArray, JString}

class MergeServiceSpec extends AnyFunSpec with ServiceSpecBase {
  val service = new MergeService with AccountService with ActivityService with IssuesService with LabelsService
  with MilestonesService with RepositoryService with PrioritiesService with PullRequestService with CommitsService
  with WebHookPullRequestService with WebHookPullRequestReviewCommentService with RequestCache {
    override protected def getReceiveHooks(): Seq[ReceiveHook] = Nil
  }
  val branch = "master"
  val issueId = 10
  def initRepository(owner: String, name: String): File = {
    val dir = createTestRepository(getRepositoryDir(owner, name))
    Using.resource(Git.open(dir)) { git =>
      createFile(git, "refs/heads/master", "test.txt", "hoge")
      git.branchCreate().setStartPoint(s"refs/heads/master").setName(s"refs/pull/${issueId}/head").call()
    }
    dir
  }
  def createConfrict(git: Git) = {
    createFile(git, s"refs/heads/${branch}", "test.txt", "hoge2")
    createFile(git, s"refs/pull/${issueId}/head", "test.txt", "hoge4")
  }
  describe("checkConflict, checkConflictCache") {
    it("checkConflict false if not conflicted, and create cache") {
      val repo1Dir = initRepository("user1", "repo1")
      assert(service.checkConflictCache("user1", "repo1", branch, issueId) == None)
      val conflicted = service.checkConflict("user1", "repo1", branch, issueId)
      assert(service.checkConflictCache("user1", "repo1", branch, issueId) == Some(None))
      assert(conflicted.isEmpty)
    }
    it("checkConflict true if not conflicted, and create cache") {
      val repo2Dir = initRepository("user1", "repo2")
      Using.resource(Git.open(repo2Dir)) { git =>
        createConfrict(git)
      }
      assert(service.checkConflictCache("user1", "repo2", branch, issueId) == None)
      val conflicted = service.checkConflict("user1", "repo2", branch, issueId)
      assert(conflicted.isDefined)
      assert(service.checkConflictCache("user1", "repo2", branch, issueId) match {
        case Some(Some(_: String)) => true
        case _                     => false
      })
    }
  }
  describe("checkConflictCache") {
    it("merged cache invalid if origin branch moved") {
      val repo3Dir = initRepository("user1", "repo3")
      assert(service.checkConflict("user1", "repo3", branch, issueId).isEmpty)
      assert(service.checkConflictCache("user1", "repo3", branch, issueId) == Some(None))
      Using.resource(Git.open(repo3Dir)) { git =>
        createFile(git, s"refs/heads/${branch}", "test.txt", "hoge2")
      }
      assert(service.checkConflictCache("user1", "repo3", branch, issueId) == None)
    }
    it("merged cache invalid if request branch moved") {
      val repo4Dir = initRepository("user1", "repo4")
      assert(service.checkConflict("user1", "repo4", branch, issueId).isEmpty)
      assert(service.checkConflictCache("user1", "repo4", branch, issueId) == Some(None))
      Using.resource(Git.open(repo4Dir)) { git =>
        createFile(git, s"refs/pull/${issueId}/head", "test.txt", "hoge4")
      }
      assert(service.checkConflictCache("user1", "repo4", branch, issueId) == None)
    }
    it("should merged cache invalid if origin branch moved") {
      val repo5Dir = initRepository("user1", "repo5")
      assert(service.checkConflict("user1", "repo5", branch, issueId).isEmpty)
      assert(service.checkConflictCache("user1", "repo5", branch, issueId) == Some(None))
      Using.resource(Git.open(repo5Dir)) { git =>
        createFile(git, s"refs/heads/${branch}", "test.txt", "hoge2")
      }
      assert(service.checkConflictCache("user1", "repo5", branch, issueId) == None)
    }
    it("conflicted cache invalid if request branch moved") {
      val repo6Dir = initRepository("user1", "repo6")
      Using.resource(Git.open(repo6Dir)) { git =>
        createConfrict(git)
      }
      assert(service.checkConflict("user1", "repo6", branch, issueId).isDefined)
      assert(service.checkConflictCache("user1", "repo6", branch, issueId) match {
        case Some(Some(_: String)) => true
        case _                     => false
      })
      Using.resource(Git.open(repo6Dir)) { git =>
        createFile(git, s"refs/pull/${issueId}/head", "test.txt", "hoge4")
      }
      assert(service.checkConflictCache("user1", "repo6", branch, issueId) == None)
    }
    it("conflicted cache invalid if origin branch moved") {
      val repo7Dir = initRepository("user1", "repo7")
      Using.resource(Git.open(repo7Dir)) { git =>
        createConfrict(git)
      }
      assert(service.checkConflict("user1", "repo7", branch, issueId).isDefined)
      assert(service.checkConflictCache("user1", "repo7", branch, issueId) match {
        case Some(Some(_)) => true
        case _             => false
      })
      Using.resource(Git.open(repo7Dir)) { git =>
        createFile(git, s"refs/heads/${branch}", "test.txt", "hoge4")
      }
      assert(service.checkConflictCache("user1", "repo7", branch, issueId) == None)
    }
  }
  describe("mergePullRequest") {
    it("can merge") {
      implicit val jsonFormats = gitbucket.core.api.JsonFormat.jsonFormats
      import gitbucket.core.util.Implicits._

      withTestDB { implicit session =>
        generateNewUserWithDBRepository("user1", "repo8")
        initRepository("user1", "repo8")

        implicit val context = Context(
          createSystemSettings(),
          Some(createAccount("dummy2", "dummy2-fullname", "dummy2@example.com")),
          request
        )

        Using.resource(Git.open(getRepositoryDir("user1", "repo8"))) { git =>
          val commitId = createFile(git, s"refs/pull/${issueId}/head", "test.txt", "hoge2")
          assert(getFile(git, branch, "test.txt").content.get == "hoge")

          val requestBranchId = git.getRepository.resolve(s"refs/pull/${issueId}/head")
          val masterId = git.getRepository.resolve(branch)
          val repository = createRepositoryInfo("user1", "repo8")

          registerWebHook("user1", "repo8", "http://localhost:9999")

          Using.resource(new TestServer()) { server =>
            service.mergeWithMergeCommit(
              git,
              repository,
              branch,
              issueId,
              "merged",
              context.loginAccount.get,
              context.settings
            )

            Thread.sleep(5000)

            val json = parse(new String(server.lastRequestContent, StandardCharsets.UTF_8))
            // 2 commits (create file + merge commit)
            assert((json \ "commits").asInstanceOf[JArray].arr.length == 2)
            // verify id of file creation commit
            assert((json \ "commits" \ "id").asInstanceOf[JArray].arr(0).asInstanceOf[JString].s == commitId.getName)
          }

          val lastCommitId = git.getRepository.resolve(branch)
          val commit = Using.resource(new RevWalk(git.getRepository))(_.parseCommit(lastCommitId))
          assert(commit.getCommitterIdent().getName == "dummy2-fullname")
          assert(commit.getCommitterIdent().getEmailAddress == "dummy2@example.com")
          assert(commit.getAuthorIdent().getName == "dummy2-fullname")
          assert(commit.getAuthorIdent().getEmailAddress == "dummy2@example.com")
          assert(commit.getFullMessage() == "merged")
          assert(commit.getParents.toSet == Set(requestBranchId, masterId))
          assert(getFile(git, branch, "test.txt").content.get == "hoge2")
        }
      }
    }
  }

  private def registerWebHook(userName: String, repositoryName: String, url: String)(implicit s: Session): Unit = {
    RepositoryWebHooks insert RepositoryWebHook(
      userName = userName,
      repositoryName = repositoryName,
      url = url,
      ctype = WebHookContentType.JSON,
      token = None
    )
    RepositoryWebHookEvents insert RepositoryWebHookEvent(userName, repositoryName, url, WebHook.Push)
  }

  private def createAccount(userName: String, fullName: String, mailAddress: String): Account =
    Account(
      userName = userName,
      fullName = fullName,
      mailAddress = mailAddress,
      password = "password",
      isAdmin = false,
      url = None,
      registeredDate = new Date(),
      updatedDate = new Date(),
      lastLoginDate = None,
      image = None,
      isGroupAccount = false,
      isRemoved = false,
      description = None
    )

  private def createRepositoryInfo(userName: String, repositoryName: String): RepositoryInfo =
    RepositoryInfo(
      owner = userName,
      name = repositoryName,
      repository = gitbucket.core.model.Repository(
        userName = userName,
        repositoryName = repositoryName,
        isPrivate = false,
        description = None,
        defaultBranch = "master",
        registeredDate = new Date(),
        updatedDate = new Date(),
        lastActivityDate = new Date(),
        originUserName = None,
        originRepositoryName = None,
        parentUserName = None,
        parentRepositoryName = None,
        options = RepositoryOptions(
          issuesOption = "PUBLIC",
          externalIssuesUrl = None,
          wikiOption = "PUBLIC",
          externalWikiUrl = None,
          allowFork = true,
          mergeOptions = "merge-commit,squash,rebase",
          defaultMergeOption = "merge-commit"
        )
      ),
      issueCount = 0,
      pullCount = 0,
      forkedCount = 0,
      milestoneCount = 0,
      branchList = Nil,
      tags = Nil,
      managers = Nil
    )
}

object MergeServiceSpec {
  class TestServer extends AutoCloseable {
    var lastRequestURI: String = null
    var lastRequestHeaders: Map[String, String] = null
    var lastRequestContent: Array[Byte] = null

    val server = new Server(new InetSocketAddress(9999))
    val context = new WebAppContext()
    context.setServer(server)
    server.setStopAtShutdown(true)
    server.setStopTimeout(500)
    server.setHandler(new AbstractHandler {
      override def handle(
        target: String,
        baseRequest: Request,
        request: HttpServletRequest,
        response: HttpServletResponse
      ): Unit = {
        lastRequestURI = request.getRequestURI
        lastRequestHeaders = request.getHeaderNames.asScala.map { key =>
          key -> request.getHeader(key)
        }.toMap
        val bytes = new Array[Byte](request.getContentLength)
        if (bytes.length > 0) {
          request.getInputStream.read(bytes)
          lastRequestContent = bytes
        }
      }
    })
    server.start()

    override def close(): Unit = {
      server.stop()
    }
  }
}
