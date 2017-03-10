package gitbucket.core.service

import gitbucket.core.model.Profile.profile.blockingApi._
import gitbucket.core.util.SyntaxSugars._
import gitbucket.core.util.Directory._
import gitbucket.core.util.JGitUtil
import gitbucket.core.model.Account
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.dircache.DirCache
import org.eclipse.jgit.lib.{FileMode, Constants}

trait RepositoryCreationService {
  self: AccountService with RepositoryService with LabelsService with WikiService with ActivityService =>

  def createRepository(loginAccount: Account, owner: String, name: String, description: Option[String], isPrivate: Boolean, createReadme: Boolean)
                      (implicit s: Session) {
    val ownerAccount  = getAccountByUserName(owner).get
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

  def insertDefaultLabels(userName: String, repositoryName: String)(implicit s: Session): Unit = {
    createLabel(userName, repositoryName, "bug", "fc2929")
    createLabel(userName, repositoryName, "duplicate", "cccccc")
    createLabel(userName, repositoryName, "enhancement", "84b6eb")
    createLabel(userName, repositoryName, "invalid", "e6e6e6")
    createLabel(userName, repositoryName, "question", "cc317c")
    createLabel(userName, repositoryName, "wontfix", "ffffff")
  }


}
