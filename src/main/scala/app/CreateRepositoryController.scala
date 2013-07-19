package app

import util.Directory._
import util.{JGitUtil, UsersAuthenticator}
import service._
import java.io.File
import org.eclipse.jgit.api.Git
import org.apache.commons.io._
import jp.sf.amateras.scalatra.forms._

class CreateRepositoryController extends CreateRepositoryControllerBase
  with RepositoryService with AccountService with WikiService with LabelsService with ActivityService
  with UsersAuthenticator

/**
 * Creates new repository.
 */
trait CreateRepositoryControllerBase extends ControllerBase {
  self: RepositoryService with WikiService with LabelsService with ActivityService
    with UsersAuthenticator =>

  case class RepositoryCreationForm(name: String, description: Option[String], isPrivate: Boolean, createReadme: Boolean)

  val form = mapping(
    "name"         -> trim(label("Repository name", text(required, maxlength(40), identifier, unique))),
    "description"  -> trim(label("Description"    , optional(text()))),
    "isPrivate"    -> trim(label("Repository Type", boolean())),
    "createReadme" -> trim(label("Create README"  , boolean()))
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
    val loginAccount  = context.loginAccount.get
    val loginUserName = loginAccount.userName

    // Insert to the database at first
    createRepository(form.name, loginUserName, form.description, form.isPrivate)

    // Insert default labels
    createLabel(loginUserName, form.name, "bug", "fc2929")
    createLabel(loginUserName, form.name, "duplicate", "cccccc")
    createLabel(loginUserName, form.name, "enhancement", "84b6eb")
    createLabel(loginUserName, form.name, "invalid", "e6e6e6")
    createLabel(loginUserName, form.name, "question", "cc317c")
    createLabel(loginUserName, form.name, "wontfix", "ffffff")

    // Create the actual repository
    val gitdir = getRepositoryDir(loginUserName, form.name)
    JGitUtil.initRepository(gitdir)

    if(form.createReadme){
      val tmpdir = getInitRepositoryDir(loginUserName, form.name)
      try {
        // Clone the repository
        Git.cloneRepository.setURI(gitdir.toURI.toString).setDirectory(tmpdir).call

        // Create README.md
        FileUtils.writeStringToFile(new File(tmpdir, "README.md"),
          if(form.description.nonEmpty){
            form.name + "\n" +
            "===============\n" +
            "\n" +
            form.description.get
          } else {
            form.name + "\n" +
            "===============\n"
          }, "UTF-8")

        val git = Git.open(tmpdir)
        git.add.addFilepattern("README.md").call
        git.commit.setMessage("Initial commit").call
        git.push.call

      } finally {
        FileUtils.deleteDirectory(tmpdir)
      }
    }

    // Create Wiki repository
    createWikiRepository(loginAccount, form.name)

    // Record activity
    recordCreateRepositoryActivity(loginUserName, form.name, loginUserName)

    // redirect to the repository
    redirect(s"/${loginUserName}/${form.name}")
  })
  
  /**
   * Duplicate check for the repository name.
   */
  private def unique: Constraint = new Constraint(){
    def validate(name: String, value: String): Option[String] =
      getRepositoryNamesOfUser(context.loginAccount.get.userName).find(_ == value).map(_ => "Repository already exists.")
  }
  
}