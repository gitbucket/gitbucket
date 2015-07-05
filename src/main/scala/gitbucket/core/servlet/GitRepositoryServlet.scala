package gitbucket.core.servlet

import java.io.File

import gitbucket.core.api
import gitbucket.core.model.Session
import gitbucket.core.plugin.{GitRepositoryRouting, PluginRegistry}
import gitbucket.core.service.IssuesService.IssueSearchCondition
import gitbucket.core.service.WebHookService._
import gitbucket.core.service._
import gitbucket.core.util.ControlUtil._
import gitbucket.core.util.Implicits._
import gitbucket.core.util.JGitUtil.CommitInfo
import gitbucket.core.util._

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.http.server.GitServlet
import org.eclipse.jgit.lib._
import org.eclipse.jgit.transport._
import org.eclipse.jgit.transport.resolver._
import org.slf4j.LoggerFactory

import javax.servlet.ServletConfig
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}


/**
 * Provides Git repository via HTTP.
 * 
 * This servlet provides only Git repository functionality.
 * Authentication is provided by [[BasicAuthenticationFilter]].
 */
class GitRepositoryServlet extends GitServlet with SystemSettingsService {

  private val logger = LoggerFactory.getLogger(classOf[GitRepositoryServlet])
  
  override def init(config: ServletConfig): Unit = {
    setReceivePackFactory(new GitBucketReceivePackFactory())

    val root: File = new File(Directory.RepositoryHome)
    setRepositoryResolver(new GitBucketRepositoryResolver(new FileResolver[HttpServletRequest](root, true)))

    super.init(config)
  }

  override def service(req: HttpServletRequest, res: HttpServletResponse): Unit = {
    val agent = req.getHeader("USER-AGENT")
    val index = req.getRequestURI.indexOf(".git")
    if(index >= 0 && (agent == null || agent.toLowerCase.indexOf("git/") < 0)){
      // redirect for browsers
      val paths = req.getRequestURI.substring(0, index).split("/")
      res.sendRedirect(baseUrl(req) + "/" + paths.dropRight(1).last + "/" + paths.last)
    } else {
      // response for git client
      super.service(req, res)
    }
  }
}

class GitBucketRepositoryResolver(parent: FileResolver[HttpServletRequest]) extends RepositoryResolver[HttpServletRequest] {

  private val resolver = new FileResolver[HttpServletRequest](new File(Directory.GitBucketHome), true)

  override def open(req: HttpServletRequest, name: String): Repository = {
    // Rewrite repository path if routing is marched
    PluginRegistry().getRepositoryRouting("/" + name).map { case GitRepositoryRouting(urlPattern, localPath, _) =>
      val path = urlPattern.r.replaceFirstIn(name, localPath)
      resolver.open(req, path)
    }.getOrElse {
      parent.open(req, name)
    }
  }

}

class GitBucketReceivePackFactory extends ReceivePackFactory[HttpServletRequest] with SystemSettingsService {

  private val logger = LoggerFactory.getLogger(classOf[GitBucketReceivePackFactory])

  override def create(request: HttpServletRequest, db: Repository): ReceivePack = {
    val receivePack = new ReceivePack(db)

    if(PluginRegistry().getRepositoryRouting(request.gitRepositoryPath).isEmpty){
      val pusher = request.getAttribute(Keys.Request.UserName).asInstanceOf[String]

      logger.debug("requestURI: " + request.getRequestURI)
      logger.debug("pusher:" + pusher)

      defining(request.paths){ paths =>
        val owner      = paths(1)
        val repository = paths(2).stripSuffix(".git")

        logger.debug("repository:" + owner + "/" + repository)

        if(!repository.endsWith(".wiki")){
          defining(request) { implicit r =>
            val hook = new CommitLogHook(owner, repository, pusher, baseUrl)
            receivePack.setPreReceiveHook(hook)
            receivePack.setPostReceiveHook(hook)
          }
        }
      }
    }

    receivePack
  }
}

import scala.collection.JavaConverters._

class CommitLogHook(owner: String, repository: String, pusher: String, baseUrl: String)(implicit session: Session)
  extends PostReceiveHook with PreReceiveHook
  with RepositoryService with AccountService with IssuesService with ActivityService with PullRequestService with WebHookService
  with WebHookPullRequestService {
  
  private val logger = LoggerFactory.getLogger(classOf[CommitLogHook])
  private var existIds: Seq[String] = Nil

  def onPreReceive(receivePack: ReceivePack, commands: java.util.Collection[ReceiveCommand]): Unit = {
    try {
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

  def onPostReceive(receivePack: ReceivePack, commands: java.util.Collection[ReceiveCommand]): Unit = {
    try {
      using(Git.open(Directory.getRepositoryDir(owner, repository))) { git =>
        val pushedIds = scala.collection.mutable.Set[String]()
        commands.asScala.foreach { command =>
          logger.debug(s"commandType: ${command.getType}, refName: ${command.getRefName}")
          implicit val apiContext = api.JsonFormat.Context(baseUrl)
          val refName = command.getRefName.split("/")
          val branchName = refName.drop(2).mkString("/")
          val commits = if (refName(1) == "tags") {
            Nil
          } else {
            command.getType match {
              case ReceiveCommand.Type.DELETE => Nil
              case _ => JGitUtil.getCommitLog(git, command.getOldId.name, command.getNewId.name)
            }
          }

          // Retrieve all issue count in the repository
          val issueCount =
            countIssue(IssueSearchCondition(state = "open"), false, owner -> repository) +
            countIssue(IssueSearchCondition(state = "closed"), false, owner -> repository)

          val repositoryInfo = getRepository(owner, repository, baseUrl).get

          // Extract new commit and apply issue comment
          val defaultBranch = repositoryInfo.repository.defaultBranch
          val newCommits = commits.flatMap { commit =>
            if (!existIds.contains(commit.id) && !pushedIds.contains(commit.id)) {
              if (issueCount > 0) {
                pushedIds.add(commit.id)
                createIssueComment(commit)
                // close issues
                if(refName(1) == "heads" && branchName == defaultBranch && command.getType == ReceiveCommand.Type.UPDATE){
                  closeIssuesFromMessage(commit.fullMessage, pusher, owner, repository)
                }
              }
              Some(commit)
            } else None
          }

          // record activity
          if(refName(1) == "heads"){
            command.getType match {
              case ReceiveCommand.Type.CREATE => recordCreateBranchActivity(owner, repository, pusher, branchName)
              case ReceiveCommand.Type.UPDATE => recordPushActivity(owner, repository, pusher, branchName, newCommits)
              case ReceiveCommand.Type.DELETE => recordDeleteBranchActivity(owner, repository, pusher, branchName)
              case _ =>
            }
          } else if(refName(1) == "tags"){
            command.getType match {
              case ReceiveCommand.Type.CREATE => recordCreateTagActivity(owner, repository, pusher, branchName, newCommits)
              case ReceiveCommand.Type.DELETE => recordDeleteTagActivity(owner, repository, pusher, branchName, newCommits)
              case _ =>
            }
          }

          if(refName(1) == "heads"){
            command.getType match {
              case ReceiveCommand.Type.CREATE |
                   ReceiveCommand.Type.UPDATE |
                   ReceiveCommand.Type.UPDATE_NONFASTFORWARD =>
                updatePullRequests(owner, repository, branchName)
                getAccountByUserName(pusher).map{ pusherAccount =>
                  callPullRequestWebHookByRequestBranch("synchronize", repositoryInfo, branchName, baseUrl, pusherAccount)
                }
              case _ =>
            }
          }

          // call web hook
          callWebHookOf(owner, repository, "push"){
            for(pusherAccount <- getAccountByUserName(pusher);
              ownerAccount   <- getAccountByUserName(owner)) yield {
              WebHookPushPayload(git, pusherAccount, command.getRefName, repositoryInfo, newCommits, ownerAccount)
            }
          }
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

  private def createIssueComment(commit: CommitInfo) = {
    StringUtil.extractIssueId(commit.fullMessage).foreach { issueId =>
      if(getIssue(owner, repository, issueId).isDefined){
        getAccountByMailAddress(commit.committerEmailAddress).foreach { account =>
          createComment(owner, repository, account.userName, issueId.toInt, commit.fullMessage + " " + commit.id, "commit")
        }
      }
    }
  }
}
