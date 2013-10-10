package util

import org.eclipse.jgit.api.Git
import util.Directory._
import util.StringUtil._
import util.ControlUtil._
import scala.collection.JavaConverters._
import org.eclipse.jgit.lib._
import org.eclipse.jgit.revwalk._
import org.eclipse.jgit.revwalk.filter._
import org.eclipse.jgit.treewalk._
import org.eclipse.jgit.treewalk.filter._
import org.eclipse.jgit.diff.DiffEntry.ChangeType
import org.eclipse.jgit.errors.MissingObjectException
import java.util.Date
import org.eclipse.jgit.api.errors.NoHeadException
import service.RepositoryService

/**
 * Provides complex JGit operations.
 */
object JGitUtil {

  /**
   * The repository data.
   *
   * @param owner the user name of the repository owner
   * @param name the repository name
   * @param url the repository URL
   * @param commitCount the commit count. If the repository has over 1000 commits then this property is 1001.
   * @param branchList the list of branch names
   * @param tags the list of tags
   */
  case class RepositoryInfo(owner: String, name: String, url: String, commitCount: Int, branchList: List[String], tags: List[TagInfo])

  /**
   * The file data for the file list of the repository viewer.
   *
   * @param id the object id
   * @param isDirectory whether is it directory
   * @param name the file (or directory) name
   * @param time the last modified time
   * @param message the last commit message
   * @param commitId the last commit id
   * @param committer the last committer name
   */
  case class FileInfo(id: ObjectId, isDirectory: Boolean, name: String, time: Date, message: String, commitId: String, committer: String)

  /**
   * The commit data.
   *
   * @param id the commit id
   * @param time the commit time
   * @param committer  the committer name
   * @param mailAddress the mail address of the committer
   * @param shortMessage the short message
   * @param fullMessage the full message
   * @param parents the list of parent commit id
   */
  case class CommitInfo(id: String, time: Date, committer: String, mailAddress: String,
                        shortMessage: String, fullMessage: String, parents: List[String]){
    
    def this(rev: org.eclipse.jgit.revwalk.RevCommit) = this(
        rev.getName,
        rev.getCommitterIdent.getWhen,
        rev.getCommitterIdent.getName,
        rev.getCommitterIdent.getEmailAddress,
        rev.getShortMessage,
        rev.getFullMessage,
        rev.getParents().map(_.name).toList)

    val summary = defining(fullMessage.trim.indexOf("\n")){ i =>
      defining(if(i >= 0) fullMessage.trim.substring(0, i).trim else fullMessage){ firstLine =>
        if(firstLine.length > shortMessage.length) shortMessage else firstLine
      }
    }

    val description = defining(fullMessage.trim.indexOf("\n")){ i =>
      optionIf(i >= 0){
        Some(fullMessage.trim.substring(i).trim)
      }
    }
  }

  case class DiffInfo(changeType: ChangeType, oldPath: String, newPath: String, oldContent: Option[String], newContent: Option[String])

  /**
   * The file content data for the file content view of the repository viewer.
   *
   * @param viewType "image", "large" or "other"
   * @param content the string content
   */
  case class ContentInfo(viewType: String, content: Option[String])

  /**
   * The tag data.
   *
   * @param name the tag name
   * @param time the tagged date
   * @param id the commit id
   */
  case class TagInfo(name: String, time: Date, id: String)

  /**
   * Returns RevCommit from the commit or tag id.
   * 
   * @param git the Git object
   * @param objectId the ObjectId of the commit or tag
   * @return the RevCommit for the specified commit or tag
   */
  def getRevCommitFromId(git: Git, objectId: ObjectId): RevCommit = {
    val revWalk = new RevWalk(git.getRepository)
    val revCommit = revWalk.parseAny(objectId) match {
      case r: RevTag => revWalk.parseCommit(r.getObject)
      case _         => revWalk.parseCommit(objectId)
    }
    revWalk.dispose
    revCommit
  }
  
  /**
   * Returns the repository information. It contains branch names and tag names.
   */
  def getRepositoryInfo(owner: String, repository: String, baseUrl: String): RepositoryInfo = {
    using(Git.open(getRepositoryDir(owner, repository))){ git =>
      try {
        // get commit count
        val commitCount = git.log.all.call.iterator.asScala.map(_ => 1).take(1000).sum

        RepositoryInfo(
          owner, repository, s"${baseUrl}/git/${owner}/${repository}.git",
          // commit count
          commitCount,
          // branches
          git.branchList.call.asScala.map { ref =>
            ref.getName.replaceFirst("^refs/heads/", "")
          }.toList,
          // tags
          git.tagList.call.asScala.map { ref =>
            val revCommit = getRevCommitFromId(git, ref.getObjectId)
            TagInfo(ref.getName.replaceFirst("^refs/tags/", ""), revCommit.getCommitterIdent.getWhen, revCommit.getName)
          }.toList
        )
      } catch {
        // not initialized
        case e: NoHeadException => RepositoryInfo(
          owner, repository, s"${baseUrl}/git/${owner}/${repository}.git", 0, Nil, Nil)

      }
    }
  }
  
  /**
   * Returns the file list of the specified path.
   * 
   * @param git the Git object
   * @param revision the branch name or commit id
   * @param path the directory path (optional)
   * @return HTML of the file list
   */
  def getFileList(git: Git, revision: String, path: String = "."): List[FileInfo] = {
    val list = new scala.collection.mutable.ListBuffer[(ObjectId, FileMode, String, String)]

    using(new RevWalk(git.getRepository)){ revWalk =>
      val objectId  = git.getRepository.resolve(revision)
      val revCommit = revWalk.parseCommit(objectId)

      using(new TreeWalk(git.getRepository)){ treeWalk =>
        treeWalk.addTree(revCommit.getTree)
        if(path != "."){
          treeWalk.setRecursive(true)
          treeWalk.setFilter(new TreeFilter(){

            var stopRecursive = false

            def include(walker: TreeWalk): Boolean = {
              val targetPath = walker.getPathString
              if((path + "/").startsWith(targetPath)){
                true
              } else if(targetPath.startsWith(path + "/") && targetPath.substring(path.length + 1).indexOf("/") < 0){
                stopRecursive = true
                treeWalk.setRecursive(false)
                true
              } else {
                false
              }
            }

            def shouldBeRecursive(): Boolean = !stopRecursive

            override def clone: TreeFilter = return this
          })
        }
        while (treeWalk.next()) {
          list.append((treeWalk.getObjectId(0), treeWalk.getFileMode(0), treeWalk.getPathString, treeWalk.getNameString))
        }
      }
    }

    val commits = getLatestCommitFromPaths(git, list.toList.map(_._3), revision)
    list.map { case (objectId, fileMode, path, name) =>
      FileInfo(
        objectId,
        fileMode == FileMode.TREE,
        name,
        commits(path).getCommitterIdent.getWhen,
        commits(path).getShortMessage,
        commits(path).getName,
        commits(path).getCommitterIdent.getName)
    }.sortWith { (file1, file2) =>
      (file1.isDirectory, file2.isDirectory) match {
        case (true , false) => true
        case (false, true ) => false
       case _ => file1.name.compareTo(file2.name) < 0
      }
    }.toList
  }
  
  /**
   * Returns the commit list of the specified branch.
   * 
   * @param git the Git object
   * @param revision the branch name or commit id
   * @param page the page number (1-)
   * @param limit the number of commit info per page. 0 (default) means unlimited.
   * @param path filters by this path. default is no filter.
   * @return a tuple of the commit list and whether has next, or the error message
   */
  def getCommitLog(git: Git, revision: String, page: Int = 1, limit: Int = 0, path: String = ""): Either[String, (List[CommitInfo], Boolean)] = {
    val fixedPage = if(page <= 0) 1 else page
    
    @scala.annotation.tailrec
    def getCommitLog(i: java.util.Iterator[RevCommit], count: Int, logs: List[CommitInfo]): (List[CommitInfo], Boolean)  =
      i.hasNext match {
        case true if(limit <= 0 || logs.size < limit) => {
          val commit = i.next
          getCommitLog(i, count + 1, if(limit <= 0 || (fixedPage - 1) * limit <= count) logs :+ new CommitInfo(commit) else logs)
        }
        case _ => (logs, i.hasNext)
      }
    
    using(new RevWalk(git.getRepository)){ revWalk =>
      defining(git.getRepository.resolve(revision)){ objectId =>
        if(objectId == null){
          Left(s"${revision} can't be resolved.")
        } else {
          revWalk.markStart(revWalk.parseCommit(objectId))
          if(path.nonEmpty){
            revWalk.setRevFilter(new RevFilter(){
              def include(walk: RevWalk, commit: RevCommit): Boolean = {
                getDiffs(git, commit.getName, false)._1.find(_.newPath == path).nonEmpty
              }
              override def clone(): RevFilter = this
            })
          }
          Right(getCommitLog(revWalk.iterator, 0, Nil))
        }
      }
    }
  }

  def getCommitLogs(git: Git, begin: String, includesLastCommit: Boolean = false)
                   (endCondition: RevCommit => Boolean): List[CommitInfo] = {
    @scala.annotation.tailrec
    def getCommitLog(i: java.util.Iterator[RevCommit], logs: List[CommitInfo]): List[CommitInfo] =
      i.hasNext match {
        case true  => {
          val revCommit = i.next
          if(endCondition(revCommit)){
            if(includesLastCommit) logs :+ new CommitInfo(revCommit) else logs
          } else {
            getCommitLog(i, logs :+ new CommitInfo(revCommit))
          }
        }
        case false => logs
      }

    using(new RevWalk(git.getRepository)){ revWalk =>
      revWalk.markStart(revWalk.parseCommit(git.getRepository.resolve(begin)))
      getCommitLog(revWalk.iterator, Nil).reverse
    }
  }

  
  /**
   * Returns the commit list between two revisions.
   * 
   * @param git the Git object
   * @param from the from revision
   * @param to the to revision
   * @return the commit list
   */
  // TODO swap parameters 'from' and 'to'!?
  def getCommitLog(git: Git, from: String, to: String): List[CommitInfo] =
    getCommitLogs(git, to)(_.getName == from)
  
  /**
   * Returns the latest RevCommit of the specified path.
   * 
   * @param git the Git object
   * @param path the path
   * @param revision the branch name or commit id
   * @return the latest commit
   */
  def getLatestCommitFromPath(git: Git, path: String, revision: String): Option[RevCommit] =
    getLatestCommitFromPaths(git, List(path), revision).get(path)

  /**
   * Returns the list of latest RevCommit of the specified paths.
   *
   * @param git the Git object
   * @param paths the list of paths
   * @param revision the branch name or commit id
   * @return the list of latest commit
   */
  def getLatestCommitFromPaths(git: Git, paths: List[String], revision: String): Map[String, RevCommit] = {
    val start = getRevCommitFromId(git, git.getRepository.resolve(revision))
    paths.map { path =>
      val commit = git.log.add(start).addPath(path).setMaxCount(1).call.iterator.next
      (path, commit)
    }.toMap
  }

  /**
   * Get object content of the given id as String from the Git repository.
   * 
   * @param git the Git object
   * @param id the object id
   * @param large if false then returns None for the large file
   * @return the object or None if object does not exist
   */
  def getContent(git: Git, id: ObjectId, large: Boolean): Option[Array[Byte]] = try {
    val loader = git.getRepository.getObjectDatabase.open(id)
    if(large == false && FileUtil.isLarge(loader.getSize)){
      None
    } else {
      using(git.getRepository.getObjectDatabase){ db =>
        Some(db.open(id).getBytes)
      }
    }
  } catch {
    case e: MissingObjectException => None
  }

  /**
   * Returns the tuple of diff of the given commit and the previous commit id.
   */
  def getDiffs(git: Git, id: String, fetchContent: Boolean = true): (List[DiffInfo], Option[String]) = {
    @scala.annotation.tailrec
    def getCommitLog(i: java.util.Iterator[RevCommit], logs: List[RevCommit]): List[RevCommit] =
      i.hasNext match {
        case true if(logs.size < 2) => getCommitLog(i, logs :+ i.next)
        case _ => logs
      }

    using(new RevWalk(git.getRepository)){ revWalk =>
      revWalk.markStart(revWalk.parseCommit(git.getRepository.resolve(id)))
      val commits   = getCommitLog(revWalk.iterator, Nil)
      val revCommit = commits(0)

      if(commits.length >= 2){
        // not initial commit
        val oldCommit = commits(1)
        (getDiffs(git, oldCommit.getName, id, fetchContent), Some(oldCommit.getName))

      } else {
        // initial commit
        using(new TreeWalk(git.getRepository)){ treeWalk =>
          treeWalk.addTree(revCommit.getTree)
          val buffer = new scala.collection.mutable.ListBuffer[DiffInfo]()
          while(treeWalk.next){
            buffer.append((if(!fetchContent){
              DiffInfo(ChangeType.ADD, null, treeWalk.getPathString, None, None)
            } else {
              DiffInfo(ChangeType.ADD, null, treeWalk.getPathString, None,
                JGitUtil.getContent(git, treeWalk.getObjectId(0), false).filter(FileUtil.isText).map(convertFromByteArray))
            }))
          }
          (buffer.toList, None)
        }
      }
    }
  }

  def getDiffs(git: Git, from: String, to: String, fetchContent: Boolean): List[DiffInfo] = {
    val reader = git.getRepository.newObjectReader
    val oldTreeIter = new CanonicalTreeParser
    oldTreeIter.reset(reader, git.getRepository.resolve(from + "^{tree}"))

    val newTreeIter = new CanonicalTreeParser
    newTreeIter.reset(reader, git.getRepository.resolve(to + "^{tree}"))

    import scala.collection.JavaConverters._
    git.diff.setNewTree(newTreeIter).setOldTree(oldTreeIter).call.asScala.map { diff =>
      if(!fetchContent || FileUtil.isImage(diff.getOldPath) || FileUtil.isImage(diff.getNewPath)){
        DiffInfo(diff.getChangeType, diff.getOldPath, diff.getNewPath, None, None)
      } else {
        DiffInfo(diff.getChangeType, diff.getOldPath, diff.getNewPath,
          JGitUtil.getContent(git, diff.getOldId.toObjectId, false).filter(FileUtil.isText).map(convertFromByteArray),
          JGitUtil.getContent(git, diff.getNewId.toObjectId, false).filter(FileUtil.isText).map(convertFromByteArray))
      }
    }.toList
  }


  /**
   * Returns the list of branch names of the specified commit.
   */
  def getBranchesOfCommit(git: Git, commitId: String): List[String] =
    using(new RevWalk(git.getRepository)){ revWalk =>
      defining(revWalk.parseCommit(git.getRepository.resolve(commitId + "^0"))){ commit =>
        git.getRepository.getAllRefs.entrySet.asScala.filter { e =>
          (e.getKey.startsWith(Constants.R_HEADS) && revWalk.isMergedInto(commit, revWalk.parseCommit(e.getValue.getObjectId)))
        }.map { e =>
          e.getValue.getName.substring(org.eclipse.jgit.lib.Constants.R_HEADS.length)
        }.toList.sorted
      }
    }

  /**
   * Returns the list of tags of the specified commit.
   */
  def getTagsOfCommit(git: Git, commitId: String): List[String] =
    using(new RevWalk(git.getRepository)){ revWalk =>
      defining(revWalk.parseCommit(git.getRepository.resolve(commitId + "^0"))){ commit =>
        git.getRepository.getAllRefs.entrySet.asScala.filter { e =>
          (e.getKey.startsWith(Constants.R_TAGS) && revWalk.isMergedInto(commit, revWalk.parseCommit(e.getValue.getObjectId)))
        }.map { e =>
          e.getValue.getName.substring(org.eclipse.jgit.lib.Constants.R_TAGS.length)
        }.toList.sorted.reverse
      }
    }

  def initRepository(dir: java.io.File): Unit =
    using(new RepositoryBuilder().setGitDir(dir).setBare.build){ repository =>
      repository.create
      setReceivePack(repository)
    }

  def cloneRepository(from: java.io.File, to: java.io.File): Unit =
    using(Git.cloneRepository.setURI(from.toURI.toString).setDirectory(to).setBare(true).call){ git =>
      setReceivePack(git.getRepository)
    }

  def isEmpty(git: Git): Boolean = git.getRepository.resolve(Constants.HEAD) == null

  private def setReceivePack(repository: org.eclipse.jgit.lib.Repository): Unit =
    defining(repository.getConfig){ config =>
      config.setBoolean("http", null, "receivepack", true)
      config.save
    }

  def getDefaultBranch(git: Git, repository: RepositoryService.RepositoryInfo,
                       revstr: String = ""): Option[(ObjectId, String)] = {
    Seq(
      Some(if(revstr.isEmpty) repository.repository.defaultBranch else revstr),
      repository.branchList.headOption
    ).flatMap {
      case Some(rev) => Some((git.getRepository.resolve(rev), rev))
      case None      => None
    }.find(_._1 != null)
  }

}
