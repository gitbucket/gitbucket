package app

import util.Directory._
import util.UsersOnlyAuthenticator
import service._
import java.io.File
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib._
import org.apache.commons.io._
import jp.sf.amateras.scalatra.forms._

class CreateRepositoryController extends CreateRepositoryControllerBase
  with RepositoryService with AccountService with WikiService with UsersOnlyAuthenticator

/**
 * Creates new repository.
 */
trait CreateRepositoryControllerBase extends ControllerBase {
  self: RepositoryService with WikiService with UsersOnlyAuthenticator =>

  case class RepositoryCreationForm(name: String, description: Option[String])

  val form = mapping(
    "name"        -> trim(label("Repository name", text(required, maxlength(40), repository))),
    "description" -> trim(label("Description"    , optional(text())))
  )(RepositoryCreationForm.apply)

  /**
   * Show the new repository form.
   */
  get("/new")(usersOnly {
    html.newrepo()
  })
  
  /**
   * Create new repository.
   */
  post("/new", form)(usersOnly { form =>
    val loginUserName = context.loginAccount.get.userName

    // Insert to the database at first
    createRepository(form.name, loginUserName, form.description)

    // Create the actual repository
    val gitdir = getRepositoryDir(loginUserName, form.name)
    val repository = new RepositoryBuilder().setGitDir(gitdir).setBare.build

    repository.create

    val config = repository.getConfig
    config.setBoolean("http", null, "receivepack", true)
    config.save

    val tmpdir = getInitRepositoryDir(loginUserName, form.name)
    try {
      // Clone the repository
      Git.cloneRepository.setURI(gitdir.toURI.toString).setDirectory(tmpdir).call
    
      // Create README.md
      FileUtils.writeStringToFile(new File(tmpdir, "README.md"),
        if(form.description.nonEmpty){
          form.name + "\n===============\n\n" + form.description.get
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

    // Create Wiki repository
    createWikiRepository(context.loginAccount.get, form.name)

    // redirect to the repository
    redirect("/%s/%s".format(loginUserName, form.name))
  })
  
  /**
   * Constraint for the repository name.
   */
  def repository: Constraint = new Constraint(){
    def validate(name: String, value: String): Option[String] = {
      if(!value.matches("^[a-zA-Z0-9\\-_]+$")){
        Some("Repository name contains invalid character.")
      } else if(value.startsWith("_") || value.startsWith("-")){
        Some("Repository name starts with invalid character.")
      } else if(getRepositoryNamesOfUser(context.loginAccount.get.userName).contains(value)){
        Some("Repository already exists.")
      } else {
        None
      }
    }
  }
  
}