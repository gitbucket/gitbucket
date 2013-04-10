package app

import util.Directory._

import org.scalatra._
import java.io.File
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib._
import org.apache.commons.io.FileUtils

/**
 * Creates new repository.
 */
class CreateRepositoryServlet extends ScalatraServlet with ServletBase {
    
  /**
   * Show the new repository form.
   */
  get("/new") {
    html.newrepo.render()
  }
  
  /**
   * Create new repository.
   */
  post("/new") {
    val repositoryName = params("name")
    val description = params("description")
    
    val dir = new File(getRepositoryDir(LoginUser, repositoryName), ".git")
    val repository = new RepositoryBuilder()
          .setGitDir(dir)
          .build()
    
    repository.create
    
    if(description.nonEmpty){
      // Create README.md
      val readme = new File(dir.getParentFile, "README.md")
      FileUtils.writeStringToFile(readme, 
          repositoryName + "\n===============\n\n" + description, "UTF-8")
      
      val git = new Git(repository)
      git.add.addFilepattern("README.md").call
      git.commit.setMessage("Initial commit").call
    }
    
    // redirect to the repository
    redirect("/%s/%s".format(LoginUser, repositoryName))
  }
}