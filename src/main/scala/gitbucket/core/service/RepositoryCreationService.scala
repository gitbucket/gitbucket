package gitbucket.core.service

import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap

import gitbucket.core.model.Profile.profile.blockingApi._
import gitbucket.core.util.SyntaxSugars._
import gitbucket.core.util.Directory._
import gitbucket.core.util.JGitUtil
import gitbucket.core.model.Account
import gitbucket.core.servlet.Database
import org.apache.commons.io.FileUtils
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.dircache.DirCache
import org.eclipse.jgit.lib.{Constants, FileMode}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object RepositoryCreationService {

  private val Creating = new ConcurrentHashMap[String, Boolean]()

  def isCreating(owner: String, repository: String): Boolean = {
    Creating.containsKey(s"${owner}/${repository}")
  }

  def startCreation(owner: String, repository: String): Unit = {
    Creating.put(s"${owner}/${repository}", true)
  }

  def endCreation(owner: String, repository: String): Unit = {
    Creating.remove(s"${owner}/${repository}")
  }

}

trait RepositoryCreationService {
  self: AccountService with RepositoryService with LabelsService with WikiService with ActivityService with PrioritiesService =>

  def createRepository(loginAccount: Account, owner: String, name: String, description: Option[String],
    isPrivate: Boolean, createReadme: Boolean): Future[Unit] = {
    createRepository(loginAccount, owner, name, description, isPrivate, if (createReadme) "README" else "EMPTY", None)
  }

  def createRepository(loginAccount: Account, owner: String, name: String, description: Option[String],
    isPrivate: Boolean, initOption: String, sourceUrl: Option[String]): Future[Unit] = Future {
    RepositoryCreationService.startCreation(owner, name)
    try {
      Database() withTransaction { implicit session =>
        val ownerAccount = getAccountByUserName(owner).get
        val loginUserName = loginAccount.userName

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

        if (initOption == "README") {
          using(Git.open(gitdir)) { git =>
            val builder = DirCache.newInCore.builder()
            val inserter = git.getRepository.newObjectInserter()
            val headId = git.getRepository.resolve(Constants.HEAD + "^{commit}")
            val content = if (description.nonEmpty) {
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

        if (initOption == "COPY") {
          sourceUrl.foreach { url =>
            // TODO How to feedback error in this block?
            val dir = Files.createTempDirectory(s"gitbucket-${owner}-${name}").toFile

            Git.cloneRepository().setBare(true).setURI(url).setDirectory(dir).setCloneAllBranches(true).call()
            using(Git.open(dir)) { git =>
              git.push().setRemote(gitdir.toURI.toString).setPushAll().setPushTags().call()
            }

            FileUtils.deleteQuietly(dir)
          }
        }

        // Create Wiki repository
        createWikiRepository(loginAccount, owner, name)

        // Record activity
        recordCreateRepositoryActivity(owner, name, loginUserName)
      }
    } finally {
      RepositoryCreationService.endCreation(owner, name)
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
    createPriority(userName, repositoryName, "highest", Some("All defects at this priority must be fixed before any public product is delivered."), "fc2929")
    createPriority(userName, repositoryName, "very high", Some("Issues must be addressed before a final product is delivered."), "fc5629")
    createPriority(userName, repositoryName, "high", Some("Issues should be addressed before a final product is delivered. If the issue cannot be resolved before delivery, it should be prioritized for the next release."), "fc9629")
    createPriority(userName, repositoryName, "important", Some("Issues can be shipped with a final product, but should be reviewed before the next release."), "fccd29")
    createPriority(userName, repositoryName, "default", Some("Default."), "acacac")

    setDefaultPriority(userName, repositoryName, getPriority(userName, repositoryName, "default").map(_.priorityId))
  }
}
