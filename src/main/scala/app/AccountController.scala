package app

import service._
import util._
import util.StringUtil._
import util.Directory._
import util.ControlUtil._
import util.Implicits._
import ssh.SshUtil
import jp.sf.amateras.scalatra.forms._
import org.apache.commons.io.FileUtils
import org.scalatra.i18n.Messages
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.{FileMode, Constants}
import org.eclipse.jgit.dircache.DirCache
import model.GroupMember

class AccountController extends AccountControllerBase
  with AccountService with RepositoryService with ActivityService with WikiService with LabelsService with SshKeyService
  with OneselfAuthenticator with UsersAuthenticator with GroupManagerAuthenticator with ReadableUsersAuthenticator

trait AccountControllerBase extends AccountManagementControllerBase {
  self: AccountService with RepositoryService with ActivityService with WikiService with LabelsService with SshKeyService
    with OneselfAuthenticator with UsersAuthenticator with GroupManagerAuthenticator with ReadableUsersAuthenticator =>

  case class AccountNewForm(userName: String, password: String, fullName: String, mailAddress: String,
                            url: Option[String], fileId: Option[String])

  case class AccountEditForm(password: Option[String], fullName: String, mailAddress: String,
                             url: Option[String], fileId: Option[String], clearImage: Boolean)

  case class SshKeyForm(title: String, publicKey: String)

  val newForm = mapping(
    "userName"    -> trim(label("User name"    , text(required, maxlength(100), identifier, uniqueUserName))),
    "password"    -> trim(label("Password"     , text(required, maxlength(20)))),
    "fullName"    -> trim(label("Full Name"    , text(required, maxlength(100)))),
    "mailAddress" -> trim(label("Mail Address" , text(required, maxlength(100), uniqueMailAddress()))),
    "url"         -> trim(label("URL"          , optional(text(maxlength(200))))),
    "fileId"      -> trim(label("File ID"      , optional(text())))
  )(AccountNewForm.apply)

  val editForm = mapping(
    "password"    -> trim(label("Password"     , optional(text(maxlength(20))))),
    "fullName"    -> trim(label("Full Name"    , text(required, maxlength(100)))),
    "mailAddress" -> trim(label("Mail Address" , text(required, maxlength(100), uniqueMailAddress("userName")))),
    "url"         -> trim(label("URL"          , optional(text(maxlength(200))))),
    "fileId"      -> trim(label("File ID"      , optional(text()))),
    "clearImage"  -> trim(label("Clear image"  , boolean()))
  )(AccountEditForm.apply)

  val sshKeyForm = mapping(
    "title"     -> trim(label("Title", text(required, maxlength(100)))),
    "publicKey" -> trim(label("Key"  , text(required, validPublicKey)))
  )(SshKeyForm.apply)

  case class NewGroupForm(groupName: String, url: Option[String], fileId: Option[String], members: String)
  case class EditGroupForm(groupName: String, url: Option[String], fileId: Option[String], members: String, clearImage: Boolean)

  val newGroupForm = mapping(
    "groupName" -> trim(label("Group name" ,text(required, maxlength(100), identifier, uniqueUserName))),
    "url"       -> trim(label("URL"        ,optional(text(maxlength(200))))),
    "fileId"    -> trim(label("File ID"    ,optional(text()))),
    "members"   -> trim(label("Members"    ,text(required, members)))
  )(NewGroupForm.apply)

  val editGroupForm = mapping(
    "groupName"  -> trim(label("Group name"  ,text(required, maxlength(100), identifier))),
    "url"        -> trim(label("URL"         ,optional(text(maxlength(200))))),
    "fileId"     -> trim(label("File ID"     ,optional(text()))),
    "members"    -> trim(label("Members"     ,text(required, members))),
    "clearImage" -> trim(label("Clear image" ,boolean()))
  )(EditGroupForm.apply)

  case class RepositoryCreationForm(owner: String, name: String, description: Option[String], isPrivate: Boolean, createReadme: Boolean)
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

  /**
   * Displays user information.
   */
  get("/:userName") {
    val userName = params("userName")
    getAccountByUserName(userName).map { account =>
      params.getOrElse("tab", "repositories") match {
        // Public Activity
        case "activity" =>
          _root_.account.html.activity(account,
            if(account.isGroupAccount) Nil else getGroupsByUserName(userName),
            getActivitiesByUser(userName, true))

        // Members
        case "members" if(account.isGroupAccount) => {
          val members = getGroupMembers(account.userName)
          _root_.account.html.members(account, members.map(_.userName),
            context.loginAccount.exists(x => members.exists { member => member.userName == x.userName && member.isManager }))
        }

        // Repositories
        case _ => {
          val members = getGroupMembers(account.userName)
          _root_.account.html.repositories(account,
            if(account.isGroupAccount) Nil else getGroupsByUserName(userName),
            getVisibleRepositories(context.loginAccount, context.baseUrl, Some(userName)),
            context.loginAccount.exists(x => members.exists { member => member.userName == x.userName && member.isManager }))
        }
      }
    } getOrElse NotFound
  }

  get("/:userName.atom") {
    val userName = params("userName")
    contentType = "application/atom+xml; type=feed"
    helper.xml.feed(getActivitiesByUser(userName, true))
  }

  get("/:userName/_avatar"){
    val userName = params("userName")
    getAccountByUserName(userName).flatMap(_.image).map { image =>
      contentType = FileUtil.getMimeType(image)
      new java.io.File(getUserUploadDir(userName), image)
    } getOrElse {
      contentType = "image/png"
      Thread.currentThread.getContextClassLoader.getResourceAsStream("noimage.png")
    }
  }

  get("/:userName/_edit")(oneselfOnly {
    val userName = params("userName")
    getAccountByUserName(userName).map { x =>
      account.html.edit(x, flash.get("info"))
    } getOrElse NotFound
  })

  post("/:userName/_edit", editForm)(oneselfOnly { form =>
    val userName = params("userName")
    getAccountByUserName(userName).map { account =>
      updateAccount(account.copy(
        password    = form.password.map(sha1).getOrElse(account.password),
        fullName    = form.fullName,
        mailAddress = form.mailAddress,
        url         = form.url))

      updateImage(userName, form.fileId, form.clearImage)
      flash += "info" -> "Account information has been updated."
      redirect(s"/${userName}/_edit")

    } getOrElse NotFound
  })

  get("/:userName/_delete")(oneselfOnly {
    val userName = params("userName")

    getAccountByUserName(userName, true).foreach { account =>
      // Remove repositories
      getRepositoryNamesOfUser(userName).foreach { repositoryName =>
        deleteRepository(userName, repositoryName)
        FileUtils.deleteDirectory(getRepositoryDir(userName, repositoryName))
        FileUtils.deleteDirectory(getWikiRepositoryDir(userName, repositoryName))
        FileUtils.deleteDirectory(getTemporaryDir(userName, repositoryName))
      }
      // Remove from GROUP_MEMBER, COLLABORATOR and REPOSITORY
      removeUserRelatedData(userName)

      updateAccount(account.copy(isRemoved = true))
    }

    session.invalidate
    redirect("/")
  })

  get("/:userName/_ssh")(oneselfOnly {
    val userName = params("userName")
    getAccountByUserName(userName).map { x =>
      account.html.ssh(x, getPublicKeys(x.userName))
    } getOrElse NotFound
  })

  post("/:userName/_ssh", sshKeyForm)(oneselfOnly { form =>
    val userName = params("userName")
    addPublicKey(userName, form.title, form.publicKey)
    redirect(s"/${userName}/_ssh")
  })

  get("/:userName/_ssh/delete/:id")(oneselfOnly {
    val userName = params("userName")
    val sshKeyId = params("id").toInt
    deletePublicKey(userName, sshKeyId)
    redirect(s"/${userName}/_ssh")
  })

  get("/register"){
    if(context.settings.allowAccountRegistration){
      if(context.loginAccount.isDefined){
        redirect("/")
      } else {
        account.html.register()
      }
    } else NotFound
  }

  post("/register", newForm){ form =>
    if(context.settings.allowAccountRegistration){
      createAccount(form.userName, sha1(form.password), form.fullName, form.mailAddress, false, form.url)
      updateImage(form.userName, form.fileId, false)
      redirect("/signin")
    } else NotFound
  }

  get("/groups/new")(usersOnly {
    account.html.group(None, List(GroupMember("", context.loginAccount.get.userName, true)))
  })

  post("/groups/new", newGroupForm)(usersOnly { form =>
    createGroup(form.groupName, form.url)
    updateGroupMembers(form.groupName, form.members.split(",").map {
      _.split(":") match {
        case Array(userName, isManager) => (userName, isManager.toBoolean)
      }
    }.toList)
    updateImage(form.groupName, form.fileId, false)
    redirect(s"/${form.groupName}")
  })

  get("/:groupName/_editgroup")(managersOnly {
    defining(params("groupName")){ groupName =>
      account.html.group(getAccountByUserName(groupName, true), getGroupMembers(groupName))
    }
  })

  get("/:groupName/_deletegroup")(managersOnly {
    defining(params("groupName")){ groupName =>
      // Remove from GROUP_MEMBER
      updateGroupMembers(groupName, Nil)
      // Remove repositories
      getRepositoryNamesOfUser(groupName).foreach { repositoryName =>
        deleteRepository(groupName, repositoryName)
        FileUtils.deleteDirectory(getRepositoryDir(groupName, repositoryName))
        FileUtils.deleteDirectory(getWikiRepositoryDir(groupName, repositoryName))
        FileUtils.deleteDirectory(getTemporaryDir(groupName, repositoryName))
      }
    }
    redirect("/")
  })

  post("/:groupName/_editgroup", editGroupForm)(managersOnly { form =>
    defining(params("groupName"), form.members.split(",").map {
      _.split(":") match {
        case Array(userName, isManager) => (userName, isManager.toBoolean)
      }
    }.toList){ case (groupName, members) =>
      getAccountByUserName(groupName, true).map { account =>
        updateGroup(groupName, form.url, false)

        // Update GROUP_MEMBER
        updateGroupMembers(form.groupName, members)
        // Update COLLABORATOR for group repositories
        getRepositoryNamesOfUser(form.groupName).foreach { repositoryName =>
          removeCollaborators(form.groupName, repositoryName)
          members.foreach { case (userName, isManager) =>
            addCollaborator(form.groupName, repositoryName, userName)
          }
        }

        updateImage(form.groupName, form.fileId, form.clearImage)
        redirect(s"/${form.groupName}")

      } getOrElse NotFound
    }
  })

  /**
   * Show the new repository form.
   */
  get("/new")(usersOnly {
    account.html.newrepo(getGroupsByUserName(context.loginAccount.get.userName))
  })

  /**
   * Create new repository.
   */
  post("/new", newRepositoryForm)(usersOnly { form =>
    LockUtil.lock(s"${form.owner}/${form.name}"){
      if(getRepository(form.owner, form.name, context.baseUrl).isEmpty){
        val ownerAccount  = getAccountByUserName(form.owner).get
        val loginAccount  = context.loginAccount.get
        val loginUserName = loginAccount.userName

        // Insert to the database at first
        createRepository(form.name, form.owner, form.description, form.isPrivate)

        // Add collaborators for group repository
        if(ownerAccount.isGroupAccount){
          getGroupMembers(form.owner).foreach { member =>
            addCollaborator(form.owner, form.name, member.userName)
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
              Constants.HEAD, loginAccount.fullName, loginAccount.mailAddress, "Initial commit")
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

    LockUtil.lock(s"${loginUserName}/${repository.name}"){
      if(repository.owner == loginUserName || getRepository(loginAccount.userName, repository.name, baseUrl).isDefined){
        // redirect to the repository if repository already exists
        redirect(s"/${loginUserName}/${repository.name}")
      } else {
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

        // Record activity
        recordForkActivity(repository.owner, repository.name, loginUserName)
        // redirect to the repository
        redirect(s"/${loginUserName}/${repository.name}")
      }
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

  private def members: Constraint = new Constraint(){
    override def validate(name: String, value: String, messages: Messages): Option[String] = {
      if(value.split(",").exists {
        _.split(":") match { case Array(userName, isManager) => isManager.toBoolean }
      }) None else Some("Must select one manager at least.")
    }
  }

  private def validPublicKey: Constraint = new Constraint(){
    override def validate(name: String, value: String, messages: Messages): Option[String] = SshUtil.str2PublicKey(value) match {
     case Some(_) => None
     case None => Some("Key is invalid.")
    }
  }
}
