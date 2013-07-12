package app

import util.Directory._
import util.{JGitUtil, UsersAuthenticator, ReferrerAuthenticator}
import service._
import java.io.File
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib._
import org.apache.commons.io._
import jp.sf.amateras.scalatra.forms._

class CreateRepositoryController extends CreateRepositoryControllerBase
  with RepositoryService with AccountService with WikiService with LabelsService with ActivityService
  with UsersAuthenticator with ReferrerAuthenticator

/**
 * Creates new repository.
 */
trait CreateRepositoryControllerBase extends ControllerBase {
  self: RepositoryService with WikiService with LabelsService with ActivityService
    with UsersAuthenticator with ReferrerAuthenticator =>

  case class RepositoryCreationForm(name: String, description: Option[String], isPrivate: Boolean, createReadme: Boolean)

  case class ForkRepositoryForm(owner: String, name: String)

  val newForm = mapping(
    "name"         -> trim(label("Repository name", text(required, maxlength(40), identifier, unique))),
    "description"  -> trim(label("Description"    , optional(text()))),
    "isPrivate"    -> trim(label("Repository Type", boolean())),
    "createReadme" -> trim(label("Create README"  , boolean()))
  )(RepositoryCreationForm.apply)

  val forkForm = mapping(
    "owner" -> trim(label("Repository owner", text(required))),
    "name"  -> trim(label("Repository name",  text(required)))
  )(ForkRepositoryForm.apply)

  /**
   * Show the new repository form.
   */
  get("/new")(usersOnly {
    html.newrepo()
  })
  
  /**
   * Create new repository.
   */
  post("/new", newForm)(usersOnly { form =>
    val loginAccount  = context.loginAccount.get
    val loginUserName = loginAccount.userName

    // Insert to the database at first
    createRepository(form.name, loginUserName, form.description, form.isPrivate)

    // Insert default labels
    insertDefaultLabels(loginUserName, form.name)

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

  post("/:owner/:repository/_fork")(referrersOnly { repository =>
    val loginAccount   = context.loginAccount.get
    val loginUserName  = loginAccount.userName

    if(getRepository(loginUserName, repository.name, baseUrl).isEmpty){
      // Insert to the database at first
      // TODO Is private repository cloneable?
      createRepository(repository.name, loginUserName, repository.repository.description,
        repository.repository.isPrivate, Some(repository.name), Some(repository.owner))

      // Insert default labels
      insertDefaultLabels(loginUserName, repository.name)

      // clone repository actually
      JGitUtil.cloneRepository(
        getRepositoryDir(repository.owner, repository.name),
        getRepositoryDir(loginUserName, repository.name))

      // Create Wiki repository
      JGitUtil.cloneRepository(
        getWikiRepositoryDir(repository.owner, repository.name),
        getWikiRepositoryDir(loginUserName, repository.name))

      // Record activity
      recordForkActivity(repository.owner, repository.name, loginUserName)
    }
    // redirect to the repository
    redirect("/%s/%s".format(loginUserName, repository.name))
  })

  private def insertDefaultLabels(userName: String, repositoryName: String): Unit = {
    createLabel(userName, repositoryName, "bug", "fc2929")
    createLabel(userName, repositoryName, "duplicate", "cccccc")
    createLabel(userName, repositoryName, "enhancement", "84b6eb")
    createLabel(userName, repositoryName, "invalid", "e6e6e6")
    createLabel(userName, repositoryName, "question", "cc317c")
    createLabel(userName, repositoryName, "wontfix", "ffffff")
  }

  /**
   * Duplicate check for the repository name.
   */
  private def unique: Constraint = new Constraint(){
    def validate(name: String, value: String): Option[String] =
      getRepositoryNamesOfUser(context.loginAccount.get.userName).find(_ == value).map(_ => "Repository already exists.")
  }
  
}