package app

import util.Directory._
import org.scalatra._
import java.io.File
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib._
import org.apache.commons.io._

/**
 * Creates new repository.
 */
class CreateRepositoryServlet extends ScalatraServlet with ServletBase {
    
  /**
   * Show the new repository form.
   */
  get("/") {
    html.newrepo.render()
  }
  
  /**
   * Create new repository.
   */
  post("/") {
    val repositoryName = params("name")
    val description = params("description")
    
    val dir = getRepositoryDir(LoginUser, repositoryName)
    val repository = new RepositoryBuilder()
          .setGitDir(dir)
          .setBare
          .build
    
    repository.create
    
    
    if(description.nonEmpty){
      val tmpdir = getInitRepositoryDir(LoginUser, repositoryName)
      Git.cloneRepository.setURI(dir.toURI.toString).setDirectory(tmpdir).call
      
      // Create README.md
      val readme = new File(tmpdir, "README.md")
      FileUtils.writeStringToFile(readme, 
          repositoryName + "\n===============\n\n" + description, "UTF-8")
      
      val git = Git.open(tmpdir)
      git.add.addFilepattern("README.md").call
      git.commit.setMessage("Initial commit").call
      git.push.call
      
      FileUtils.deleteDirectory(tmpdir)
    }
    
    // redirect to the repository
    redirect("/%s/%s".format(LoginUser, repositoryName))
  }
}