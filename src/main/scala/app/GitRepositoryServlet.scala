package app

import javax.servlet.ServletConfig
import javax.servlet.ServletException
import org.eclipse.jgit.http.server.GitServlet
import javax.servlet.ServletContext
import util.Directory
import java.io.File

/**
 * Provides Git repository via HTTP.
 * 
 * This servlet provides only Git repository functionality.
 * Authentication is provided by [[app.BasicAuthenticationFilter]].
 */
class GitRepositoryServlet extends GitServlet {

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