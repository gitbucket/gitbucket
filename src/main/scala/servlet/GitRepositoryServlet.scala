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
  
  override def create(req: HttpServletRequest, db: Repository): ReceivePack = {
    val receivePack = new ReceivePack(db)
    val userName = req.getAttribute("USER_NAME")
    
    logger.debug("requestURI: " + req.getRequestURI)
    logger.debug("userName:" + userName)
    
    val pathList = req.getRequestURI.split("/")
    val owner      = pathList(2)
    val repository = pathList(3).replaceFirst("\\.git$", "")
    
    logger.debug("repository:" + owner + "/" + repository)
    
    receivePack.setPostReceiveHook(new CommitLogHook(owner, repository))
    receivePack
  }
}

import scala.collection.JavaConverters._

class CommitLogHook(owner: String, repository: String) extends PostReceiveHook {
  
  private val logger = LoggerFactory.getLogger(classOf[CommitLogHook])
  
  def onPostReceive(receivePack: ReceivePack, commands: java.util.Collection[ReceiveCommand]): Unit = {
    JGitUtil.withGit(Directory.getRepositoryDir(owner, repository)) { git =>
      commands.asScala.foreach { command =>
        JGitUtil.getCommitLog(git, command.getOldId.name, command.getNewId.name).foreach { commit =>
          // TODO extract issue id and add comment to issue
          logger.debug(commit.id + ":" + commit.shortMessage)
        }
      }
    }
    
    // TODO update repository last modified time.
    
  }
}
