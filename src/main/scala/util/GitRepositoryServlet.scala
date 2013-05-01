package util

import java.io._
import javax.servlet._
import javax.servlet.http._
import util.Directory
import org.eclipse.jgit.http.server.GitServlet
import org.slf4j.LoggerFactory

/**
 * Provides Git repository via HTTP.
 * 
 * This servlet provides only Git repository functionality.
 * Authentication is provided by [[app.BasicAuthenticationFilter]].
 */
class GitRepositoryServlet extends GitServlet {

  private val logger = LoggerFactory.getLogger(classOf[GitRepositoryServlet])
  
  // TODO are there any other ways...?
  override def init(config: ServletConfig): Unit = {
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
