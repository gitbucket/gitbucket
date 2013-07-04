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
  }
  
}

class GitBucketReceivePackFactory extends ReceivePackFactory[HttpServletRequest] {
  
  private val logger = LoggerFactory.getLogger(classOf[GitBucketReceivePackFactory])
  
  override def create(request: HttpServletRequest, db: Repository): ReceivePack = {
    val receivePack = new ReceivePack(db)
    val userName = request.getAttribute("USER_NAME")

    logger.debug("requestURI: " + request.getRequestURI)
    logger.debug("userName:" + userName)

    val paths = request.getRequestURI.substring(request.getContextPath.length).split("/")
    val owner      = paths(2)
    val repository = paths(3).replaceFirst("\\.git$", "")
    
    logger.debug("repository:" + owner + "/" + repository)

    receivePack.setPostReceiveHook(new CommitLogHook(owner, repository))
    receivePack
  }
}

import scala.collection.JavaConverters._

class CommitLogHook(owner: String, repository: String) extends PostReceiveHook
  with RepositoryService with AccountService with IssuesService {
  
  private val logger = LoggerFactory.getLogger(classOf[CommitLogHook])
  
  def onPostReceive(receivePack: ReceivePack, commands: java.util.Collection[ReceiveCommand]): Unit = {
    JGitUtil.withGit(Directory.getRepositoryDir(owner, repository)) { git =>
      commands.asScala.foreach { command =>
        JGitUtil.getCommitLog(git, command.getOldId.name, command.getNewId.name).foreach { commit =>
          "(^|\\W)#(\\d+)(\\W|$)".r.findAllIn(commit.fullMessage).matchData.foreach { matchData =>
            val issueId = matchData.group(2)
            if(getAccountByUserName(commit.committer).isDefined && getIssue(owner, repository, issueId).isDefined){
              createComment(owner, repository, commit.committer, issueId.toInt, commit.fullMessage, None)
            }
          }
        }
      }
    }
    // update repository last modified time.
    updateLastActivityDate(owner, repository)
  }
}
