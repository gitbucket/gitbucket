package gitbucket.core.servlet

import java.io.File
import java.util
import java.util.Date

import gitbucket.core.api
import gitbucket.core.model.WebHook
import gitbucket.core.model.Profile.profile.blockingApi._
import gitbucket.core.plugin.{GitRepositoryRouting, PluginRegistry}
import gitbucket.core.service.IssuesService.IssueSearchCondition
import gitbucket.core.service.WebHookService._
import gitbucket.core.service._
import gitbucket.core.util.SyntaxSugars._
import gitbucket.core.util.Implicits._
import gitbucket.core.util._
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.http.server.GitServlet
import org.eclipse.jgit.lib._
import org.eclipse.jgit.transport._
import org.eclipse.jgit.transport.resolver._
import org.slf4j.LoggerFactory
import javax.servlet.ServletConfig
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

import org.eclipse.jgit.diff.DiffEntry.ChangeType
import org.json4s.jackson.Serialization._

/**
 * Provides Git repository via HTTP.
 *
 * This servlet provides only Git repository functionality.
 * Authentication is provided by [[GitAuthenticationFilter]].
 */
class GitRepositoryServlet extends GitServlet with SystemSettingsService {

  private val logger = LoggerFactory.getLogger(classOf[GitRepositoryServlet])
  private implicit val jsonFormats = gitbucket.core.api.JsonFormat.jsonFormats

  override def init(config: ServletConfig): Unit = {
    setReceivePackFactory(new GitBucketReceivePackFactory())

    val root: File = new File(Directory.RepositoryHome)
    setRepositoryResolver(new GitBucketRepositoryResolver(new FileResolver[HttpServletRequest](root, true)))

    super.init(config)
  }

  override def service(req: HttpServletRequest, res: HttpServletResponse): Unit = {
    val agent = req.getHeader("USER-AGENT")
    val index = req.getRequestURI.indexOf(".git")
    if (index >= 0 && (agent == null || agent.toLowerCase.indexOf("git") < 0)) {
      // redirect for browsers
      val paths = req.getRequestURI.substring(0, index).split("/")
      res.sendRedirect(baseUrl(req) + "/" + paths.dropRight(1).last + "/" + paths.last)

    } else if (req.getMethod.toUpperCase == "POST" && req.getRequestURI.endsWith("/info/lfs/objects/batch")) {
      serviceGitLfsBatchAPI(req, res)

    } else {
      // response for git client
      super.service(req, res)
    }
  }

  /**
   * Provides GitLFS Batch API
   * https://github.com/git-lfs/git-lfs/blob/master/docs/api/batch.md
   */
  protected def serviceGitLfsBatchAPI(req: HttpServletRequest, res: HttpServletResponse): Unit = {
    val batchRequest = read[GitLfs.BatchRequest](req.getInputStream)
    val settings = loadSystemSettings()

    settings.baseUrl match {
      case None => {
        throw new IllegalStateException("lfs.server_url is not configured.")
      }
      case Some(baseUrl) => {
        val index = req.getRequestURI.indexOf(".git")
        if (index >= 0) {
          req.getRequestURI.substring(0, index).split("/").reverse match {
            case Array(repository, owner, _*) =>
              val timeout = System.currentTimeMillis + (60000 * 10) // 10 min.
              val batchResponse = batchRequest.operation match {
                case "upload" =>
                  GitLfs.BatchUploadResponse(
                    "basic",
                    batchRequest.objects.map { requestObject =>
                      GitLfs.BatchResponseObject(
                        requestObject.oid,
                        requestObject.size,
                        true,
                        GitLfs.Actions(
                          upload = Some(
                            GitLfs.Action(
                              href = baseUrl + "/git-lfs/" + owner + "/" + repository + "/" + requestObject.oid,
                              header =
                                Map("Authorization" -> StringUtil.encodeBlowfish(timeout + " " + requestObject.oid)),
                              expires_at = new Date(timeout)
                            )
                          )
                        )
                      )
                    }
                  )
                case "download" =>
                  GitLfs.BatchUploadResponse(
                    "basic",
                    batchRequest.objects.map { requestObject =>
                      GitLfs.BatchResponseObject(
                        requestObject.oid,
                        requestObject.size,
                        true,
                        GitLfs.Actions(
                          download = Some(
                            GitLfs.Action(
                              href = baseUrl + "/git-lfs/" + owner + "/" + repository + "/" + requestObject.oid,
                              header =
                                Map("Authorization" -> StringUtil.encodeBlowfish(timeout + " " + requestObject.oid)),
                              expires_at = new Date(timeout)
                            )
                          )
                        )
                      )
                    }
                  )
              }

              res.setContentType("application/vnd.git-lfs+json")
              using(res.getWriter) { out =>
                out.print(write(batchResponse))
                out.flush()
              }
          }
        }
      }
    }
  }
}

class GitBucketRepositoryResolver(parent: FileResolver[HttpServletRequest])
    extends RepositoryResolver[HttpServletRequest] {

  private val resolver = new FileResolver[HttpServletRequest](new File(Directory.GitBucketHome), true)

  override def open(req: HttpServletRequest, name: String): Repository = {
    // Rewrite repository path if routing is marched
    PluginRegistry()
      .getRepositoryRouting("/" + name)
      .map {
        case GitRepositoryRouting(urlPattern, localPath, _) =>
          val path = urlPattern.r.replaceFirstIn(name, localPath)
          resolver.open(req, path)
      }
      .getOrElse {
        parent.open(req, name)
      }
  }

}

class GitBucketReceivePackFactory extends ReceivePackFactory[HttpServletRequest] with SystemSettingsService {

  private val logger = LoggerFactory.getLogger(classOf[GitBucketReceivePackFactory])

  override def create(request: HttpServletRequest, db: Repository): ReceivePack = {
    val receivePack = new ReceivePack(db)

    if (PluginRegistry().getRepositoryRouting(request.gitRepositoryPath).isEmpty) {
      val pusher = request.getAttribute(Keys.Request.UserName).asInstanceOf[String]

      logger.debug("requestURI: " + request.getRequestURI)
      logger.debug("pusher:" + pusher)

      defining(request.paths) { paths =>
        val owner = paths(1)
        val repository = paths(2).stripSuffix(".git")

        logger.debug("repository:" + owner + "/" + repository)

        val settings = loadSystemSettings()
        val baseUrl = settings.baseUrl(request)
        val sshUrl = settings.sshAddress.map { x =>
          s"${x.genericUser}@${x.host}:${x.port}"
        }

        if (!repository.endsWith(".wiki")) {
          defining(request) { implicit r =>
            val hook = new CommitLogHook(owner, repository, pusher, baseUrl, sshUrl)
            receivePack.setPreReceiveHook(hook)
            receivePack.setPostReceiveHook(hook)
          }
        }

        if (repository.endsWith(".wiki")) {
          defining(request) { implicit r =>
            receivePack.setPostReceiveHook(
              new WikiCommitHook(owner, repository.stripSuffix(".wiki"), pusher, baseUrl, sshUrl)
            )
          }
        }
      }
    }

    receivePack
  }
}

import scala.collection.JavaConverters._

class CommitLogHook(owner: String, repository: String, pusher: String, baseUrl: String, sshUrl: Option[String])
    extends PostReceiveHook
    with PreReceiveHook
    with RepositoryService
    with AccountService
    with IssuesService
    with ActivityService
    with PullRequestService
    with WebHookService
    with LabelsService
    with PrioritiesService
    with MilestonesService
    with WebHookPullRequestService
    with CommitsService {

  private val logger = LoggerFactory.getLogger(classOf[CommitLogHook])
  private var existIds: Seq[String] = Nil

  def onPreReceive(receivePack: ReceivePack, commands: java.util.Collection[ReceiveCommand]): Unit = {
    Database() withTransaction { implicit session =>
      try {
        commands.asScala.foreach { command =>
          // call pre-commit hook
          PluginRegistry().getReceiveHooks
            .flatMap(_.preReceive(owner, repository, receivePack, command, pusher))
            .headOption
            .foreach { error =>
              command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, error)
            }
        }
        using(Git.open(Directory.getRepositoryDir(owner, repository))) { git =>
          existIds = JGitUtil.getAllCommitIds(git)
        }
      } catch {
        case ex: Exception => {
          logger.error(ex.toString, ex)
          throw ex
        }
      }
    }
  }

  def onPostReceive(receivePack: ReceivePack, commands: java.util.Collection[ReceiveCommand]): Unit = {
    Database() withTransaction { implicit session =>
      try {
        using(Git.open(Directory.getRepositoryDir(owner, repository))) { git =>
          JGitUtil.removeCache(git)

          val pushedIds = scala.collection.mutable.Set[String]()
          commands.asScala.foreach { command =>
            logger.debug(s"commandType: ${command.getType}, refName: ${command.getRefName}")
            implicit val apiContext = api.JsonFormat.Context(baseUrl, sshUrl)
            val refName = command.getRefName.split("/")
            val branchName = refName.drop(2).mkString("/")
            val commits = if (refName(1) == "tags") {
              Nil
            } else {
              command.getType match {
                case ReceiveCommand.Type.DELETE => Nil
                case _                          => JGitUtil.getCommitLog(git, command.getOldId.name, command.getNewId.name)
              }
            }

            val repositoryInfo = getRepository(owner, repository).get

            // Update default branch if repository is empty and pushed branch is not current default branch
            if (JGitUtil.isEmpty(git) && commits.nonEmpty && branchName != repositoryInfo.repository.defaultBranch) {
              saveRepositoryDefaultBranch(owner, repository, branchName)
              // Change repository HEAD
              using(Git.open(Directory.getRepositoryDir(owner, repository))) { git =>
                git.getRepository.updateRef(Constants.HEAD, true).link(Constants.R_HEADS + branchName)
              }
            }

            // Retrieve all issue count in the repository
            val issueCount =
              countIssue(IssueSearchCondition(state = "open"), false, owner -> repository) +
                countIssue(IssueSearchCondition(state = "closed"), false, owner -> repository)

            // Extract new commit and apply issue comment
            val defaultBranch = repositoryInfo.repository.defaultBranch
            val newCommits = commits.flatMap { commit =>
              if (!existIds.contains(commit.id) && !pushedIds.contains(commit.id)) {
                if (issueCount > 0) {
                  pushedIds.add(commit.id)
                  createIssueComment(owner, repository, commit)
                  // close issues
                  if (refName(1) == "heads" && branchName == defaultBranch && command.getType == ReceiveCommand.Type.UPDATE) {
                    getAccountByUserName(pusher).map { pusherAccount =>
                      closeIssuesFromMessage(commit.fullMessage, pusher, owner, repository).foreach { issueId =>
                        getIssue(owner, repository, issueId.toString).map { issue =>
                          callIssuesWebHook("closed", repositoryInfo, issue, baseUrl, pusherAccount)
                          PluginRegistry().getIssueHooks
                            .foreach(_.closedByCommitComment(issue, repositoryInfo, commit.fullMessage, pusherAccount))
                        }
                      }
                    }
                  }
                }
                Some(commit)
              } else None
            }

            // set PR as merged
            val pulls = getPullRequestsByBranch(owner, repository, branchName, Some(false))
            pulls.foreach { pull =>
              if (commits.find { c =>
                    c.id == pull.commitIdTo
                  }.isDefined) {
                markMergeAndClosePullRequest(pusher, owner, repository, pull)
                getAccountByUserName(pusher).map { pusherAccount =>
                  callPullRequestWebHook("closed", repositoryInfo, pull.issueId, baseUrl, pusherAccount)
                }
              }
            }

            // record activity
            if (refName(1) == "heads") {
              command.getType match {
                case ReceiveCommand.Type.CREATE => recordCreateBranchActivity(owner, repository, pusher, branchName)
                case ReceiveCommand.Type.UPDATE => recordPushActivity(owner, repository, pusher, branchName, newCommits)
                case ReceiveCommand.Type.DELETE => recordDeleteBranchActivity(owner, repository, pusher, branchName)
                case _                          =>
              }
            } else if (refName(1) == "tags") {
              command.getType match {
                case ReceiveCommand.Type.CREATE =>
                  recordCreateTagActivity(owner, repository, pusher, branchName, newCommits)
                case ReceiveCommand.Type.DELETE =>
                  recordDeleteTagActivity(owner, repository, pusher, branchName, newCommits)
                case _ =>
              }
            }

            if (refName(1) == "heads") {
              command.getType match {
                case ReceiveCommand.Type.CREATE | ReceiveCommand.Type.UPDATE |
                    ReceiveCommand.Type.UPDATE_NONFASTFORWARD =>
                  updatePullRequests(owner, repository, branchName)
                  getAccountByUserName(pusher).map { pusherAccount =>
                    callPullRequestWebHookByRequestBranch(
                      "synchronize",
                      repositoryInfo,
                      branchName,
                      baseUrl,
                      pusherAccount
                    )
                  }
                case _ =>
              }
            }

            // call web hook
            callWebHookOf(owner, repository, WebHook.Push) {
              for {
                pusherAccount <- getAccountByUserName(pusher)
                ownerAccount <- getAccountByUserName(owner)
              } yield {
                WebHookPushPayload(
                  git,
                  pusherAccount,
                  command.getRefName,
                  repositoryInfo,
                  newCommits,
                  ownerAccount,
                  newId = command.getNewId(),
                  oldId = command.getOldId()
                )
              }
            }
            if (command.getType == ReceiveCommand.Type.CREATE) {
              callWebHookOf(owner, repository, WebHook.Create) {
                for {
                  pusherAccount <- getAccountByUserName(pusher)
                  ownerAccount <- getAccountByUserName(owner)
                } yield {
                  val refType = if (refName(1) == "tags") "tag" else "branch"
                  WebHookCreatePayload(
                    git,
                    pusherAccount,
                    command.getRefName,
                    repositoryInfo,
                    newCommits,
                    ownerAccount,
                    ref = branchName,
                    refType = refType
                  )
                }
              }
            }

            // call post-commit hook
            PluginRegistry().getReceiveHooks.foreach(_.postReceive(owner, repository, receivePack, command, pusher))
          }
        }
        // update repository last modified time.
        updateLastActivityDate(owner, repository)
      } catch {
        case ex: Exception => {
          logger.error(ex.toString, ex)
          throw ex
        }
      }
    }
  }

}

class WikiCommitHook(owner: String, repository: String, pusher: String, baseUrl: String, sshUrl: Option[String])
    extends PostReceiveHook
    with WebHookService
    with AccountService
    with RepositoryService {

  private val logger = LoggerFactory.getLogger(classOf[WikiCommitHook])

  override def onPostReceive(receivePack: ReceivePack, commands: util.Collection[ReceiveCommand]): Unit = {
    Database() withTransaction { implicit session =>
      try {
        commands.asScala.headOption.foreach { command =>
          implicit val apiContext = api.JsonFormat.Context(baseUrl, sshUrl)
          val refName = command.getRefName.split("/")
          val commitIds = if (refName(1) == "tags") {
            None
          } else {
            command.getType match {
              case ReceiveCommand.Type.DELETE => None
              case _                          => Some((command.getOldId.getName, command.getNewId.name))
            }
          }

          commitIds.map {
            case (oldCommitId, newCommitId) =>
              val commits = using(Git.open(Directory.getWikiRepositoryDir(owner, repository))) { git =>
                JGitUtil.getCommitLog(git, oldCommitId, newCommitId).flatMap { commit =>
                  val diffs = JGitUtil.getDiffs(git, None, commit.id, false, false)
                  diffs.collect {
                    case diff if diff.newPath.toLowerCase.endsWith(".md") =>
                      val action = if (diff.changeType == ChangeType.ADD) "created" else "edited"
                      val fileName = diff.newPath
                      (action, fileName, commit.id)
                  }
                }
              }

              val pages = commits
                .groupBy { case (action, fileName, commitId) => fileName }
                .map {
                  case (fileName, commits) =>
                    (commits.head._1, fileName, commits.last._3)
                }

              callWebHookOf(owner, repository, WebHook.Gollum) {
                for {
                  pusherAccount <- getAccountByUserName(pusher)
                  repositoryUser <- getAccountByUserName(owner)
                  repositoryInfo <- getRepository(owner, repository)
                } yield {
                  WebHookGollumPayload(pages.toSeq, repositoryInfo, repositoryUser, pusherAccount)
                }
              }
          }
        }
      } catch {
        case ex: Exception => {
          logger.error(ex.toString, ex)
          throw ex
        }
      }
    }
  }

}

object GitLfs {

  case class BatchRequest(
    operation: String,
    transfers: Seq[String],
    objects: Seq[BatchRequestObject]
  )

  case class BatchRequestObject(
    oid: String,
    size: Long
  )

  case class BatchUploadResponse(
    transfer: String,
    objects: Seq[BatchResponseObject]
  )

  case class BatchResponseObject(
    oid: String,
    size: Long,
    authenticated: Boolean,
    actions: Actions
  )

  case class Actions(
    download: Option[Action] = None,
    upload: Option[Action] = None
  )

  case class Action(
    href: String,
    header: Map[String, String] = Map.empty,
    expires_at: Date
  )

  case class Error(
    message: String
  )

}
