package gitbucket.core.controller

import gitbucket.core.account.html
import gitbucket.core.helper
import gitbucket.core.model.{GroupMember, Role}
import gitbucket.core.plugin.PluginRegistry
import gitbucket.core.service._
import gitbucket.core.ssh.SshUtil
import gitbucket.core.util.SyntaxSugars._
import gitbucket.core.util.Directory._
import gitbucket.core.util.Implicits._
import gitbucket.core.util.StringUtil._
import gitbucket.core.util._
import io.github.gitbucket.scalatra.forms._
import org.apache.commons.io.FileUtils
import org.scalatra.i18n.Messages
import org.scalatra.BadRequest


class AccountController extends AccountControllerBase
  with AccountService with RepositoryService with ActivityService with WikiService with LabelsService with SshKeyService
  with OneselfAuthenticator with UsersAuthenticator with GroupManagerAuthenticator with ReadableUsersAuthenticator
  with AccessTokenService with WebHookService with RepositoryCreationService


trait AccountControllerBase extends AccountManagementControllerBase {
  self: AccountService with RepositoryService with ActivityService with WikiService with LabelsService with SshKeyService
    with OneselfAuthenticator with UsersAuthenticator with GroupManagerAuthenticator with ReadableUsersAuthenticator
    with AccessTokenService with WebHookService with RepositoryCreationService =>

  case class AccountNewForm(userName: String, password: String, fullName: String, mailAddress: String,
                            description: Option[String], url: Option[String], fileId: Option[String])

  case class AccountEditForm(password: Option[String], fullName: String, mailAddress: String,
                             description: Option[String], url: Option[String], fileId: Option[String], clearImage: Boolean)

  case class SshKeyForm(title: String, publicKey: String)

  case class PersonalTokenForm(note: String)

  val newForm = mapping(
    "userName"    -> trim(label("User name"    , text(required, maxlength(100), identifier, uniqueUserName, reservedNames))),
    "password"    -> trim(label("Password"     , text(required, maxlength(20), password))),
    "fullName"    -> trim(label("Full Name"    , text(required, maxlength(100)))),
    "mailAddress" -> trim(label("Mail Address" , text(required, maxlength(100), uniqueMailAddress()))),
    "description" -> trim(label("bio"          , optional(text()))),
    "url"         -> trim(label("URL"          , optional(text(maxlength(200))))),
    "fileId"      -> trim(label("File ID"      , optional(text())))
  )(AccountNewForm.apply)

  val editForm = mapping(
    "password"    -> trim(label("Password"     , optional(text(maxlength(20), password)))),
    "fullName"    -> trim(label("Full Name"    , text(required, maxlength(100)))),
    "mailAddress" -> trim(label("Mail Address" , text(required, maxlength(100), uniqueMailAddress("userName")))),
    "description" -> trim(label("bio"          , optional(text()))),
    "url"         -> trim(label("URL"          , optional(text(maxlength(200))))),
    "fileId"      -> trim(label("File ID"      , optional(text()))),
    "clearImage"  -> trim(label("Clear image"  , boolean()))
  )(AccountEditForm.apply)

  val sshKeyForm = mapping(
    "title"     -> trim(label("Title", text(required, maxlength(100)))),
    "publicKey" -> trim2(label("Key" , text(required, validPublicKey)))
  )(SshKeyForm.apply)

  val personalTokenForm = mapping(
    "note" -> trim(label("Token", text(required, maxlength(100))))
  )(PersonalTokenForm.apply)

  case class NewGroupForm(groupName: String, description: Option[String], url: Option[String], fileId: Option[String], members: String)
  case class EditGroupForm(groupName: String, description: Option[String], url: Option[String], fileId: Option[String], members: String, clearImage: Boolean)

  val newGroupForm = mapping(
    "groupName"   -> trim(label("Group name" ,text(required, maxlength(100), identifier, uniqueUserName, reservedNames))),
    "description" -> trim(label("Group description", optional(text()))),
    "url"         -> trim(label("URL"        ,optional(text(maxlength(200))))),
    "fileId"      -> trim(label("File ID"    ,optional(text()))),
    "members"     -> trim(label("Members"    ,text(required, members)))
  )(NewGroupForm.apply)

  val editGroupForm = mapping(
    "groupName"   -> trim(label("Group name"  ,text(required, maxlength(100), identifier))),
    "description" -> trim(label("Group description", optional(text()))),
    "url"         -> trim(label("URL"         ,optional(text(maxlength(200))))),
    "fileId"      -> trim(label("File ID"     ,optional(text()))),
    "members"     -> trim(label("Members"     ,text(required, members))),
    "clearImage"  -> trim(label("Clear image" ,boolean()))
  )(EditGroupForm.apply)

  case class RepositoryCreationForm(owner: String, name: String, description: Option[String], isPrivate: Boolean, createReadme: Boolean)
  case class ForkRepositoryForm(owner: String, name: String)

  val newRepositoryForm = mapping(
    "owner"        -> trim(label("Owner"          , text(required, maxlength(100), identifier, existsAccount))),
    "name"         -> trim(label("Repository name", text(required, maxlength(100), repository, uniqueRepository))),
    "description"  -> trim(label("Description"    , optional(text()))),
    "isPrivate"    -> trim(label("Repository Type", boolean())),
    "createReadme" -> trim(label("Create README"  , boolean()))
  )(RepositoryCreationForm.apply)

  val forkRepositoryForm = mapping(
    "owner" -> trim(label("Repository owner", text(required))),
    "name"  -> trim(label("Repository name",  text(required)))
  )(ForkRepositoryForm.apply)

  case class AccountForm(accountName: String)

  val accountForm = mapping(
    "account" -> trim(label("Group/User name", text(required, validAccountName)))
  )(AccountForm.apply)

  /**
   * Displays user information.
   */
  get("/:userName") {
    val userName = params("userName")
    getAccountByUserName(userName).map { account =>
      params.getOrElse("tab", "repositories") match {
        // Public Activity
        case "activity" =>
          gitbucket.core.account.html.activity(account,
            if(account.isGroupAccount) Nil else getGroupsByUserName(userName),
            getActivitiesByUser(userName, true))

        // Members
        case "members" if(account.isGroupAccount) => {
          val members = getGroupMembers(account.userName)
          gitbucket.core.account.html.members(account, members,
            context.loginAccount.exists(x => members.exists { member => member.userName == x.userName && member.isManager }))
        }

        // Repositories
        case _ => {
          val members = getGroupMembers(account.userName)
          gitbucket.core.account.html.repositories(account,
            if(account.isGroupAccount) Nil else getGroupsByUserName(userName),
            getVisibleRepositories(context.loginAccount, Some(userName)),
            context.loginAccount.exists(x => members.exists { member => member.userName == x.userName && member.isManager }))
        }
      }
    } getOrElse NotFound()
  }

  get("/:userName.atom") {
    val userName = params("userName")
    contentType = "application/atom+xml; type=feed"
    helper.xml.feed(getActivitiesByUser(userName, true))
  }

  get("/:userName/_avatar"){
    val userName = params("userName")
    contentType = "image/png"
    getAccountByUserName(userName).flatMap{ account =>
      response.setDateHeader("Last-Modified", account.updatedDate.getTime)
      account.image.map{ image =>
        Some(RawData(FileUtil.getMimeType(image), new java.io.File(getUserUploadDir(userName), image)))
      }.getOrElse{
        if (account.isGroupAccount) {
          TextAvatarUtil.textGroupAvatar(account.fullName)
        } else {
          TextAvatarUtil.textAvatar(account.fullName)
        }
      }
    }.getOrElse{
      response.setHeader("Cache-Control", "max-age=3600")
      Thread.currentThread.getContextClassLoader.getResourceAsStream("noimage.png")
    }
  }

  get("/:userName/_edit")(oneselfOnly {
    val userName = params("userName")
    getAccountByUserName(userName).map { x =>
      html.edit(x, flash.get("info"), flash.get("error"))
    } getOrElse NotFound()
  })

  post("/:userName/_edit", editForm)(oneselfOnly { form =>
    val userName = params("userName")
    getAccountByUserName(userName).map { account =>
      updateAccount(account.copy(
        password    = form.password.map(sha1).getOrElse(account.password),
        fullName    = form.fullName,
        mailAddress = form.mailAddress,
        description = form.description,
        url         = form.url))

      updateImage(userName, form.fileId, form.clearImage)
      flash += "info" -> "Account information has been updated."
      redirect(s"/${userName}/_edit")

    } getOrElse NotFound()
  })

  get("/:userName/_delete")(oneselfOnly {
    val userName = params("userName")

    getAccountByUserName(userName, true).map { account =>
      if(isLastAdministrator(account)){
        flash += "error" -> "Account can't be removed because this is last one administrator."
        redirect(s"/${userName}/_edit")
      } else {
//      // Remove repositories
//      getRepositoryNamesOfUser(userName).foreach { repositoryName =>
//        deleteRepository(userName, repositoryName)
//        FileUtils.deleteDirectory(getRepositoryDir(userName, repositoryName))
//        FileUtils.deleteDirectory(getWikiRepositoryDir(userName, repositoryName))
//        FileUtils.deleteDirectory(getTemporaryDir(userName, repositoryName))
//      }
        // Remove from GROUP_MEMBER and COLLABORATOR
        removeUserRelatedData(userName)
        updateAccount(account.copy(isRemoved = true))

        // call hooks
        PluginRegistry().getAccountHooks.foreach(_.deleted(userName))

        session.invalidate
        redirect("/")
      }
    } getOrElse NotFound()
  })

  get("/:userName/_ssh")(oneselfOnly {
    val userName = params("userName")
    getAccountByUserName(userName).map { x =>
      html.ssh(x, getPublicKeys(x.userName))
    } getOrElse NotFound()
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

  get("/:userName/_application")(oneselfOnly {
    val userName = params("userName")
    getAccountByUserName(userName).map { x =>
      var tokens = getAccessTokens(x.userName)
      val generatedToken = flash.get("generatedToken") match {
        case Some((tokenId:Int, token:String)) => {
          val gt = tokens.find(_.accessTokenId == tokenId)
          gt.map{ t =>
            tokens = tokens.filterNot(_ == t)
            (t, token)
          }
        }
        case _ => None
      }
      html.application(x, tokens, generatedToken)
    } getOrElse NotFound()
  })

  post("/:userName/_personalToken", personalTokenForm)(oneselfOnly { form =>
    val userName = params("userName")
    getAccountByUserName(userName).map { x =>
      val (tokenId, token) = generateAccessToken(userName, form.note)
      flash += "generatedToken" -> (tokenId, token)
    }
    redirect(s"/${userName}/_application")
  })

  get("/:userName/_personalToken/delete/:id")(oneselfOnly {
    val userName = params("userName")
    val tokenId = params("id").toInt
    deleteAccessToken(userName, tokenId)
    redirect(s"/${userName}/_application")
  })

  get("/register"){
    if(context.settings.allowAccountRegistration){
      if(context.loginAccount.isDefined){
        redirect("/")
      } else {
        html.register()
      }
    } else NotFound()
  }

  post("/register", newForm){ form =>
    if(context.settings.allowAccountRegistration){
      createAccount(form.userName, sha1(form.password), form.fullName, form.mailAddress, false, form.description, form.url)
      updateImage(form.userName, form.fileId, false)
      redirect("/signin")
    } else NotFound()
  }

  get("/groups/new")(usersOnly {
    html.group(None, List(GroupMember("", context.loginAccount.get.userName, true)))
  })

  post("/groups/new", newGroupForm)(usersOnly { form =>
    createGroup(form.groupName, form.description, form.url)
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
      html.group(getAccountByUserName(groupName, true), getGroupMembers(groupName))
    }
  })

  get("/:groupName/_deletegroup")(managersOnly {
    defining(params("groupName")){ groupName =>
      // Remove from GROUP_MEMBER
      updateGroupMembers(groupName, Nil)
      // Disable group
      getAccountByUserName(groupName, false).foreach { account =>
        updateGroup(groupName, account.description, account.url, true)
      }
//      // Remove repositories
//      getRepositoryNamesOfUser(groupName).foreach { repositoryName =>
//        deleteRepository(groupName, repositoryName)
//        FileUtils.deleteDirectory(getRepositoryDir(groupName, repositoryName))
//        FileUtils.deleteDirectory(getWikiRepositoryDir(groupName, repositoryName))
//        FileUtils.deleteDirectory(getTemporaryDir(groupName, repositoryName))
//      }
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
        updateGroup(groupName, form.description, form.url, false)

        // Update GROUP_MEMBER
        updateGroupMembers(form.groupName, members)
//        // Update COLLABORATOR for group repositories
//        getRepositoryNamesOfUser(form.groupName).foreach { repositoryName =>
//          removeCollaborators(form.groupName, repositoryName)
//          members.foreach { case (userName, isManager) =>
//            addCollaborator(form.groupName, repositoryName, userName)
//          }
//        }

        updateImage(form.groupName, form.fileId, form.clearImage)
        redirect(s"/${form.groupName}")

      } getOrElse NotFound()
    }
  })

  /**
   * Show the new repository form.
   */
  get("/new")(usersOnly {
    html.newrepo(getGroupsByUserName(context.loginAccount.get.userName), context.settings.isCreateRepoOptionPublic)
  })

  /**
   * Create new repository.
   */
  post("/new", newRepositoryForm)(usersOnly { form =>
    LockUtil.lock(s"${form.owner}/${form.name}"){
      if(getRepository(form.owner, form.name).isEmpty){
        // Create the repository
        createRepository(context.loginAccount.get, form.owner, form.name, form.description, form.isPrivate, form.createReadme)

        // Call hooks
        PluginRegistry().getRepositoryHooks.foreach(_.created(form.owner, form.name))
      }
    }

    // redirect to the repository
    redirect(s"/${form.owner}/${form.name}")
  })

  get("/:owner/:repository/fork")(readableUsersOnly { repository =>
    if(repository.repository.options.allowFork){
      val loginAccount   = context.loginAccount.get
      val loginUserName  = loginAccount.userName
      val groups         = getGroupsByUserName(loginUserName)
      groups match {
        case _: List[String] =>
          val managerPermissions = groups.map { group =>
            val members = getGroupMembers(group)
            context.loginAccount.exists(x => members.exists { member => member.userName == x.userName && member.isManager })
          }
          helper.html.forkrepository(
            repository,
            (groups zip managerPermissions).toMap
          )
        case _ => redirect(s"/${loginUserName}")
      }
    } else BadRequest()
  })

  post("/:owner/:repository/fork", accountForm)(readableUsersOnly { (form, repository) =>
    if(repository.repository.options.allowFork){
      val loginAccount  = context.loginAccount.get
      val loginUserName = loginAccount.userName
      val accountName   = form.accountName

      LockUtil.lock(s"${accountName}/${repository.name}"){
        if(getRepository(accountName, repository.name).isDefined ||
            (accountName != loginUserName && !getGroupsByUserName(loginUserName).contains(accountName))){
          // redirect to the repository if repository already exists
          redirect(s"/${accountName}/${repository.name}")
        } else {
          // Insert to the database at first
          val originUserName = repository.repository.originUserName.getOrElse(repository.owner)
          val originRepositoryName = repository.repository.originRepositoryName.getOrElse(repository.name)

          insertRepository(
            repositoryName       = repository.name,
            userName             = accountName,
            description          = repository.repository.description,
            isPrivate            = repository.repository.isPrivate,
            originRepositoryName = Some(originRepositoryName),
            originUserName       = Some(originUserName),
            parentRepositoryName = Some(repository.name),
            parentUserName       = Some(repository.owner)
          )

          // Set default collaborators for the private fork
          if(repository.repository.isPrivate){
            // Copy collaborators from the source repository
            getCollaborators(repository.owner, repository.name).foreach { case (collaborator, _) =>
              addCollaborator(accountName, repository.name, collaborator.collaboratorName, collaborator.role)
            }
            // Register an owner of the source repository as a collaborator
            addCollaborator(accountName, repository.name, repository.owner, Role.ADMIN.name)
          }

          // Insert default labels
          insertDefaultLabels(accountName, repository.name)

          // clone repository actually
          JGitUtil.cloneRepository(
            getRepositoryDir(repository.owner, repository.name),
            getRepositoryDir(accountName, repository.name))

          // Create Wiki repository
          JGitUtil.cloneRepository(
            getWikiRepositoryDir(repository.owner, repository.name),
            getWikiRepositoryDir(accountName, repository.name))

          // Record activity
          recordForkActivity(repository.owner, repository.name, loginUserName, accountName)

          // Call hooks
          PluginRegistry().getRepositoryHooks.foreach(_.forked(repository.owner, accountName, repository.name))

          // redirect to the repository
          redirect(s"/${accountName}/${repository.name}")
        }
      }
    } else BadRequest()
  })

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
     case Some(_) if !getAllKeys().exists(_.publicKey == value) => None
     case _ => Some("Key is invalid.")
    }
  }

  private def validAccountName: Constraint = new Constraint(){
    override def validate(name: String, value: String, messages: Messages): Option[String] = {
      getAccountByUserName(value) match {
        case Some(_) => None
        case None => Some("Invalid Group/User Account.")
      }
    }
  }
}
