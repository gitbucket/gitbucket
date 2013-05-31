package servlet

import org.eclipse.jgit.http.server.GitServlet
import org.eclipse.jgit.lib._
import org.eclipse.jgit.transport._
import org.eclipse.jgit.transport.resolver._
import org.slf4j.LoggerFactory

import javax.servlet.ServletConfig
import javax.servlet.ServletContext
import javax.servlet.http.HttpServletRequest
import util.Directory

/**
 * Provides Git repository via HTTP.
 * 
 * This servlet provides only Git repository functionality.
 * Authentication is provided by [[app.BasicAuthenticationFilter]].
 */
class GitRepositoryServlet extends GitServlet {

  private val logger = LoggerFactory.getLogger(classOf[GitRepositoryServlet])
  
  override def init(config: ServletConfig): Unit = {
    setReceivePackFactory(new GitBucketRecievePackFactory())
    
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

class GitBucketRecievePackFactory extends ReceivePackFactory[HttpServletRequest] {
  override def create(req: HttpServletRequest, db: Repository): ReceivePack = {
    val receivePack = new ReceivePack(db)
    
    println("----")
    println("contextPath: " + req.getContextPath)
    println("requestURI: " + req.getRequestURI)
    println("remoteUser:" + req.getRemoteUser)
    
    val userName = req.getSession.getAttribute("USER_INFO")
    println("userName: " + userName)
    
    println("----")
    
    receivePack.setPostReceiveHook(new CommitLogHook())
    receivePack
  }
}

import scala.collection.JavaConverters._

class CommitLogHook extends PostReceiveHook {
  def onPostReceive(receivePack: ReceivePack, commands: java.util.Collection[ReceiveCommand]): Unit = {
    println("**** hook ****")
    commands.asScala.foreach { command =>
      println(command.getRefName)
      println(command.getMessage)
      println(command.getRef().getName())
      println("--")
    }
    
    
  }
}
