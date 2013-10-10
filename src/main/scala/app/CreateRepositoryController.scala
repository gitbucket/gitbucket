package app

import util.Directory._
import util.ControlUtil._
import util._
import service._
import java.io.File
import org.eclipse.jgit.api.Git
import org.apache.commons.io._
import jp.sf.amateras.scalatra.forms._
import org.eclipse.jgit.lib.PersonIdent

class CreateRepositoryController extends CreateRepositoryControllerBase
  with RepositoryService with AccountService with WikiService with LabelsService with ActivityService
  with UsersAuthenticator with ReadableUsersAuthenticator

/**
 * Creates new repository.
 */
trait CreateRepositoryControllerBase extends ControllerBase {
  self: RepositoryService with AccountService with WikiService with LabelsService with ActivityService
    with UsersAuthenticator with ReadableUsersAuthenticator =>

  case class RepositoryCreationForm(owner: String, name: String, description: Option[String], isPrivate: Boolean, createReadme: Boolean)

  case class ForkRepositoryForm(owner: String, name: String)

  val newForm = mapping(
    "owner"        -> trim(label("Owner"          , text(required, maxlength(40), identifier, existsAccount))),
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
    html.newrepo(getGroupsByUserName(context.loginAccount.get.userName))
  })
  
  /**
   * Create new repository.
   */
  post("/new", newForm)(usersOnly { form =>
    LockUtil.lock(s"${form.owner}/${form.name}/create"){
      if(getRepository(form.owner, form.name, baseUrl).isEmpty){
        val ownerAccount  = getAccountByUserName(form.owner).get
        val loginAccount  = context.loginAccount.get
        val loginUserName = loginAccount.userName

        // Insert to the database at first
        createRepository(form.name, form.owner, form.description, form.isPrivate)

        // Add collaborators for group repository
        if(ownerAccount.isGroupAccount){
          getGroupMembers(form.owner).foreach { userName =>
            addCollaborator(form.owner, form.name, userName)
          }
        }

        // Insert default labels
        insertDefaultLabels(form.owner, form.name)

        // Create the actual repository
        val gitdir = getRepositoryDir(form.owner, form.name)
        JGitUtil.initRepository(gitdir)

        if(form.createReadme){
          FileUtil.withTmpDir(getInitRepositoryDir(form.owner, form.name)){ tmpdir =>
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
            git.commit
              .setCommitter(new PersonIdent(loginUserName, loginAccount.mailAddress))
              .setMessage("Initial commit").call
            git.push.call
          }
        }

        // Create Wiki repository
        createWikiRepository(loginAccount, form.owner, form.name)

        // Record activity
        recordCreateRepositoryActivity(form.owner, form.name, loginUserName)
      }

      // redirect to the repository
      redirect(s"/${form.owner}/${form.name}")
    }
  })

  get("/:owner/:repository/fork")(readableUsersOnly { repository =>
    val loginAccount   = context.loginAccount.get
    val loginUserName  = loginAccount.userName

    LockUtil.lock(s"${loginUserName}/${repository.name}/create"){
      if(getRepository(loginUserName, repository.name, baseUrl).isEmpty){
        // Insert to the database at first
        val originUserName = repository.repository.originUserName.getOrElse(repository.owner)
        val originRepositoryName = repository.repository.originRepositoryName.getOrElse(repository.name)

        createRepository(
          repositoryName       = repository.name,
          userName             = loginUserName,
          description          = repository.repository.description,
          isPrivate            = repository.repository.isPrivate,
          originRepositoryName = Some(originRepositoryName),
          originUserName       = Some(originUserName),
          parentRepositoryName = Some(repository.name),
          parentUserName       = Some(repository.owner)
        )

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

        // insert commit id
        using(Git.open(getRepositoryDir(loginUserName, repository.name))){ git =>
          JGitUtil.getRepositoryInfo(loginUserName, repository.name, baseUrl).branchList.foreach { branch =>
            JGitUtil.getCommitLog(git, branch) match {
              case Right((commits, _)) => commits.foreach { commit =>
                if(!existsCommitId(loginUserName, repository.name, commit.id)){
                  insertCommitId(loginUserName, repository.name, commit.id)
                }
              }
              case Left(_) => ???
            }
          }
        }

        // Record activity
        recordForkActivity(repository.owner, repository.name, loginUserName)
      }
      // redirect to the repository
      redirect("/%s/%s".format(loginUserName, repository.name))
    }
  })

  private def insertDefaultLabels(userName: String, repositoryName: String): Unit = {
    createLabel(userName, repositoryName, "bug", "fc2929")
    createLabel(userName, repositoryName, "duplicate", "cccccc")
    createLabel(userName, repositoryName, "enhancement", "84b6eb")
    createLabel(userName, repositoryName, "invalid", "e6e6e6")
    createLabel(userName, repositoryName, "question", "cc317c")
    createLabel(userName, repositoryName, "wontfix", "ffffff")
  }

  private def existsAccount: Constraint = new Constraint(){
    override def validate(name: String, value: String): Option[String] =
      if(getAccountByUserName(value).isEmpty) Some("User or group does not exist.") else None
  }

  /**
   * Duplicate check for the repository name.
   */
  private def unique: Constraint = new Constraint(){
    override def validate(name: String, value: String, params: Map[String, String]): Option[String] =
      params.get("owner").flatMap { userName =>
        getRepositoryNamesOfUser(userName).find(_ == value).map(_ => "Repository already exists.")
      }
  }
  
}
