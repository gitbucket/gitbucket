package servlet

import org.eclipse.jgit.http.server.GitServlet
import org.eclipse.jgit.lib._
import org.eclipse.jgit.transport._
import org.eclipse.jgit.transport.resolver._
import org.slf4j.LoggerFactory

import javax.servlet.ServletConfig
import javax.servlet.ServletContext
import javax.servlet.http.HttpServletRequest
import util.{JGitUtil, Directory}
import service._
import WebHookService._
import org.eclipse.jgit.diff.DiffEntry
import org.apache.http.client.methods.HttpPost

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
    });

    super.init(config)
  }
  
}

class GitBucketReceivePackFactory extends ReceivePackFactory[HttpServletRequest] {
  
  private val logger = LoggerFactory.getLogger(classOf[GitBucketReceivePackFactory])
  
  override def create(request: HttpServletRequest, db: Repository): ReceivePack = {
    val receivePack = new ReceivePack(db)
    val userName = request.getAttribute("USER_NAME").asInstanceOf[String]

    logger.debug("requestURI: " + request.getRequestURI)
    logger.debug("userName:" + userName)

    val paths      = request.getRequestURI.substring(request.getContextPath.length).split("/")
    val owner      = paths(2)
    val repository = paths(3).replaceFirst("\\.git$", "")
    val baseURL    = request.getRequestURL.toString.replaceFirst("/git/", "/").replaceFirst("\\.git/.*$", "")

    logger.debug("repository:" + owner + "/" + repository)
    logger.debug("baseURL:" + baseURL)

    receivePack.setPostReceiveHook(new CommitLogHook(owner, repository, userName, baseURL))
    receivePack
  }
}

import scala.collection.JavaConverters._

class CommitLogHook(owner: String, repository: String, userName: String, baseURL: String) extends PostReceiveHook
  with RepositoryService with AccountService with IssuesService with ActivityService with WebHookService {
  
  private val logger = LoggerFactory.getLogger(classOf[CommitLogHook])
  
  def onPostReceive(receivePack: ReceivePack, commands: java.util.Collection[ReceiveCommand]): Unit = {
    JGitUtil.withGit(Directory.getRepositoryDir(owner, repository)) { git =>
      commands.asScala.foreach { command =>
        val commits = JGitUtil.getCommitLog(git, command.getOldId.name, command.getNewId.name)
        val refName = command.getRefName.split("/")
        
        // apply issue comment
        val newCommits = commits.flatMap { commit =>
          if(!existsCommitId(owner, repository, commit.id)){
            insertCommitId(owner, repository, commit.id)
            "(^|\\W)#(\\d+)(\\W|$)".r.findAllIn(commit.fullMessage).matchData.foreach { matchData =>
              val issueId = matchData.group(2)
              if(getAccountByUserName(commit.committer).isDefined && getIssue(owner, repository, issueId).isDefined){
                createComment(owner, repository, commit.committer, issueId.toInt, commit.fullMessage, "commit")
              }
            }
            Some(commit)
          } else None
        }.toList
        
        // record activity
        if(refName(1) == "heads"){
          command.getType match {
            case ReceiveCommand.Type.CREATE => {
              recordCreateBranchActivity(owner, repository, userName, refName(2))
              recordPushActivity(owner, repository, userName, refName(2), newCommits)
            }
            case ReceiveCommand.Type.UPDATE => recordPushActivity(owner, repository, userName, refName(2), newCommits)
            case _ =>
          }
        } else if(refName(1) == "tags"){
          command.getType match {
            case ReceiveCommand.Type.CREATE => recordCreateTagActivity(owner, repository, userName, refName(2), newCommits)
            case _ =>
          }
        }

        // call web hook
        val repositoryInfo  = getRepository(owner, repository, "").get
        val repositoryOwner = getAccountByUserName(owner)

        val payload = WebHookPayload(
          ref     = command.getRefName,
          commits = newCommits.map { commit =>
            val diffs = JGitUtil.getDiffs(git, commit.id, false)

            WebHookCommit(
              id        = commit.id,
              message   = commit.fullMessage,
              timestamp = commit.time.toString,
              url       = baseURL + "/commit/" + commit.id,
              added     = diffs._1.collect { case x if(x.changeType == DiffEntry.ChangeType.ADD)    => x.newPath },
              removed   = diffs._1.collect { case x if(x.changeType == DiffEntry.ChangeType.DELETE) => x.oldPath },
              modified  = diffs._1.collect { case x if(x.changeType != DiffEntry.ChangeType.ADD &&
                                                       x.changeType != DiffEntry.ChangeType.DELETE) => x.newPath },
              author    = WebHookUser(
                name  = commit.committer,
                email = commit.mailAddress
              )
            )
          }.toList,
          repository = WebHookRepository(
            name        = repositoryInfo.name,
            url         = baseURL,
            description = repositoryInfo.repository.description.getOrElse(""),
            watchers    = 0,
            forks       = repositoryInfo.forkedCount,
            `private`   = repositoryInfo.repository.isPrivate,
            owner = WebHookUser(
              name  = repositoryOwner.get.userName,
              email = repositoryOwner.get.mailAddress
            )
          )
        )

        callWebHook(owner, repository, payload)
      }
    }
    // update repository last modified time.
    updateLastActivityDate(owner, repository)
  }
}
