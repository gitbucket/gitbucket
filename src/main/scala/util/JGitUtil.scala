package util

import org.eclipse.jgit.api.Git
import util.Directory._
import scala.collection.JavaConverters._
import javax.servlet.ServletContext
import org.eclipse.jgit.lib._
import org.eclipse.jgit.revwalk._
import org.eclipse.jgit.revwalk.filter._
import org.eclipse.jgit.treewalk._
import org.eclipse.jgit.treewalk.filter._
import org.eclipse.jgit.diff._
import org.eclipse.jgit.diff.DiffEntry.ChangeType
import org.eclipse.jgit.util.io.DisabledOutputStream
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
   * @param shortMessage the short message
   * @param fullMessage the full message
   * @param parents the list of parent commit id
   */
  case class CommitInfo(id: String, time: Date, committer: String, shortMessage: String, fullMessage: String, parents: List[String]){
    
    def this(rev: org.eclipse.jgit.revwalk.RevCommit) = this(
        rev.getName, rev.getCommitterIdent.getWhen, rev.getCommitterIdent.getName, rev.getShortMessage, rev.getFullMessage,
        rev.getParents().map(_.name).toList)

    val summary = {
      val i = fullMessage.trim.indexOf("\n")
      val firstLine = if(i >= 0){
        fullMessage.trim.substring(0, i).trim
      } else {
        fullMessage
      }
      if(firstLine.length > shortMessage.length){
        shortMessage
      } else {
        firstLine
      }
    }

    val description = {
      val i = fullMessage.trim.indexOf("\n")
      if(i >= 0){
        Some(fullMessage.trim.substring(i).trim)
      } else {
        None
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
   * Use this method to use the Git object.
   * Repository resources are released certainly after processing.
   */
  def withGit[T](dir: java.io.File)(f: Git => T): T = withGit(Git.open(dir))(f)
  
  /**
   * Use this method to use the Git object.
   * Repository resources are released certainly after processing.
   */
  def withGit[T](git: Git)(f: Git => T): T = {
    try {
      f(git)
    } finally {
      git.getRepository.close
    }
  }
  
  /**
   * Returns RevCommit from the commit id.
   * 
   * @param git the Git object
   * @param commitId the ObjectId of the commit
   * @return the RevCommit for the specified commit
   */
  def getRevCommitFromId(git: Git, commitId: ObjectId): RevCommit = {
    val revWalk = new RevWalk(git.getRepository)
    val revCommit = revWalk.parseCommit(commitId)
    revWalk.dispose
    revCommit
  }
  
  /**
   * Returns the repository information. It contains branch names and tag names.
   */
  def getRepositoryInfo(owner: String, repository: String, baseUrl: String): RepositoryInfo = {
    withGit(getRepositoryDir(owner, repository)){ git =>
      try {
        // get commit count
        val i = git.log.all.call.iterator
        var commitCount = 0
        while(i.hasNext && commitCount <= 1000){
          i.next
          commitCount = commitCount + 1
        }

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
    val revWalk = new RevWalk(git.getRepository)
    val objectId = git.getRepository.resolve(revision)
    val revCommit = revWalk.parseCommit(objectId)
      
    val treeWalk = new TreeWalk(git.getRepository)
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
      
    val list = new scala.collection.mutable.ListBuffer[(ObjectId, FileMode, String, String)]
    
    while (treeWalk.next()) {
      list.append((treeWalk.getObjectId(0), treeWalk.getFileMode(0), treeWalk.getPathString, treeWalk.getNameString))
    }
    
    treeWalk.release
    revWalk.dispose

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
   * @return a tuple of the commit list and whether has next
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
    
    val revWalk = new RevWalk(git.getRepository)
    val objectId = git.getRepository.resolve(revision)
    if(objectId == null){
      Left(s"${revision} can't be resolved.")
    } else {
      revWalk.markStart(revWalk.parseCommit(objectId))
      if(path.nonEmpty){
        revWalk.setRevFilter(new RevFilter(){
          def include(walk: RevWalk, commit: RevCommit): Boolean = {
            getDiffs(git, commit.getName, false).find(_.newPath == path).nonEmpty
          }
          override def clone(): RevFilter = this
        })
      }

      val commits = getCommitLog(revWalk.iterator, 0, Nil)
      revWalk.release

      Right(commits)
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
  def getCommitLog(git: Git, from: String, to: String): List[CommitInfo] = {
    @scala.annotation.tailrec
    def getCommitLog(i: java.util.Iterator[RevCommit], logs: List[CommitInfo]): List[CommitInfo] =
      i.hasNext match {
        case true  => {
          val revCommit = i.next
          if(revCommit.name == from){
            logs 
          } else {
            getCommitLog(i, logs :+ new CommitInfo(revCommit))
          }
        }
        case false => logs
      }
    
    val revWalk = new RevWalk(git.getRepository)
    revWalk.markStart(revWalk.parseCommit(git.getRepository.resolve(to)))
    
    val commits = getCommitLog(revWalk.iterator, Nil)
    revWalk.release
    
    commits.reverse
  }
  
  
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

    val map = new scala.collection.mutable.HashMap[String, RevCommit]

    val revWalk = new RevWalk(git.getRepository)
    revWalk.markStart(revWalk.parseCommit(git.getRepository.resolve(revision)))
    //revWalk.sort(RevSort.REVERSE);
    val i = revWalk.iterator

    while(i.hasNext && map.size != paths.length){
      val commit = i.next
      if(commit.getParentCount == 0){
        // Initial commit
        val treeWalk = new TreeWalk(git.getRepository)
        treeWalk.reset()
        treeWalk.setRecursive(true)
        treeWalk.addTree(commit.getTree)
        while (treeWalk.next) {
          paths.foreach { path =>
            if(treeWalk.getPathString.startsWith(path) && !map.contains(path)){
              map.put(path, commit)
            }
          }
        }
        treeWalk.release
      } else {
        (0 to commit.getParentCount - 1).foreach { i =>
          val parent = revWalk.parseCommit(commit.getParent(i).getId())
          val df = new DiffFormatter(DisabledOutputStream.INSTANCE)
          df.setRepository(git.getRepository)
          df.setDiffComparator(RawTextComparator.DEFAULT)
          df.setDetectRenames(true)
          val diffs = df.scan(parent.getTree(), commit.getTree)
          diffs.asScala.foreach { diff =>
            paths.foreach { path =>
              if(diff.getChangeType != ChangeType.DELETE && diff.getNewPath.startsWith(path) && !map.contains(path)){
                map.put(path, commit)
              }
            }
          }
        }
      }

      revWalk.release
    }
    map.toMap
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
      val db = git.getRepository.getObjectDatabase
      try {
        Some(db.open(id).getBytes)
      } finally {
        db.close
      }
    }
  } catch {
    case e: MissingObjectException => None
  }
  
  def getDiffs(git: Git, id: String, fetchContent: Boolean = true): List[DiffInfo] = {
    @scala.annotation.tailrec
    def getCommitLog(i: java.util.Iterator[RevCommit], logs: List[RevCommit]): List[RevCommit] =
      i.hasNext match {
        case true if(logs.size < 2) => getCommitLog(i, logs :+ i.next)
        case _ => logs
      }

    val revWalk = new RevWalk(git.getRepository)
    revWalk.markStart(revWalk.parseCommit(git.getRepository.resolve(id)))
    
    val commits = getCommitLog(revWalk.iterator, Nil)
    revWalk.release
    
    val revCommit = commits(0)
    
    if(commits.length >= 2){
      // not initial commit
      val oldCommit = commits(1)
      
      // get diff between specified commit and its previous commit
      val reader = git.getRepository.newObjectReader
      
      val oldTreeIter = new CanonicalTreeParser
      oldTreeIter.reset(reader, git.getRepository.resolve(oldCommit.name + "^{tree}"))
      
      val newTreeIter = new CanonicalTreeParser
      newTreeIter.reset(reader, git.getRepository.resolve(id + "^{tree}"))
      
      import scala.collection.JavaConverters._
      git.diff.setNewTree(newTreeIter).setOldTree(oldTreeIter).call.asScala.map { diff =>
        if(!fetchContent || FileUtil.isImage(diff.getOldPath) || FileUtil.isImage(diff.getNewPath)){
          DiffInfo(diff.getChangeType, diff.getOldPath, diff.getNewPath, None, None)
        } else {
          DiffInfo(diff.getChangeType, diff.getOldPath, diff.getNewPath,
            JGitUtil.getContent(git, diff.getOldId.toObjectId, false).filter(FileUtil.isText).map(new String(_, "UTF-8")),
            JGitUtil.getContent(git, diff.getNewId.toObjectId, false).filter(FileUtil.isText).map(new String(_, "UTF-8")))
        }
      }.toList
    } else {
      // initial commit
      val walk = new TreeWalk(git.getRepository)
      walk.addTree(revCommit.getTree)
      val buffer = new scala.collection.mutable.ListBuffer[DiffInfo]()
      while(walk.next){
        buffer.append((if(!fetchContent){
          DiffInfo(ChangeType.ADD, null, walk.getPathString, None, None)
        } else {
          DiffInfo(ChangeType.ADD, null, walk.getPathString, None, 
              JGitUtil.getContent(git, walk.getObjectId(0), false).filter(FileUtil.isText).map(new String(_, "UTF-8")))
        }))
      }
      walk.release
      buffer.toList
    }
  }

  /**
   * Returns the list of branch names of the specified commit.
   */
  def getBranchesOfCommit(git: Git, commitId: String): List[String] = {
    val walk = new org.eclipse.jgit.revwalk.RevWalk(git.getRepository)
    try {
      val commit = walk.parseCommit(git.getRepository.resolve(commitId + "^0"))

      git.getRepository.getAllRefs.entrySet.asScala.filter { e =>
        (e.getKey.startsWith(Constants.R_HEADS) && walk.isMergedInto(commit, walk.parseCommit(e.getValue.getObjectId)))
      }.map { e =>
        e.getValue.getName.substring(org.eclipse.jgit.lib.Constants.R_HEADS.length)
      }.toList.sorted

    } finally {
      walk.release
    }
  }

  /**
   * Returns the list of tags of the specified commit.
   */
  def getTagsOfCommit(git: Git, commitId: String): List[String] = {
    val walk = new org.eclipse.jgit.revwalk.RevWalk(git.getRepository)
    try {
      val commit = walk.parseCommit(git.getRepository.resolve(commitId + "^0"))

      git.getRepository.getAllRefs.entrySet.asScala.filter { e =>
        (e.getKey.startsWith(Constants.R_TAGS) && walk.isMergedInto(commit, walk.parseCommit(e.getValue.getObjectId)))
      }.map { e =>
        e.getValue.getName.substring(org.eclipse.jgit.lib.Constants.R_TAGS.length)
      }.toList.sorted.reverse

    } finally {
      walk.release
    }
  }

  def initRepository(dir: java.io.File): Unit = {
    val repository = new RepositoryBuilder().setGitDir(dir).setBare.build
    try {
      repository.create
      setReceivePack(repository)
    } finally {
      repository.close
    }
  }

  def cloneRepository(from: java.io.File, to: java.io.File): Unit = {
    val git = Git.cloneRepository.setURI(from.toURI.toString).setDirectory(to).setBare(true).call
    try {
      setReceivePack(git.getRepository)
    } finally {
      git.getRepository.close
    }
  }

  private def setReceivePack(repository: org.eclipse.jgit.lib.Repository): Unit = {
    val config = repository.getConfig
    config.setBoolean("http", null, "receivepack", true)
    config.save
  }

  def getDefaultBranch(git: Git, repository: RepositoryService.RepositoryInfo,
                       revstr: String = ""): Option[(ObjectId, String)] = {
    Seq(
      if(revstr.isEmpty) repository.repository.defaultBranch else revstr,
      repository.branchList.head
    ).map { rev =>
      (git.getRepository.resolve(rev), rev)
    }.find(_._1 != null)
  }

}