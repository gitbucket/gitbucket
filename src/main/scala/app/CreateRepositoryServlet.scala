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
    
    val gitdir = getRepositoryDir(LoginUser, repositoryName)
    val repository = new RepositoryBuilder().setGitDir(gitdir).setBare.build
    
    repository.create
    
    val config = repository.getConfig
    config.setBoolean("http", null, "receivepack", true)
    config.save
    
    val tmpdir = getInitRepositoryDir(LoginUser, repositoryName)
    try {
      // Clone the repository
      Git.cloneRepository.setURI(gitdir.toURI.toString).setDirectory(tmpdir).call
    
      // Create README.md
      val readme = new File(tmpdir, "README.md")
    
      FileUtils.writeStringToFile(readme, if(description.nonEmpty){
          repositoryName + "\n===============\n\n" + description
        } else {
          repositoryName + "\n===============\n"
        }, "UTF-8")
    
      val git = Git.open(tmpdir)
      git.add.addFilepattern("README.md").call
      git.commit.setMessage("Initial commit").call
      git.push.call
      
    } finally {
      FileUtils.deleteDirectory(tmpdir)
    }
      
    // redirect to the repository
    redirect("/%s/%s".format(LoginUser, repositoryName))
  }
}