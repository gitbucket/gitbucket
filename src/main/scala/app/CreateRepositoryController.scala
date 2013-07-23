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
  self: RepositoryService with AccountService with WikiService with LabelsService with ActivityService
    with UsersAuthenticator =>

  case class RepositoryCreationForm(owner: String, name: String, description: Option[String], isPrivate: Boolean, createReadme: Boolean)

  val form = mapping(
    "owner"        -> trim(label("Owner"          , text(required, maxlength(40), identifier))), // TODO check existence.
    "name"         -> trim(label("Repository name", text(required, maxlength(40), identifier, unique))),
    "description"  -> trim(label("Description"    , optional(text()))),
    "isPrivate"    -> trim(label("Repository Type", boolean())),
    "createReadme" -> trim(label("Create README"  , boolean()))
  )(RepositoryCreationForm.apply)

  /**
   * Show the new repository form.
   */
  get("/new")(usersOnly {
    html.newrepo(getGroupsByUserName(context.loginAccount.get.userName))
  })
  
  /**
   * Create new repository.
   */
  post("/new", form)(usersOnly { form =>
    val loginAccount  = context.loginAccount.get
    val loginUserName = loginAccount.userName

    // Insert to the database at first
    createRepository(form.name, form.owner, form.description, form.isPrivate)

    // Insert default labels
    createLabel(form.owner, form.name, "bug", "fc2929")
    createLabel(form.owner, form.name, "duplicate", "cccccc")
    createLabel(form.owner, form.name, "enhancement", "84b6eb")
    createLabel(form.owner, form.name, "invalid", "e6e6e6")
    createLabel(form.owner, form.name, "question", "cc317c")
    createLabel(form.owner, form.name, "wontfix", "ffffff")

    // Create the actual repository
    val gitdir = getRepositoryDir(form.owner, form.name)
    JGitUtil.initRepository(gitdir)

    if(form.createReadme){
      val tmpdir = getInitRepositoryDir(form.owner, form.name)
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
    createWikiRepository(loginAccount, form.owner, form.name)

    // Record activity
    recordCreateRepositoryActivity(loginUserName, form.name, form.owner)

    // redirect to the repository
    redirect(s"/${form.owner}/${form.name}")
  })
  
  /**
   * Duplicate check for the repository name.
   */
  private def unique: Constraint = new Constraint(){
    def validate(name: String, value: String): Option[String] = {
      // TODO fix to retreive user name from request parameter
      getRepositoryNamesOfUser(context.loginAccount.get.userName).find(_ == value).map(_ => "Repository already exists.")
    }
  }
  
}