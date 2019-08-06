package gitbucket.core.service

import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap

import gitbucket.core.model.Profile.profile.blockingApi._
import gitbucket.core.util.Directory._
import gitbucket.core.util.{FileUtil, JGitUtil, LockUtil}
import gitbucket.core.model.{Account, Role}
import gitbucket.core.plugin.PluginRegistry
import gitbucket.core.service.RepositoryService.RepositoryInfo
import gitbucket.core.servlet.Database
import org.apache.commons.io.FileUtils
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.dircache.DirCache
import org.eclipse.jgit.lib.{Constants, FileMode}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Using

object RepositoryCreationService {

  private val Creating = new ConcurrentHashMap[String, Option[String]]()

  def isCreating(owner: String, repository: String): Boolean = {
    Option(Creating.get(s"${owner}/${repository}")).map(_.isEmpty).getOrElse(false)
  }

  def startCreation(owner: String, repository: String): Unit = {
    Creating.put(s"${owner}/${repository}", None)
  }

  def endCreation(owner: String, repository: String, error: Option[String]): Unit = {
    error match {
      case None        => Creating.remove(s"${owner}/${repository}")
      case Some(error) => Creating.put(s"${owner}/${repository}", Some(error))
    }
  }

  def getCreationError(owner: String, repository: String): Option[String] = {
    Option(Creating.remove(s"${owner}/${repository}")).getOrElse(None)
  }

}

trait RepositoryCreationService {
  self: AccountService
    with RepositoryService
    with LabelsService
    with WikiService
    with ActivityService
    with PrioritiesService =>

  def createRepository(
    loginAccount: Account,
    owner: String,
    name: String,
    description: Option[String],
    isPrivate: Boolean,
    createReadme: Boolean
  ): Future[Unit] = {
    createRepository(loginAccount, owner, name, description, isPrivate, if (createReadme) "README" else "EMPTY", None)
  }

  def createRepository(
    loginAccount: Account,
    owner: String,
    name: String,
    description: Option[String],
    isPrivate: Boolean,
    initOption: String,
    sourceUrl: Option[String]
  ): Future[Unit] = Future {
    RepositoryCreationService.startCreation(owner, name)
    try {
      Database() withTransaction { implicit session =>
        val ownerAccount = getAccountByUserName(owner).get
        val loginUserName = loginAccount.userName

        val copyRepositoryDir = if (initOption == "COPY") {
          sourceUrl.flatMap { url =>
            val dir = Files.createTempDirectory(s"gitbucket-${owner}-${name}").toFile
            Git.cloneRepository().setBare(true).setURI(url).setDirectory(dir).setCloneAllBranches(true).call()
            Some(dir)
          }
        } else None

        // Insert to the database at first
        insertRepository(name, owner, description, isPrivate)

        //    // Add collaborators for group repository
        //    if(ownerAccount.isGroupAccount){
        //      getGroupMembers(owner).foreach { member =>
        //        addCollaborator(owner, name, member.userName)
        //      }
        //    }

        // Insert default labels
        insertDefaultLabels(owner, name)

        // Insert default priorities
        insertDefaultPriorities(owner, name)

        // Create the actual repository
        val gitdir = getRepositoryDir(owner, name)
        JGitUtil.initRepository(gitdir)

        if (initOption == "README" || initOption == "EMPTY_COMMIT") {
          Using.resource(Git.open(gitdir)) { git =>
            val builder = DirCache.newInCore.builder()
            val inserter = git.getRepository.newObjectInserter()
            val headId = git.getRepository.resolve(Constants.HEAD + "^{commit}")

            if (initOption == "README") {
              val content = if (description.nonEmpty) {
                name + "\n" +
                  "===============\n" +
                  "\n" +
                  description.get
              } else {
                name + "\n" +
                  "===============\n"
              }

              builder.add(
                JGitUtil.createDirCacheEntry(
                  "README.md",
                  FileMode.REGULAR_FILE,
                  inserter.insert(Constants.OBJ_BLOB, content.getBytes("UTF-8"))
                )
              )
            }
            builder.finish()

            JGitUtil.createNewCommit(
              git,
              inserter,
              headId,
              builder.getDirCache.writeTree(inserter),
              Constants.HEAD,
              loginAccount.fullName,
              loginAccount.mailAddress,
              "Initial commit"
            )
          }
        }

        copyRepositoryDir.foreach { dir =>
          try {
            Using.resource(Git.open(dir)) { git =>
              git.push().setRemote(gitdir.toURI.toString).setPushAll().setPushTags().call()
            }
          } finally {
            FileUtils.deleteQuietly(dir)
          }
        }

        // Create Wiki repository
        createWikiRepository(loginAccount, owner, name)

        // Record activity
        recordCreateRepositoryActivity(owner, name, loginUserName)

        // Call hooks
        PluginRegistry().getRepositoryHooks.foreach(_.created(owner, name))
      }

      RepositoryCreationService.endCreation(owner, name, None)

    } catch {
      case ex: Exception => RepositoryCreationService.endCreation(owner, name, Some(ex.toString))
    }
  }

  def forkRepository(accountName: String, repository: RepositoryInfo, loginUserName: String): Future[Unit] = Future {
    RepositoryCreationService.startCreation(accountName, repository.name)
    try {
      LockUtil.lock(s"${accountName}/${repository.name}") {
        Database() withTransaction { implicit session =>
          val originUserName = repository.repository.originUserName.getOrElse(repository.owner)
          val originRepositoryName = repository.repository.originRepositoryName.getOrElse(repository.name)

          insertRepository(
            repositoryName = repository.name,
            userName = accountName,
            description = repository.repository.description,
            isPrivate = repository.repository.isPrivate,
            originRepositoryName = Some(originRepositoryName),
            originUserName = Some(originUserName),
            parentRepositoryName = Some(repository.name),
            parentUserName = Some(repository.owner)
          )

          // Set default collaborators for the private fork
          if (repository.repository.isPrivate) {
            // Copy collaborators from the source repository
            getCollaborators(repository.owner, repository.name).foreach {
              case (collaborator, _) =>
                addCollaborator(accountName, repository.name, collaborator.collaboratorName, collaborator.role)
            }
            // Register an owner of the source repository as a collaborator
            addCollaborator(accountName, repository.name, repository.owner, Role.ADMIN.name)
          }

          // Insert default labels
          insertDefaultLabels(accountName, repository.name)
          // Insert default priorities
          insertDefaultPriorities(accountName, repository.name)

          // clone repository actually
          JGitUtil.cloneRepository(
            getRepositoryDir(repository.owner, repository.name),
            FileUtil.deleteIfExists(getRepositoryDir(accountName, repository.name))
          )

          // Create Wiki repository
          JGitUtil.cloneRepository(
            getWikiRepositoryDir(repository.owner, repository.name),
            FileUtil.deleteIfExists(getWikiRepositoryDir(accountName, repository.name))
          )

          // Copy LFS files
          val lfsDir = getLfsDir(repository.owner, repository.name)
          if (lfsDir.exists) {
            FileUtils.copyDirectory(lfsDir, FileUtil.deleteIfExists(getLfsDir(accountName, repository.name)))
          }

          // Record activity
          recordForkActivity(repository.owner, repository.name, loginUserName, accountName)

          // Call hooks
          PluginRegistry().getRepositoryHooks.foreach(_.forked(repository.owner, accountName, repository.name))

          RepositoryCreationService.endCreation(accountName, repository.name, None)
        }
      }
    } catch {
      case ex: Exception => RepositoryCreationService.endCreation(accountName, repository.name, Some(ex.toString))
    }
  }

  def insertDefaultLabels(userName: String, repositoryName: String)(implicit s: Session): Unit = {
    createLabel(userName, repositoryName, "bug", "fc2929")
    createLabel(userName, repositoryName, "duplicate", "cccccc")
    createLabel(userName, repositoryName, "enhancement", "84b6eb")
    createLabel(userName, repositoryName, "invalid", "e6e6e6")
    createLabel(userName, repositoryName, "question", "cc317c")
    createLabel(userName, repositoryName, "wontfix", "ffffff")
  }

  def insertDefaultPriorities(userName: String, repositoryName: String)(implicit s: Session): Unit = {
    createPriority(
      userName,
      repositoryName,
      "highest",
      Some("All defects at this priority must be fixed before any public product is delivered."),
      "fc2929"
    )
    createPriority(
      userName,
      repositoryName,
      "very high",
      Some("Issues must be addressed before a final product is delivered."),
      "fc5629"
    )
    createPriority(
      userName,
      repositoryName,
      "high",
      Some(
        "Issues should be addressed before a final product is delivered. If the issue cannot be resolved before delivery, it should be prioritized for the next release."
      ),
      "fc9629"
    )
    createPriority(
      userName,
      repositoryName,
      "important",
      Some("Issues can be shipped with a final product, but should be reviewed before the next release."),
      "fccd29"
    )
    createPriority(userName, repositoryName, "default", Some("Default."), "acacac")

    setDefaultPriority(userName, repositoryName, getPriority(userName, repositoryName, "default").map(_.priorityId))
  }
}
