package app

import util.Directory._
import util.ControlUtil._
import util._
import service._
import org.eclipse.jgit.api.Git
import jp.sf.amateras.scalatra.forms._
import org.eclipse.jgit.lib.{FileMode, Constants}
import org.eclipse.jgit.dircache.DirCache
import org.scalatra.i18n.Messages
import org.apache.commons.io.FileUtils

class CreateRepositoryController extends CreateRepositoryControllerBase
  with RepositoryService with AccountService with WikiService with LabelsService with ActivityService
  with UsersAuthenticator with ReadableUsersAuthenticator

/**
 * Creates new repository or group.
 */
trait CreateRepositoryControllerBase extends AccountManagementControllerBase {
  self: RepositoryService with AccountService with WikiService with LabelsService with ActivityService
    with UsersAuthenticator with ReadableUsersAuthenticator =>

  case class RepositoryCreationForm(owner: String, name: String, description: Option[String],
                                    isPrivate: Boolean, createReadme: Boolean)

  case class ForkRepositoryForm(owner: String, name: String)

  val newRepositoryForm = mapping(
    "owner"        -> trim(label("Owner"          , text(required, maxlength(40), identifier, existsAccount))),
    "name"         -> trim(label("Repository name", text(required, maxlength(40), identifier, uniqueRepository))),
    "description"  -> trim(label("Description"    , optional(text()))),
    "isPrivate"    -> trim(label("Repository Type", boolean())),
    "createReadme" -> trim(label("Create README"  , boolean()))
  )(RepositoryCreationForm.apply)

  val forkRepositoryForm = mapping(
    "owner" -> trim(label("Repository owner", text(required))),
    "name"  -> trim(label("Repository name",  text(required)))
  )(ForkRepositoryForm.apply)

  case class NewGroupForm(groupName: String, url: Option[String], fileId: Option[String],
                          memberNames: Option[String])

  case class EditGroupForm(groupName: String, url: Option[String], fileId: Option[String],
                           memberNames: Option[String], clearImage: Boolean, isRemoved: Boolean)

  val newGroupForm = mapping(
    "groupName"   -> trim(label("Group name"   ,text(required, maxlength(100), identifier, uniqueUserName))),
    "url"         -> trim(label("URL"          ,optional(text(maxlength(200))))),
    "fileId"      -> trim(label("File ID"      ,optional(text()))),
    "memberNames" -> trim(label("Member Names" ,optional(text())))
  )(NewGroupForm.apply)

  val editGroupForm = mapping(
    "groupName"   -> trim(label("Group name"   ,text(required, maxlength(100), identifier))),
    "url"         -> trim(label("URL"          ,optional(text(maxlength(200))))),
    "fileId"      -> trim(label("File ID"      ,optional(text()))),
    "memberNames" -> trim(label("Member Names" ,optional(text()))),
    "clearImage"  -> trim(label("Clear image"  ,boolean())),
    "removed"     -> trim(label("Disable"      ,boolean()))
  )(EditGroupForm.apply)

  /**
   * Show the new repository form.
   */
  get("/new")(usersOnly {
    html.newrepo(getGroupsByUserName(context.loginAccount.get.userName))
  })
  
  /**
   * Create new repository.
   */
  post("/new", newRepositoryForm)(usersOnly { form =>
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
          using(Git.open(gitdir)){ git =>
            val builder  = DirCache.newInCore.builder()
            val inserter = git.getRepository.newObjectInserter()
            val headId   = git.getRepository.resolve(Constants.HEAD + "^{commit}")
            val content  = if(form.description.nonEmpty){
              form.name + "\n" +
              "===============\n" +
              "\n" +
              form.description.get
            } else {
              form.name + "\n" +
              "===============\n"
            }

            builder.add(JGitUtil.createDirCacheEntry("README.md", FileMode.REGULAR_FILE,
              inserter.insert(Constants.OBJ_BLOB, content.getBytes("UTF-8"))))
            builder.finish()

            JGitUtil.createNewCommit(git, inserter, headId, builder.getDirCache.writeTree(inserter),
              loginAccount.fullName, loginAccount.mailAddress, "Initial commit")
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
      if(repository.owner == loginUserName){
        // redirect to the repository
        redirect(s"/${repository.owner}/${repository.name}")
      } else {
        getForkedRepositories(repository.owner, repository.name).find(_._1 == loginUserName).map { case (owner, name) =>
          // redirect to the repository
          redirect(s"/${owner}/${name}")
        } getOrElse {
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
          // redirect to the repository
          redirect(s"/${loginUserName}/${repository.name}")
        }
      }
    }
  })

  get("/groups/new")(usersOnly {
    html.group(None, Nil)
  })

  post("/groups/new", newGroupForm)(usersOnly { form =>
    createGroup(form.groupName, form.url)
    updateGroupMembers(form.groupName, form.memberNames.map(_.split(",").toList).getOrElse(Nil))
    updateImage(form.groupName, form.fileId, false)
    redirect(s"/${form.groupName}")
  })

  get("/:groupName/_edit")(usersOnly { // TODO group manager only
    defining(params("groupName")){ groupName =>
      html.group(getAccountByUserName(groupName, true), getGroupMembers(groupName))
    }
  })

  post("/:groupName/_edit", editGroupForm)(usersOnly { form => // TODO group manager only
    defining(params("groupName"), form.memberNames.map(_.split(",").toList).getOrElse(Nil)){ case (groupName, memberNames) =>
      getAccountByUserName(groupName, true).map { account =>
        updateGroup(groupName, form.url, form.isRemoved)

        if(form.isRemoved){
          // Remove from GROUP_MEMBER
          updateGroupMembers(form.groupName, Nil)
          // Remove repositories
          getRepositoryNamesOfUser(form.groupName).foreach { repositoryName =>
            deleteRepository(groupName, repositoryName)
            FileUtils.deleteDirectory(getRepositoryDir(groupName, repositoryName))
            FileUtils.deleteDirectory(getWikiRepositoryDir(groupName, repositoryName))
            FileUtils.deleteDirectory(getTemporaryDir(groupName, repositoryName))
          }
        } else {
          // Update GROUP_MEMBER
          updateGroupMembers(form.groupName, memberNames)
          // Update COLLABORATOR for group repositories
          getRepositoryNamesOfUser(form.groupName).foreach { repositoryName =>
            removeCollaborators(form.groupName, repositoryName)
            memberNames.foreach { userName =>
              addCollaborator(form.groupName, repositoryName, userName)
            }
          }
        }

        updateImage(form.groupName, form.fileId, form.clearImage)
        redirect(s"/${form.groupName}")

      } getOrElse NotFound
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
    override def validate(name: String, value: String, messages: Messages): Option[String] =
      if(getAccountByUserName(value).isEmpty) Some("User or group does not exist.") else None
  }

  private def uniqueRepository: Constraint = new Constraint(){
    override def validate(name: String, value: String, params: Map[String, String], messages: Messages): Option[String] =
      params.get("owner").flatMap { userName =>
        getRepositoryNamesOfUser(userName).find(_ == value).map(_ => "Repository already exists.")
      }
  }

}
