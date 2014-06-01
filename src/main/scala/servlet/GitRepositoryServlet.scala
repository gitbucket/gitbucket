package servlet

import org.eclipse.jgit.http.server.GitServlet
import org.eclipse.jgit.lib._
import org.eclipse.jgit.transport._
import org.eclipse.jgit.transport.resolver._
import org.slf4j.LoggerFactory

import javax.servlet.ServletConfig
import javax.servlet.ServletContext
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import util.{StringUtil, Keys, JGitUtil, Directory}
import util.ControlUtil._
import util.Implicits._
import service._
import WebHookService._
import org.eclipse.jgit.api.Git
import util.JGitUtil.CommitInfo
import service.IssuesService.IssueSearchCondition

/**
 * Provides Git repository via HTTP.
 * 
 * This servlet provides only Git repository functionality.
 * Authentication is provided by [[servlet.BasicAuthenticationFilter]].
 */
class GitRepositoryServlet extends GitServlet with SystemSettingsService {

  private val logger = LoggerFactory.getLogger(classOf[GitRepositoryServlet])
  
  override def init(config: ServletConfig): Unit = {
    setReceivePackFactory(new GitBucketReceivePackFactory())

    // TODO are there any other ways...?
    super.init(new ServletConfig(){
      def getInitParameter(name: String): String = name match {
        case "base-path"  => Directory.RepositoryHome
        case "export-all" => "true"
        case name => config.getInitParameter(name)
      }
      def getInitParameterNames(): java.util.Enumeration[String] = {
         config.getInitParameterNames
      }

      def getServletContext(): ServletContext = config.getServletContext
      def getServletName(): String = config.getServletName
    })

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

class GitBucketReceivePackFactory extends ReceivePackFactory[HttpServletRequest] with SystemSettingsService {
  
  private val logger = LoggerFactory.getLogger(classOf[GitBucketReceivePackFactory])

  override def create(request: HttpServletRequest, db: Repository): ReceivePack = {
    val receivePack = new ReceivePack(db)
    val pusher = request.getAttribute(Keys.Request.UserName).asInstanceOf[String]

    logger.debug("requestURI: " + request.getRequestURI)
    logger.debug("pusher:" + pusher)

    defining(request.paths){ paths =>
      val owner      = paths(1)
      val repository = paths(2).stripSuffix(".git")

      logger.debug("repository:" + owner + "/" + repository)

      if(!repository.endsWith(".wiki")){
        val hook = new CommitLogHook(owner, repository, pusher, baseUrl(request))
        receivePack.setPreReceiveHook(hook)
        receivePack.setPostReceiveHook(hook)
      }
      receivePack
    }
  }
}

import scala.collection.JavaConverters._

class CommitLogHook(owner: String, repository: String, pusher: String, baseUrl: String) extends PostReceiveHook with PreReceiveHook
  with RepositoryService with AccountService with IssuesService with ActivityService with PullRequestService with WebHookService {
  
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
            countIssue(IssueSearchCondition(state = "open"), Map.empty, false, owner -> repository) +
            countIssue(IssueSearchCondition(state = "closed"), Map.empty, false, owner -> repository)

          // Extract new commit and apply issue comment
          val defaultBranch = getRepository(owner, repository, baseUrl).get.repository.defaultBranch
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
                updatePullRequests(branchName)
              case _ =>
            }
          }

          // call web hook
          getWebHookURLs(owner, repository) match {
            case webHookURLs if(webHookURLs.nonEmpty) =>
              for(pusherAccount <- getAccountByUserName(pusher);
                  ownerAccount   <- getAccountByUserName(owner);
                  repositoryInfo <- getRepository(owner, repository, baseUrl)){
                callWebHook(owner, repository, webHookURLs,
                  WebHookPayload(git, pusherAccount, command.getRefName, repositoryInfo, newCommits, ownerAccount))
              }
            case _ =>
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
        getAccountByMailAddress(commit.mailAddress).foreach { account =>
          createComment(owner, repository, account.userName, issueId.toInt, commit.fullMessage + " " + commit.id, "commit")
        }
      }
    }
  }

  /**
   * Fetch pull request contents into refs/pull/${issueId}/head and update pull request table.
   */
  private def updatePullRequests(branch: String) =
    getPullRequestsByRequest(owner, repository, branch, false).foreach { pullreq =>
      if(getRepository(pullreq.userName, pullreq.repositoryName, baseUrl).isDefined){
        using(Git.open(Directory.getRepositoryDir(pullreq.userName, pullreq.repositoryName)),
              Git.open(Directory.getRepositoryDir(pullreq.requestUserName, pullreq.requestRepositoryName))){ (oldGit, newGit) =>
          oldGit.fetch
            .setRemote(Directory.getRepositoryDir(owner, repository).toURI.toString)
            .setRefSpecs(new RefSpec(s"refs/heads/${branch}:refs/pull/${pullreq.issueId}/head").setForceUpdate(true))
            .call

          val commitIdTo = oldGit.getRepository.resolve(s"refs/pull/${pullreq.issueId}/head").getName
          val commitIdFrom = JGitUtil.getForkedCommitId(oldGit, newGit,
            pullreq.userName, pullreq.repositoryName, pullreq.branch,
            pullreq.requestUserName, pullreq.requestRepositoryName, pullreq.requestBranch)
          updateCommitId(pullreq.userName, pullreq.repositoryName, pullreq.issueId, commitIdTo, commitIdFrom)
        }
      }
    }
}
