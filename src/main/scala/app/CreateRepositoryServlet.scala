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
class CreateRepositoryServlet extends ServletBase {
  
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
    withValidation(validate, params){
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
        FileUtils.writeStringToFile(new File(tmpdir, "README.md"), if(description.nonEmpty){
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
  
  get("/validate") {
    contentType = "application/json"
    validate(params).toJSON
  }
  
  def validate(params: Map[String, String]): ValidationResult = {
    val name = params("name")
    if(name.isEmpty){
      ValidationResult(false, Map("name" -> "Repository name is required."))
    } else if(!name.matches("^[a-z0-6\\-_]+$")){
      ValidationResult(false, Map("name" -> "Repository name contans invalid character."))
    } else if(getRepositories(LoginUser).contains(name)){
      ValidationResult(false, Map("name" -> "Repository already exists."))
    } else {
      ValidationResult(true, Map.empty)
    }
  }
}