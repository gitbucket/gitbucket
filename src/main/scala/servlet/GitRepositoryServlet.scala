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
    val userName = request.getAttribute("USER_NAME").asInstanceOf[String]

    logger.debug("requestURI: " + request.getRequestURI)
    logger.debug("userName:" + userName)

    val paths = request.getRequestURI.substring(request.getContextPath.length).split("/")
    val owner      = paths(2)
    val repository = paths(3).replaceFirst("\\.git$", "")
    
    logger.debug("repository:" + owner + "/" + repository)

    receivePack.setPostReceiveHook(new CommitLogHook(owner, repository, userName))
    receivePack
  }
}

import scala.collection.JavaConverters._

class CommitLogHook(owner: String, repository: String, userName: String) extends PostReceiveHook
  with RepositoryService with AccountService with IssuesService with ActivityService {
  
  private val logger = LoggerFactory.getLogger(classOf[CommitLogHook])
  
  def onPostReceive(receivePack: ReceivePack, commands: java.util.Collection[ReceiveCommand]): Unit = {
    JGitUtil.withGit(Directory.getRepositoryDir(owner, repository)) { git =>
      commands.asScala.foreach { command =>
        val commits = JGitUtil.getCommitLog(git, command.getOldId.name, command.getNewId.name)
          .filter(commit => JGitUtil.getBranchesOfCommit(git, commit.id).length == 1)
        val refName = command.getRefName.split("/")
        
        println("****************************")
        println(command.getRefName)
        println(command.getMessage)
        println(command.getResult)
        println(command.getType)
        println("****************************")
        
        // apply issue comment
        if(refName(1) == "heads"){
          commits.foreach { commit =>
            "(^|\\W)#(\\d+)(\\W|$)".r.findAllIn(commit.fullMessage).matchData.foreach { matchData =>
              val issueId = matchData.group(2)
              if(getAccountByUserName(commit.committer).isDefined && getIssue(owner, repository, issueId).isDefined){
                createComment(owner, repository, commit.committer, issueId.toInt, commit.fullMessage, None)
              }
            }
          }
        }
        
        git.getRepository.getAllRefs.asScala.map { e =>
          println(e._1)
        }
        
        // record activity
        if(refName(1) == "heads"){
          recordPushActivity(owner, repository, userName, refName(2), commits)
        } else if(refName(1) == "tags"){
          recordCreateTagActivity(owner, repository, userName, refName(2), commits)
        }
      }
    }
    // update repository last modified time.
    updateLastActivityDate(owner, repository)
  }
}
