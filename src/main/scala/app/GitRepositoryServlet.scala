package app

import java.io._
import javax.servlet._
import javax.servlet.http._
import util.Directory
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Ref
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
  
  /**
   * Override GitServlet#service() to pull pushed changes to cloned repositories for branch exploring.
   */
  override def service(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    super.service(request, response)
    
    logger.debug(request.getMethod + ": " + request.getRequestURI)
    
    // update branches
    if(request.getMethod == "POST" && request.getRequestURI.endsWith("/git-receive-pack")){
      request.getRequestURI
          .replaceFirst("^" + request.getServletContext.getContextPath + "/git/", "")
          .replaceFirst("\\.git/git-receive-pack$", "").split("/") match {
        case Array(owner, repository) => Directory.updateAllBranches(owner, repository)
      }
    }
  }
  
}
