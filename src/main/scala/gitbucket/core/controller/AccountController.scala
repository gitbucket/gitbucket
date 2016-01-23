package gitbucket.core.controller

import gitbucket.core.account.html
import gitbucket.core.api._
import gitbucket.core.helper
import gitbucket.core.model.GroupMember
import gitbucket.core.service._
import gitbucket.core.ssh.SshUtil
import gitbucket.core.util.ControlUtil._
import gitbucket.core.util.Directory._
import gitbucket.core.util.Implicits._
import gitbucket.core.util.StringUtil._
import gitbucket.core.util._

import io.github.gitbucket.scalatra.forms._
import org.apache.commons.io.FileUtils
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.dircache.DirCache
import org.eclipse.jgit.lib.{FileMode, Constants}
import org.scalatra.i18n.Messages


class AccountController extends AccountControllerBase
  with AccountService with RepositoryService with ActivityService with WikiService with LabelsService with SshKeyService
  with OneselfAuthenticator with UsersAuthenticator with GroupManagerAuthenticator with ReadableUsersAuthenticator
  with AccessTokenService with WebHookService with AdminAuthenticator


trait AccountControllerBase extends AccountManagementControllerBase {
  self: AccountService with RepositoryService with ActivityService with WikiService with LabelsService with SshKeyService
    with OneselfAuthenticator with UsersAuthenticator with GroupManagerAuthenticator with ReadableUsersAuthenticator
    with AdminAuthenticator
    with AccessTokenService with WebHookService =>

  case class AccountNewForm(userName: String, password: String, fullName: String, mailAddress: String,
                            url: Option[String], fileId: Option[String])

  case class AccountEditForm(password: Option[String], fullName: String, mailAddress: String,
                             url: Option[String], fileId: Option[String], clearImage: Boolean)

  case class SshKeyForm(title: String, publicKey: String)

  case class PersonalTokenForm(note: String)

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

  val personalTokenForm = mapping(
    "note"     -> trim(label("Token", text(required, maxlength(100))))
  )(PersonalTokenForm.apply)

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
          gitbucket.core.account.html.members(account, members.map(_.userName),
            context.loginAccount.exists(x => members.exists { member => member.userName == x.userName && member.isManager }))
        }

        // Repositories
        case _ => {
          val members = getGroupMembers(account.userName)
          gitbucket.core.account.html.repositories(account,
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
      RawData(FileUtil.getMimeType(image), new java.io.File(getUserUploadDir(userName), image))
    } getOrElse {
      contentType = "image/png"
      Thread.currentThread.getContextClassLoader.getResourceAsStream("noimage.png")
    }
  }

  /**
   * https://developer.github.com/v3/users/#get-a-single-user
   */
  get("/api/v3/users/:userName") {
    getAccountByUserName(params("userName")).map { account =>
      JsonFormat(ApiUser(account))
    } getOrElse NotFound
  }

  /**
   * https://developer.github.com/v3/users/#get-the-authenticated-user
   */
  get("/api/v3/user") {
    context.loginAccount.map { account =>
      JsonFormat(ApiUser(account))
    } getOrElse Unauthorized
  }

  get("/:userName/_edit")(oneselfOnly {
    val userName = params("userName")
    getAccountByUserName(userName).map { x =>
      html.edit(x, flash.get("info"))
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
//      // Remove repositories
//      getRepositoryNamesOfUser(userName).foreach { repositoryName =>
//        deleteRepository(userName, repositoryName)
//        FileUtils.deleteDirectory(getRepositoryDir(userName, repositoryName))
//        FileUtils.deleteDirectory(getWikiRepositoryDir(userName, repositoryName))
//        FileUtils.deleteDirectory(getTemporaryDir(userName, repositoryName))
//      }
//      // Remove from GROUP_MEMBER, COLLABORATOR and REPOSITORY
//      removeUserRelatedData(userName)

      removeUserRelatedData(userName)
      updateAccount(account.copy(isRemoved = true))
    }

    session.invalidate
    redirect("/")
  })

  get("/:userName/_ssh")(oneselfOnly {
    val userName = params("userName")
    getAccountByUserName(userName).map { x =>
      html.ssh(x, getPublicKeys(x.userName))
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
    } getOrElse NotFound
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
    html.group(None, List(GroupMember("", context.loginAccount.get.userName, true)))
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
      html.group(getAccountByUserName(groupName, true), getGroupMembers(groupName))
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
    html.newrepo(getGroupsByUserName(context.loginAccount.get.userName), context.settings.isCreateRepoOptionPublic)
  })

  /**
   * Create new repository.
   */
  post("/new", newRepositoryForm)(usersOnly { form =>
    LockUtil.lock(s"${form.owner}/${form.name}"){
      if(getRepository(form.owner, form.name, context.baseUrl).isEmpty){
        createRepository(form.owner, form.name, form.description, form.isPrivate, form.createReadme)
      }

      // redirect to the repository
      redirect(s"/${form.owner}/${form.name}")
    }
  })

  /**
   * Create user repository
   * https://developer.github.com/v3/repos/#create
   */
  post("/api/v3/user/repos")(usersOnly {
    val owner = context.loginAccount.get.userName
    (for {
      data <- extractFromJsonBody[CreateARepository] if data.isValid
    } yield {
      LockUtil.lock(s"${owner}/${data.name}") {
        if(getRepository(owner, data.name, context.baseUrl).isEmpty){
          createRepository(owner, data.name, data.description, data.`private`, data.auto_init)
          val repository = getRepository(owner, data.name, context.baseUrl).get
          JsonFormat(ApiRepository(repository, ApiUser(getAccountByUserName(owner).get)))
        } else {
          ApiError(
            "A repository with this name already exists on this account", 
            Some("https://developer.github.com/v3/repos/#create")
          )
        }
      }
    }) getOrElse NotFound
  })

  /**
   * Create group repository
   * https://developer.github.com/v3/repos/#create
   */
  post("/api/v3/orgs/:org/repos")(managersOnly {
    val groupName = params("org")
    (for {
      data <- extractFromJsonBody[CreateARepository] if data.isValid
    } yield {
      LockUtil.lock(s"${groupName}/${data.name}") {
        if(getRepository(groupName, data.name, context.baseUrl).isEmpty){
          createRepository(groupName, data.name, data.description, data.`private`, data.auto_init)
          val repository = getRepository(groupName, data.name, context.baseUrl).get
          JsonFormat(ApiRepository(repository, ApiUser(getAccountByUserName(groupName).get)))
        } else {
          ApiError(
            "A repository with this name already exists for this group", 
            Some("https://developer.github.com/v3/repos/#create")
          )
        }
      }
    }) getOrElse NotFound
  })

  get("/:owner/:repository/fork")(readableUsersOnly { repository =>
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
  })

  post("/:owner/:repository/fork", accountForm)(readableUsersOnly { (form, repository) =>
    val loginAccount  = context.loginAccount.get
    val loginUserName = loginAccount.userName
    val accountName   = form.accountName

    LockUtil.lock(s"${accountName}/${repository.name}"){
      if(getRepository(accountName, repository.name, baseUrl).isDefined ||
          (accountName != loginUserName && !getGroupsByUserName(loginUserName).contains(accountName))){
        // redirect to the repository if repository already exists
        redirect(s"/${accountName}/${repository.name}")
      } else {
        // Insert to the database at first
        val originUserName = repository.repository.originUserName.getOrElse(repository.owner)
        val originRepositoryName = repository.repository.originRepositoryName.getOrElse(repository.name)

        createRepository(
          repositoryName       = repository.name,
          userName             = accountName,
          description          = repository.repository.description,
          isPrivate            = repository.repository.isPrivate,
          originRepositoryName = Some(originRepositoryName),
          originUserName       = Some(originUserName),
          parentRepositoryName = Some(repository.name),
          parentUserName       = Some(repository.owner)
        )

        // Add collaborators for group repository
        val ownerAccount = getAccountByUserName(accountName).get
        if(ownerAccount.isGroupAccount){
          getGroupMembers(accountName).foreach { member =>
            addCollaborator(accountName, repository.name, member.userName)
          }
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
        // redirect to the repository
        redirect(s"/${accountName}/${repository.name}")
      }
    }
  })

  /**
    * User api add on - user creation
    */
  post("/api/v3/user") (adminOnly {
    (
      for {
        data <- extractFromJsonBody[CreateAUser] if data.isValid
      } yield {
        if(context.settings.allowAccountRegistration){
          createAccount(data.userName, sha1(data.password), data.fullName, data.mailAddress, false, data.url)
          updateImage(data.userName, data.fileId, false)
          org.scalatra.Accepted(s"""{ "message": "${data.userName} is added"} """)
        }
        else org.scalatra.NotAcceptable("""{ "message": "user creation is not allowed" }""")

      }) getOrElse
      org.scalatra.NotAcceptable("""{ "message': "json body is not valid"}""")
  })

  delete("/api/v3/user/:userName") (adminOnly {
    val userName = params("userName")
    getAccountByUserName(userName, true).map { account =>
      removeUserRelatedData(userName)

      updateAccount(account.copy(
        password     = account.password,
        fullName     = account.fullName,
        mailAddress  = account.mailAddress,
        isAdmin      = false,
        url          = account.url,
        isRemoved    = true))

      updateImage(userName, None, true)

      org.scalatra.Accepted(s"""{ "message": "$userName is disabled" }""")
    } getOrElse NotFound
  })

  post("/api/v3/newgroup")(adminOnly {
    (for {
      data <- extractFromJsonBody[CreateAGroup] if data.isValid
    } yield {
      createGroup(data.groupName, data.url)
      updateGroupMembers(data.groupName,
        data.members.map ( m => (m, m.equalsIgnoreCase( data.manager ))))

      updateImage(data.groupName, data.fileId, false)
      org.scalatra.Accepted(s"""{"message": "${data.groupName} is added"}""")

    }) getOrElse
    org.scalatra.NotAcceptable("""{"message": "json body is not valid"}""")

  })

  delete("/api/v3/group/:groupName")(adminOnly{
    defining(params("groupName")) { groupName => {
      getAccountByUserName(groupName, true).map { account =>
        updateGroup(groupName, None, true)

        // Remove from GROUP_MEMBER
        updateGroupMembers(groupName, Nil)
        // Remove repositories
        getRepositoryNamesOfUser(groupName).foreach { repositoryName =>
          deleteRepository(groupName, repositoryName)
          FileUtils.deleteDirectory(getRepositoryDir(groupName, repositoryName))
          FileUtils.deleteDirectory(getWikiRepositoryDir(groupName, repositoryName))
          FileUtils.deleteDirectory(getTemporaryDir(groupName, repositoryName))
        }
        org.scalatra.Accepted( s"""{"message": "$groupName is disabled" }""")
      } getOrElse NotFound
    }}})

  post("/api/v3/:groupName/add/user/:userName") (adminOnly{
    defining(params("groupName"), params("userName")) { case (groupName, userName) =>
      val _oMembers = getGroupMembers(groupName).map{ member => (member.userName, member.isManager)}
      if(_oMembers.contains((userName, false)))
        org.scalatra.NotAcceptable(s"""{"message": "$userName is already in group $groupName" }""")
      else{
        val _nMembers = _oMembers:+(userName, false)
        updateGroupMembers(groupName, _nMembers)
        getRepositoryNamesOfUser(groupName).foreach { repositoryName =>
          removeCollaborators(groupName, repositoryName)
          _nMembers.foreach { case (userName, isManager) =>
            addCollaborator(groupName, repositoryName, userName)
          }
        }
        org.scalatra.Accepted(s"""{"message": "$userName added to group $groupName" }""")
      }
    }
  })

  post("/api/v3/:groupName/remove/user/:userName") (adminOnly{
    defining(params("groupName"), params("userName")) { case (groupName, userName) =>
      val _oMembers = getGroupMembers(groupName)
        .filter(member => !member.userName.equalsIgnoreCase(userName)).map{ member =>

          (member.userName, member.isManager)
      }

      updateGroupMembers(groupName, _oMembers)
      getRepositoryNamesOfUser(groupName).foreach { repositoryName =>
        removeCollaborators(groupName, repositoryName)
        _oMembers.foreach { case (userName, isManager) =>
          addCollaborator(groupName, repositoryName, userName)
        }
      }
      org.scalatra.Accepted(s"""{"message": "$userName removed from group $groupName" }""")

    }
  })


  private def createRepository(owner: String, name: String, description: Option[String], isPrivate: Boolean, createReadme: Boolean) {
    val ownerAccount  = getAccountByUserName(owner).get
    val loginAccount  = context.loginAccount.get
    val loginUserName = loginAccount.userName

    // Insert to the database at first
    createRepository(name, owner, description, isPrivate)

    // Add collaborators for group repository
    if(ownerAccount.isGroupAccount){
      getGroupMembers(owner).foreach { member =>
        addCollaborator(owner, name, member.userName)
      }
    }

    // Insert default labels
    insertDefaultLabels(owner, name)

    // Create the actual repository
    val gitdir = getRepositoryDir(owner, name)
    JGitUtil.initRepository(gitdir)

    if(createReadme){
      using(Git.open(gitdir)){ git =>
        val builder  = DirCache.newInCore.builder()
        val inserter = git.getRepository.newObjectInserter()
        val headId   = git.getRepository.resolve(Constants.HEAD + "^{commit}")
        val content  = if(description.nonEmpty){
          name + "\n" +
          "===============\n" +
          "\n" +
          description.get
        } else {
          name + "\n" +
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
    createWikiRepository(loginAccount, owner, name)

    // Record activity
    recordCreateRepositoryActivity(owner, name, loginUserName)
  }

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

  private def validAccountName: Constraint = new Constraint(){
    override def validate(name: String, value: String, messages: Messages): Option[String] = {
      getAccountByUserName(value) match {
        case Some(_) => None
        case None => Some("Invalid Group/User Account.")
      }
    }
  }
}
