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
  with RepositoryService with AccountService with WikiService with LabelsService with UsersOnlyAuthenticator

/**
 * Creates new repository.
 */
trait CreateRepositoryControllerBase extends ControllerBase {
  self: RepositoryService with WikiService with LabelsService with UsersOnlyAuthenticator =>

  case class RepositoryCreationForm(name: String, description: Option[String])

  val form = mapping(
    "name"        -> trim(label("Repository name", text(required, maxlength(40), identifier, unique))),
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

    // Insert default labels
    createLabel(loginUserName, form.name, "bug", "fc2929")
    createLabel(loginUserName, form.name, "duplicate", "cccccc")
    createLabel(loginUserName, form.name, "enhancement", "84b6eb")
    createLabel(loginUserName, form.name, "invalid", "e6e6e6")
    createLabel(loginUserName, form.name, "question", "cc317c")
    createLabel(loginUserName, form.name, "wontfix", "ffffff")

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
   * Duplicate check for the repository name.
   */
  private def unique: Constraint = new Constraint(){
    def validate(name: String, value: String): Option[String] =
      getRepositoryNamesOfUser(context.loginAccount.get.userName).find(_ == value).map(_ => "Repository already exists.")
  }
  
}