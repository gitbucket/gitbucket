package app

import util.Directory._
import util.Validations._
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
    withValidation(form, params){ form =>
      val gitdir = getRepositoryDir(LoginUser, form.name)
      val repository = new RepositoryBuilder().setGitDir(gitdir).setBare.build
    
      repository.create
    
      val config = repository.getConfig
      config.setBoolean("http", null, "receivepack", true)
      config.save
    
      val tmpdir = getInitRepositoryDir(LoginUser, form.name)
      try {
        // Clone the repository
        Git.cloneRepository.setURI(gitdir.toURI.toString).setDirectory(tmpdir).call
    
        // Create README.md
        FileUtils.writeStringToFile(new File(tmpdir, "README.md"), if(form.description.nonEmpty){
            form.name + "\n===============\n\n" + form.description
          } else {
            form.name + "\n===============\n"
          }, "UTF-8")
    
        val git = Git.open(tmpdir)
        git.add.addFilepattern("README.md").call
        git.commit.setMessage("Initial commit").call
        git.push.call
      
      } finally {
        FileUtils.deleteDirectory(tmpdir)
      }
      
      // redirect to the repository
      redirect("/%s/%s".format(LoginUser, form.name))
    }
  }
  
  get("/validate") {
    contentType = "application/json"
    form.validateAsJSON(params)
  }
  
  val form = mapping(
      "name"        -> trim(label("Repository name", text(required, maxlength(40), repository))), 
      "description" -> trim(label("Description"    , text()))
  )(RepositoryCreationForm.apply)

  def repository: Constraint = new Constraint(){
    def validate(name: String, value: String): Option[String] = {
      if(!value.matches("^[a-z0-9\\-_]+$")){
        Some("Repository name contains invalid character.")
      } else if(getRepositories(LoginUser).contains(value)){
        Some("Repository already exists.")
      } else {
        None
      }
    }
  }
  
  case class RepositoryCreationForm(name: String, description: String)
  
}