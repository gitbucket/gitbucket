package servlet

import org.eclipse.jgit.http.server.GitServlet
import org.eclipse.jgit.lib._
import org.eclipse.jgit.transport._
import org.eclipse.jgit.transport.resolver._
import org.slf4j.LoggerFactory

import javax.servlet.ServletConfig
import javax.servlet.ServletContext
import javax.servlet.http.HttpServletRequest
import util.{Keys, JGitUtil, Directory}
import util.ControlUtil._
import util.Implicits._
import service._
import WebHookService._
import org.eclipse.jgit.api.Git
import util.JGitUtil.CommitInfo

/**
 * Provides Git repository via HTTP.
 * 
 * This servlet provides only Git repository functionality.
 * Authentication is provided by [[servlet.BasicAuthenticationFilter]].
 */
class GitRepositoryServlet extends GitServlet {

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
  
}

class GitBucketReceivePackFactory extends ReceivePackFactory[HttpServletRequest] {
  
  private val logger = LoggerFactory.getLogger(classOf[GitBucketReceivePackFactory])
  
  override def create(request: HttpServletRequest, db: Repository): ReceivePack = {
    val receivePack = new ReceivePack(db)
    val userName = request.getAttribute(Keys.Request.UserName).asInstanceOf[String]

    logger.debug("requestURI: " + request.getRequestURI)
    logger.debug("userName:" + userName)

    defining(request.paths){ paths =>
      val owner      = paths(1)
      val repository = paths(2).replaceFirst("\\.git$", "")
      val baseURL    = request.getRequestURL.toString.replaceFirst("/git/.*", "")

      logger.debug("repository:" + owner + "/" + repository)
      logger.debug("baseURL:" + baseURL)

      receivePack.setPostReceiveHook(new CommitLogHook(owner, repository, userName, baseURL))
      receivePack
    }
  }
}

import scala.collection.JavaConverters._

class CommitLogHook(owner: String, repository: String, userName: String, baseURL: String) extends PostReceiveHook
  with RepositoryService with AccountService with IssuesService with ActivityService with PullRequestService with WebHookService {
  
  private val logger = LoggerFactory.getLogger(classOf[CommitLogHook])
  
  def onPostReceive(receivePack: ReceivePack, commands: java.util.Collection[ReceiveCommand]): Unit = {
    using(Git.open(Directory.getRepositoryDir(owner, repository))) { git =>
      commands.asScala.foreach { command =>
        logger.debug(s"commandType: ${command.getType}, refName: ${command.getRefName}")
        val commits = command.getType match {
          case ReceiveCommand.Type.DELETE => Nil
          case _ => JGitUtil.getCommitLog(git, command.getOldId.name, command.getNewId.name)
        }
        val refName = command.getRefName.split("/")
        val branchName = refName.drop(2).mkString("/")

        // Extract new commit and apply issue comment
        val newCommits = if(commits.size > 1000){
          val existIds = getAllCommitIds(owner, repository)
          commits.flatMap { commit =>
            optionIf(!existIds.contains(commit.id)){
              createIssueComment(commit)
              Some(commit)
            }
          }
        } else {
          commits.flatMap { commit =>
            optionIf(!existsCommitId(owner, repository, commit.id)){
              createIssueComment(commit)
              Some(commit)
            }
          }
        }

        // batch insert all new commit id
        insertAllCommitIds(owner, repository, newCommits.map(_.id))

        // record activity
        if(refName(1) == "heads"){
          command.getType match {
            case ReceiveCommand.Type.CREATE => recordCreateBranchActivity(owner, repository, userName, branchName)
            case ReceiveCommand.Type.UPDATE => recordPushActivity(owner, repository, userName, branchName, newCommits)
            case ReceiveCommand.Type.DELETE => recordDeleteBranchActivity(owner, repository, userName, branchName)
            case _ =>
          }
        } else if(refName(1) == "tags"){
          command.getType match {
            case ReceiveCommand.Type.CREATE => recordCreateTagActivity(owner, repository, userName, branchName, newCommits)
            case ReceiveCommand.Type.DELETE => recordDeleteTagActivity(owner, repository, userName, branchName, newCommits)
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
        val webHookURLs = getWebHookURLs(owner, repository)
        if(webHookURLs.nonEmpty){
          val payload = WebHookPayload(
            git,
            getAccountByUserName(userName).get,
            command.getRefName,
            getRepository(owner, repository, baseURL).get,
            newCommits,
            getAccountByUserName(owner).get)

          callWebHook(owner, repository, webHookURLs, payload)
        }
      }
    }
    // update repository last modified time.
    updateLastActivityDate(owner, repository)
  }

  private def createIssueComment(commit: CommitInfo) = {
    "(^|\\W)#(\\d+)(\\W|$)".r.findAllIn(commit.fullMessage).matchData.foreach { matchData =>
      val issueId = matchData.group(2)
      if(getAccountByUserName(commit.committer).isDefined && getIssue(owner, repository, issueId).isDefined){
        createComment(owner, repository, commit.committer, issueId.toInt, commit.fullMessage + " " + commit.id, "commit")
      }
    }
  }

  /**
   * Fetch pull request contents into refs/pull/${issueId}/head and update pull request table.
   */
  private def updatePullRequests(branch: String) =
    getPullRequestsByRequest(owner, repository, branch, false).foreach { pullreq =>
      if(getRepository(pullreq.userName, pullreq.repositoryName, baseURL).isDefined){
        using(Git.open(Directory.getRepositoryDir(pullreq.userName, pullreq.repositoryName))){ git =>
          git.fetch
            .setRemote(Directory.getRepositoryDir(owner, repository).toURI.toString)
            .setRefSpecs(new RefSpec(s"refs/heads/${branch}:refs/pull/${pullreq.issueId}/head").setForceUpdate(true))
            .call

          val commitIdTo = git.getRepository.resolve(s"refs/pull/${pullreq.issueId}/head").getName
          updateCommitIdTo(pullreq.userName, pullreq.repositoryName, pullreq.issueId, commitIdTo)
        }
      }
    }
}
