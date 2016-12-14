package gitbucket.core.util

import gitbucket.core.service.RepositoryService
import org.eclipse.jgit.api.Git
import Directory._
import StringUtil._
import ControlUtil._

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import org.eclipse.jgit.lib._
import org.eclipse.jgit.revwalk._
import org.eclipse.jgit.revwalk.filter._
import org.eclipse.jgit.treewalk._
import org.eclipse.jgit.treewalk.filter._
import org.eclipse.jgit.diff.DiffEntry.ChangeType
import org.eclipse.jgit.errors.{ConfigInvalidException, MissingObjectException}
import org.eclipse.jgit.transport.RefSpec
import java.util.Date
import java.util.concurrent.{ConcurrentHashMap, TimeUnit}
import java.util.function.Consumer

import org.cache2k.{Cache2kBuilder, CacheEntry}
import org.eclipse.jgit.api.errors.{InvalidRefNameException, JGitInternalException, NoHeadException, RefAlreadyExistsException}
import org.eclipse.jgit.dircache.DirCacheEntry
import org.slf4j.LoggerFactory

/**
 * Provides complex JGit operations.
 */
object JGitUtil {

  private val logger = LoggerFactory.getLogger(JGitUtil.getClass)

  /**
   * The repository data.
   *
   * @param owner the user name of the repository owner
   * @param name the repository name
   * @param commitCount the commit count. If the repository has over 1000 commits then this property is 1001.
   * @param branchList the list of branch names
   * @param tags the list of tags
   */
  case class RepositoryInfo(owner: String, name: String, commitCount: Int, branchList: List[String], tags: List[TagInfo]){
    def this(owner: String, name: String) = {
      this(owner, name, 0, Nil, Nil)
    }
  }

  /**
   * The file data for the file list of the repository viewer.
   *
   * @param id the object id
   * @param isDirectory whether is it directory
   * @param name the file (or directory) name
   * @param message the last commit message
   * @param commitId the last commit id
   * @param time the last modified time
   * @param author the last committer name
   * @param mailAddress the committer's mail address
   * @param linkUrl the url of submodule
   */
  case class FileInfo(id: ObjectId, isDirectory: Boolean, name: String, message: String, commitId: String,
                      time: Date, author: String, mailAddress: String, linkUrl: Option[String])

  /**
   * The commit data.
   *
   * @param id the commit id
   * @param shortMessage the short message
   * @param fullMessage the full message
   * @param parents the list of parent commit id
   * @param authorTime the author time
   * @param authorName the author name
   * @param authorEmailAddress the mail address of the author
   * @param commitTime the commit time
   * @param committerName  the committer name
   * @param committerEmailAddress the mail address of the committer
   */
  case class CommitInfo(id: String, shortMessage: String, fullMessage: String, parents: List[String],
                        authorTime: Date, authorName: String, authorEmailAddress: String,
                        commitTime: Date, committerName: String, committerEmailAddress: String){
    
    def this(rev: org.eclipse.jgit.revwalk.RevCommit) = this(
        rev.getName,
        rev.getShortMessage,
        rev.getFullMessage,
        rev.getParents().map(_.name).toList,
        rev.getAuthorIdent.getWhen,
        rev.getAuthorIdent.getName,
        rev.getAuthorIdent.getEmailAddress,
        rev.getCommitterIdent.getWhen,
        rev.getCommitterIdent.getName,
        rev.getCommitterIdent.getEmailAddress)

    val summary = getSummaryMessage(fullMessage, shortMessage)

    val description = defining(fullMessage.trim.indexOf("\n")){ i =>
      if(i >= 0){
        Some(fullMessage.trim.substring(i).trim)
      } else None
    }

    def isDifferentFromAuthor: Boolean = authorName != committerName || authorEmailAddress != committerEmailAddress
  }

  case class DiffInfo(
    changeType: ChangeType,
    oldPath: String,
    newPath: String,
    oldContent: Option[String],
    newContent: Option[String],
    oldIsImage: Boolean,
    newIsImage: Boolean,
    oldObjectId: Option[String],
    newObjectId: Option[String],
    oldMode: String,
    newMode: String,
    tooLarge: Boolean
  )

  /**
   * The file content data for the file content view of the repository viewer.
   *
   * @param viewType "image", "large" or "other"
   * @param content the string content
   * @param charset the character encoding
   */
  case class ContentInfo(viewType: String, content: Option[String], charset: Option[String]){
    /**
     * the line separator of this content ("LF" or "CRLF")
     */
    val lineSeparator: String = if(content.exists(_.indexOf("\r\n") >= 0)) "CRLF" else "LF"
  }

  /**
   * The tag data.
   *
   * @param name the tag name
   * @param time the tagged date
   * @param id the commit id
   */
  case class TagInfo(name: String, time: Date, id: String)

  /**
   * The submodule data
   *
   * @param name the module name
   * @param path the path in the repository
   * @param url the repository url of this module
   */
  case class SubmoduleInfo(name: String, path: String, url: String)

  case class BranchMergeInfo(ahead: Int, behind: Int, isMerged: Boolean)

  case class BranchInfo(name: String, committerName: String, commitTime: Date, committerEmailAddress:String, mergeInfo: Option[BranchMergeInfo], commitId: String)

  case class BlameInfo(id: String, authorName: String, authorEmailAddress: String, authorTime:java.util.Date,
    prev: Option[String], prevPath: Option[String], commitTime:java.util.Date, message:String, lines:Set[Int])

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
  def getRepositoryInfo(owner: String, repository: String): RepositoryInfo = {
    using(Git.open(getRepositoryDir(owner, repository))){ git =>
      try {
        // get commit count
        val commitCount = git.log.all.call.iterator.asScala.map(_ => 1).take(10001).sum

        RepositoryInfo(
          owner, repository,
          // commit count
          commitCount,
          // branches
          git.branchList.call.asScala.map { ref =>
            ref.getName.stripPrefix("refs/heads/")
          }.toList,
          // tags
          git.tagList.call.asScala.map { ref =>
            val revCommit = getRevCommitFromId(git, ref.getObjectId)
            TagInfo(ref.getName.stripPrefix("refs/tags/"), revCommit.getCommitterIdent.getWhen, revCommit.getName)
          }.sortBy(_.time).toList
        )
      } catch {
        // not initialized
        case e: NoHeadException => RepositoryInfo(owner, repository, 0, Nil, Nil)

      }
    }
  }

  private def usingTreeWalk(git: Git, path: String, rev: RevCommit)(f: TreeWalk => Any): Unit = if (path == ".") {
    val treeWalk = new TreeWalk(git.getRepository)
    treeWalk.addTree(rev.getTree)
    using(treeWalk)(f)
  } else {
    val treeWalk = TreeWalk.forPath(git.getRepository, path, rev.getTree)
    if(treeWalk != null){
      treeWalk.enterSubtree
      using(treeWalk)(f)
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
    using(new RevWalk(git.getRepository)){ revWalk =>
      val objectId = git.getRepository.resolve(revision)
      if(objectId == null) return Nil
      val revCommit  = revWalk.parseCommit(objectId)
      val commits    = getCachedCommits(git, objectId)
      val lastCommit = commits.headOption.orNull

      @tailrec
      def simplifyPath(tuple: (ObjectId, FileMode, String, Option[String], CachedCommit)): (ObjectId, FileMode, String, Option[String], CachedCommit) = tuple match {
        case (oid, FileMode.TREE, name, _, commit ) =>
          (using(new TreeWalk(git.getRepository)) { walk =>
            walk.addTree(oid)
            // single tree child, or None
            if(walk.next() && walk.getFileMode(0) == FileMode.TREE){
              Some((walk.getObjectId(0), walk.getFileMode(0), name + "/" + walk.getNameString, None, commit)).filterNot(_ => walk.next())
            } else {
              None
            }
          }) match {
            case Some(child) => simplifyPath(child)
            case _ => tuple
          }
        case _ => tuple
      }

      @tailrec
      def findLastCommits(result: ListBuffer[(ObjectId, FileMode, String, Option[String], CachedCommit)],
                          fileList: ListBuffer[((ObjectId, FileMode, String, Option[String]), Map[String, CachedCommit])],
                          commits: List[CachedCommit]): List[(ObjectId, FileMode, String, Option[String], CachedCommit)] ={
        if(fileList.isEmpty){
          result.toList
        } else if(commits.isEmpty){ // maybe, revCommit has only 1 log. other case, restList be empty
          result.appendAll(fileList.map { case ((objectId, fileMode, fileName, linkUrl), map) =>
            (objectId, fileMode, fileName, linkUrl, map.values.headOption.getOrElse(lastCommit))
          })
          result.toList
        } else {
          val newCommit :: restCommits = commits
          val (targets, skips) = fileList.partition { case (_, parentsMap) => parentsMap.contains(newCommit.commitId) }
          if(targets.isEmpty){
            findLastCommits(result, fileList, restCommits)
          } else {
            val newParentsMap = newCommit.parentIds.map(_ -> newCommit).toMap
            val objectIds = newCommit.objects(git, path)

            targets.foreach { case ((objectId, fileMode, fileName, linkUrl), parentsMap) =>
              if(objectIds.contains(objectId)){
                if(newParentsMap.isEmpty){
                  result.append((objectId, fileMode, fileName, linkUrl, newCommit))
                } else {
                  skips.append((objectId, fileMode, fileName, linkUrl) -> newParentsMap)
                }
              } else {
                val restParentsMap = parentsMap - newCommit.commitId
                if(restParentsMap.isEmpty){
                  result.append((objectId, fileMode, fileName, linkUrl, parentsMap(newCommit.commitId)))
                } else {
                  skips.append((objectId, fileMode, fileName, linkUrl) -> restParentsMap)
                }
              }
            }

            findLastCommits(result, skips, restCommits)
          }
        }
      }

      val fileList = new ListBuffer[(ObjectId, FileMode, String, Option[String])]()
      usingTreeWalk(git, path, revCommit){ treeWalk =>
        while (treeWalk.next()) {
          val linkUrl = if (treeWalk.getFileMode(0) == FileMode.GITLINK) {
            getSubmodules(git, revCommit.getTree).find(_.path == treeWalk.getPathString).map(_.url)
          } else None
          fileList.append((treeWalk.getObjectId(0), treeWalk.getFileMode(0), treeWalk.getNameString, linkUrl))
        }
      }

      val nextParentsMap: Map[String, CachedCommit] = Option(lastCommit).map { commit =>
        commit.parentIds.map(_ -> commit).toMap
      }.getOrElse(Map.empty)

      findLastCommits(new collection.mutable.ListBuffer(), fileList.map(a => a -> nextParentsMap), commits)
        .map(simplifyPath)
        .map { case (objectId, fileMode, name, linkUrl, commit) =>
          FileInfo(
            objectId,
            fileMode == FileMode.TREE || fileMode == FileMode.GITLINK,
            name,
            commit.message,
            commit.commitId,
            commit.when,
            commit.author,
            commit.email,
            linkUrl)
        }.sortWith { (file1, file2) =>
          (file1.isDirectory, file2.isDirectory) match {
            case (true , false) => true
            case (false, true ) => false
           case _ => file1.name.compareTo(file2.name) < 0
          }
        }
    }
  }

  case class CachedCommit(
    message: String,
    commitId: String,
    when: java.util.Date,
    author: String,
    email: String,
    parentIds: Seq[String],
    revCommit: RevCommit
  ){
    def objects(git: Git, path: String): List[ObjectId] = {
      if(path == "."){
        val key = (git.getRepository.getDirectory.getAbsolutePath, path, commitId)
        val value = treeCache.get(key)
        if(value != null){
          value
        } else {
          val list = _objects(git, path)
          treeCache.put(key, list)
          list
        }
      } else {
        _objects(git, path)
      }
    }

    private def _objects(git: Git, path: String): List[ObjectId] = {
      val buffer = new ListBuffer[ObjectId]()
      usingTreeWalk(git, path, revCommit) { walk =>
        while(walk.next) {
          buffer += walk.getObjectId(0)
        }
      }
      buffer.toList
    }
  }

  private val treeCache = new Cache2kBuilder[(String, String, String), List[ObjectId]]() {}
    .name("ObjectId")
    .expireAfterWrite(60, TimeUnit.MINUTES)
    .entryCapacity(1000)
    .build()


  object CachedCommit {
    def apply(git: Git, revCommit: RevCommit): CachedCommit = {
      CachedCommit(
        getSummaryMessage(revCommit.getFullMessage, revCommit.getShortMessage),
        revCommit.getName,
        revCommit.getAuthorIdent.getWhen,
        revCommit.getAuthorIdent.getName,
        revCommit.getAuthorIdent.getEmailAddress,
        revCommit.getParents.map(_.getName).toSeq,
        revCommit
      )
    }
  }

  private val commitCache = new Cache2kBuilder[(String, String), List[CachedCommit]]() {}
    .name("CachedCommit")
    .expireAfterWrite(60, TimeUnit.MINUTES)
    .entryCapacity(100)
    .build()

  def updateCachedCommits(git: Git, start: ObjectId): List[CachedCommit] = {
    val list = new ListBuffer[CachedCommit]()
    val i = git.log.add(start).call.iterator
    while (i.hasNext) {
      val revCommit = i.next()
      list += CachedCommit(git, revCommit)
    }
    val value = list.toList
    commitCache.put((git.getRepository.getDirectory.getAbsolutePath, start.getName), value)
    value
  }

  def getCachedCommits(git: Git, start: ObjectId): List[CachedCommit] = {
    val value = commitCache.get((git.getRepository.getDirectory.getAbsolutePath, start.getName))
    if(value != null){
      value
    } else {
      updateCachedCommits(git, start)
    }
  }

  def removeCachedCommits(git: Git): Unit = {
    val key = git.getRepository.getDirectory.getAbsolutePath
    // TODO replace with function literal in Scala 2.12
    commitCache.forEach(new Consumer[CacheEntry[(String, String), List[CachedCommit]]] {
      override def accept(t: CacheEntry[(String, String), List[CachedCommit]]): Unit = {
        if(t.getKey._1 == key){
          commitCache.remove(t.getKey)
        }
      }
    })
    treeCache.forEach(new Consumer[CacheEntry[(String, String, String), List[ObjectId]]] {
      override def accept(t: CacheEntry[(String, String, String), List[ObjectId]]): Unit = {
        if(t.getKey._1 == key){
          treeCache.remove(t.getKey)
        }
      }
    })
  }

  /**
   * Returns the first line of the commit message.
   */
  private def getSummaryMessage(fullMessage: String, shortMessage: String): String = {
    defining(fullMessage.trim.indexOf("\n")){ i =>
      defining(if(i >= 0) fullMessage.trim.substring(0, i).trim else fullMessage){ firstLine =>
        if(firstLine.length > shortMessage.length) shortMessage else firstLine
      }
    }
  }

  /**
   * get all file list by revision. only file.
   */
  def getTreeId(git: Git, revision: String): Option[String] = {
    using(new RevWalk(git.getRepository)){ revWalk =>
      val objectId  = git.getRepository.resolve(revision)
      if(objectId == null) return None
      val revCommit = revWalk.parseCommit(objectId)
      Some(revCommit.getTree.name)
    }
  }

  /**
   * get all file list by tree object id.
   */
  def getAllFileListByTreeId(git: Git, treeId: String): List[String] = {
    using(new RevWalk(git.getRepository)){ revWalk =>
      val objectId  = git.getRepository.resolve(treeId+"^{tree}")
      if(objectId == null) return Nil
      using(new TreeWalk(git.getRepository)){ treeWalk =>
        treeWalk.addTree(objectId)
        treeWalk.setRecursive(true)
        var ret: List[String] = Nil
        if(treeWalk != null){
          while (treeWalk.next()) {
            ret +:= treeWalk.getPathString
          }
        }
        ret.reverse
      }
    }
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
            revWalk.setTreeFilter(AndTreeFilter.create(PathFilter.create(path), TreeFilter.ANY_DIFF))
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
        val oldCommit = if(revCommit.getParentCount >= 2) {
          // merge commit
          revCommit.getParents.head
        } else {
          commits(1)
        }
        (getDiffs(git, oldCommit.getName, id, fetchContent), Some(oldCommit.getName))

      } else {
        // initial commit
        using(new TreeWalk(git.getRepository)){ treeWalk =>
          treeWalk.addTree(revCommit.getTree)
          val buffer = new scala.collection.mutable.ListBuffer[DiffInfo]()
          while(treeWalk.next){
            val newIsImage = FileUtil.isImage(treeWalk.getPathString)
            buffer.append((if(!fetchContent){
              DiffInfo(
                changeType  = ChangeType.ADD,
                oldPath     = null,
                newPath     = treeWalk.getPathString,
                oldContent  = None,
                newContent  = None,
                oldIsImage  = false,
                newIsImage  = newIsImage,
                oldObjectId = None,
                newObjectId = Option(treeWalk.getObjectId(0)).map(_.name),
                oldMode     = treeWalk.getFileMode(0).toString,
                newMode     = treeWalk.getFileMode(0).toString,
                tooLarge    = false
              )
            } else {
              DiffInfo(
                changeType  = ChangeType.ADD,
                oldPath     = null,
                newPath     = treeWalk.getPathString,
                oldContent  = None,
                newContent  = JGitUtil.getContentFromId(git, treeWalk.getObjectId(0), false).filter(FileUtil.isText).map(convertFromByteArray),
                oldIsImage  = false,
                newIsImage  = newIsImage,
                oldObjectId = None,
                newObjectId = Option(treeWalk.getObjectId(0)).map(_.name),
                oldMode     = treeWalk.getFileMode(0).toString,
                newMode     = treeWalk.getFileMode(0).toString,
                tooLarge    = false
              )
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
    git.getRepository.getConfig.setString("diff", null, "renames", "copies")

    val diffs = git.diff.setNewTree(newTreeIter).setOldTree(oldTreeIter).call.asScala
    diffs.map { diff =>
      if(diffs.size > 100){
        DiffInfo(
          changeType  = diff.getChangeType,
          oldPath     = diff.getOldPath,
          newPath     = diff.getNewPath,
          oldContent  = None,
          newContent  = None,
          oldIsImage  = false,
          newIsImage  = false,
          oldObjectId = Option(diff.getOldId).map(_.name),
          newObjectId = Option(diff.getNewId).map(_.name),
          oldMode     = diff.getOldMode.toString,
          newMode     = diff.getNewMode.toString,
          tooLarge    = true
        )
      } else {
        val oldIsImage = FileUtil.isImage(diff.getOldPath)
        val newIsImage = FileUtil.isImage(diff.getNewPath)
        if(!fetchContent || oldIsImage || newIsImage){
          DiffInfo(
            changeType  = diff.getChangeType,
            oldPath     = diff.getOldPath,
            newPath     = diff.getNewPath,
            oldContent  = None,
            newContent  = None,
            oldIsImage  = oldIsImage,
            newIsImage  = newIsImage,
            oldObjectId = Option(diff.getOldId).map(_.name),
            newObjectId = Option(diff.getNewId).map(_.name),
            oldMode     = diff.getOldMode.toString,
            newMode     = diff.getNewMode.toString,
            tooLarge    = false
          )
        } else {
          DiffInfo(
            changeType  = diff.getChangeType,
            oldPath     = diff.getOldPath,
            newPath     = diff.getNewPath,
            oldContent  = JGitUtil.getContentFromId(git, diff.getOldId.toObjectId, false).filter(FileUtil.isText).map(convertFromByteArray),
            newContent  = JGitUtil.getContentFromId(git, diff.getNewId.toObjectId, false).filter(FileUtil.isText).map(convertFromByteArray),
            oldIsImage  = oldIsImage,
            newIsImage  = newIsImage,
            oldObjectId = Option(diff.getOldId).map(_.name),
            newObjectId = Option(diff.getNewId).map(_.name),
            oldMode     = diff.getOldMode.toString,
            newMode     = diff.getNewMode.toString,
            tooLarge    = false
          )
        }
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
      repository.create(true)
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

  def createBranch(git: Git, fromBranch: String, newBranch: String) = {
    try {
      git.branchCreate().setStartPoint(fromBranch).setName(newBranch).call()
      removeCachedCommits(git)
      Right("Branch created.")
    } catch {
      case e: RefAlreadyExistsException => Left("Sorry, that branch already exists.")
      // JGitInternalException occurs when new branch name is 'a' and the branch whose name is 'a/*' exists.
      case _: InvalidRefNameException | _: JGitInternalException => Left("Sorry, that name is invalid.")
    }
  }

  def createDirCacheEntry(path: String, mode: FileMode, objectId: ObjectId): DirCacheEntry = {
    val entry = new DirCacheEntry(path)
    entry.setFileMode(mode)
    entry.setObjectId(objectId)
    entry
  }

  def createNewCommit(git: Git, inserter: ObjectInserter, headId: AnyObjectId, treeId: AnyObjectId,
                      ref: String, fullName: String, mailAddress: String, message: String): ObjectId = {
    val newCommit = new CommitBuilder()
    newCommit.setCommitter(new PersonIdent(fullName, mailAddress))
    newCommit.setAuthor(new PersonIdent(fullName, mailAddress))
    newCommit.setMessage(message)
    if(headId != null){
      newCommit.setParentIds(List(headId).asJava)
    }
    newCommit.setTreeId(treeId)

    val newHeadId = inserter.insert(newCommit)
    inserter.flush()
    inserter.close()

    val refUpdate = git.getRepository.updateRef(ref)
    refUpdate.setNewObjectId(newHeadId)
    refUpdate.update()

    removeCachedCommits(git)
    newHeadId
  }

  /**
   * Read submodule information from .gitmodules
   */
  def getSubmodules(git: Git, tree: RevTree): List[SubmoduleInfo] = {
    val repository = git.getRepository
    getContentFromPath(git, tree, ".gitmodules", true).map { bytes =>
      (try {
        val config = new BlobBasedConfig(repository.getConfig(), bytes)
        config.getSubsections("submodule").asScala.map { module =>
          val path = config.getString("submodule", module, "path")
          val url  = config.getString("submodule", module, "url")
          SubmoduleInfo(module, path, url)
        }
      } catch {
        case e: ConfigInvalidException => {
          logger.error("Failed to load .gitmodules file for " + repository.getDirectory(), e)
          Nil
        }
      }).toList
    } getOrElse Nil
	}

  /**
   * Get object content of the given path as byte array from the Git repository.
   *
   * @param git the Git object
   * @param revTree the rev tree
   * @param path the path
   * @param fetchLargeFile if false then returns None for the large file
   * @return the byte array of content or None if object does not exist
   */
  def getContentFromPath(git: Git, revTree: RevTree, path: String, fetchLargeFile: Boolean): Option[Array[Byte]] = {
    @scala.annotation.tailrec
    def getPathObjectId(path: String, walk: TreeWalk): Option[ObjectId] = walk.next match {
      case true if(walk.getPathString == path) => Some(walk.getObjectId(0))
      case true  => getPathObjectId(path, walk)
      case false => None
    }

    using(new TreeWalk(git.getRepository)){ treeWalk =>
      treeWalk.addTree(revTree)
      treeWalk.setRecursive(true)
      getPathObjectId(path, treeWalk)
    } flatMap { objectId =>
      getContentFromId(git, objectId, fetchLargeFile)
    }
  }

  def getContentInfo(git: Git, path: String, objectId: ObjectId): ContentInfo = {
    // Viewer
    using(git.getRepository.getObjectDatabase){ db =>
      val loader = db.open(objectId)
      val large  = FileUtil.isLarge(loader.getSize)
      val viewer = if(FileUtil.isImage(path)) "image" else if(large) "large" else "other"
      val bytes  = if(viewer == "other") JGitUtil.getContentFromId(git, objectId, false) else None

      if(viewer == "other"){
        if(bytes.isDefined && FileUtil.isText(bytes.get)){
          // text
          ContentInfo("text", Some(StringUtil.convertFromByteArray(bytes.get)), Some(StringUtil.detectEncoding(bytes.get)))
        } else {
          // binary
          ContentInfo("binary", None, None)
        }
      } else {
        // image or large
        ContentInfo(viewer, None, None)
      }
    }
  }

  /**
   * Get object content of the given object id as byte array from the Git repository.
   *
   * @param git the Git object
   * @param id the object id
   * @param fetchLargeFile if false then returns None for the large file
   * @return the byte array of content or None if object does not exist
   */
  def getContentFromId(git: Git, id: ObjectId, fetchLargeFile: Boolean): Option[Array[Byte]] = try {
    using(git.getRepository.getObjectDatabase){ db =>
      val loader = db.open(id)
      if(loader.isLarge || (fetchLargeFile == false && FileUtil.isLarge(loader.getSize))){
        None
      } else {
        Some(loader.getBytes)
      }
    }
  } catch {
    case e: MissingObjectException => None
  }

  /**
   * Get objectLoader of the given object id from the Git repository.
   *
   * @param git the Git object
   * @param id the object id
   * @param f the function process ObjectLoader
   * @return None if object does not exist
   */
  def getObjectLoaderFromId[A](git: Git, id: ObjectId)(f: ObjectLoader => A):Option[A] = try {
    using(git.getRepository.getObjectDatabase){ db =>
      Some(f(db.open(id)))
    }
  } catch {
    case e: MissingObjectException => None
  }

  /**
   * Returns all commit id in the specified repository.
   */
  def getAllCommitIds(git: Git): Seq[String] = if(isEmpty(git)) {
    Nil
  } else {
    val existIds = new scala.collection.mutable.ListBuffer[String]()
    val i = git.log.all.call.iterator
    while(i.hasNext){
      existIds += i.next.name
    }
    existIds.toSeq
  }

  def processTree[T](git: Git, id: ObjectId)(f: (String, CanonicalTreeParser) => T): Seq[T] = {
    using(new RevWalk(git.getRepository)){ revWalk =>
      using(new TreeWalk(git.getRepository)){ treeWalk =>
        val index = treeWalk.addTree(revWalk.parseTree(id))
        treeWalk.setRecursive(true)
        val result = new collection.mutable.ListBuffer[T]()
        while(treeWalk.next){
          result += f(treeWalk.getPathString, treeWalk.getTree(index, classOf[CanonicalTreeParser]))
        }
        result.toSeq
      }
    }
  }

  /**
   * Returns the identifier of the root commit (or latest merge commit) of the specified branch.
   */
  def getForkedCommitId(oldGit: Git, newGit: Git,
                        userName: String, repositoryName: String, branch: String,
                        requestUserName: String, requestRepositoryName: String, requestBranch: String): String =
    defining(getAllCommitIds(oldGit)){ existIds =>
      getCommitLogs(newGit, requestBranch, true) { commit =>
        existIds.contains(commit.name) && getBranchesOfCommit(oldGit, commit.getName).contains(branch)
      }.head.id
    }

  /**
   * Fetch pull request contents into refs/pull/${issueId}/head and return (commitIdTo, commitIdFrom)
   */
  def updatePullRequest(userName: String, repositoryName:String, branch: String, issueId: Int,
                        requestUserName: String, requestRepositoryName: String, requestBranch: String):(String, String) =
    using(Git.open(Directory.getRepositoryDir(userName, repositoryName)),
          Git.open(Directory.getRepositoryDir(requestUserName, requestRepositoryName))){ (oldGit, newGit) =>
      oldGit.fetch
        .setRemote(Directory.getRepositoryDir(requestUserName, requestRepositoryName).toURI.toString)
        .setRefSpecs(new RefSpec(s"refs/heads/${requestBranch}:refs/pull/${issueId}/head").setForceUpdate(true))
        .call

      val commitIdTo = oldGit.getRepository.resolve(s"refs/pull/${issueId}/head").getName
      val commitIdFrom = getForkedCommitId(oldGit, newGit,
        userName, repositoryName, branch,
        requestUserName, requestRepositoryName, requestBranch)
      (commitIdTo, commitIdFrom)
    }

  /**
   * Returns the last modified commit of specified path
   *
   * @param git the Git object
   * @param startCommit the search base commit id
   * @param path the path of target file or directory
   * @return the last modified commit of specified path
   */
  def getLastModifiedCommit(git: Git, startCommit: RevCommit, path: String): RevCommit = {
    return git.log.add(startCommit).addPath(path).setMaxCount(1).call.iterator.next
  }

  def getBranches(owner: String, name: String, defaultBranch: String, origin: Boolean): Seq[BranchInfo] = {
    using(Git.open(getRepositoryDir(owner, name))){ git =>
      val repo = git.getRepository
      val defaultObject = if (repo.getAllRefs.keySet().contains(defaultBranch)) {
        repo.resolve(defaultBranch)
      } else {
        git.branchList().call().iterator().next().getObjectId
      }

      git.branchList.call.asScala.map { ref =>
        val walk = new RevWalk(repo)
        try {
          val defaultCommit  = walk.parseCommit(defaultObject)
          val branchName     = ref.getName.stripPrefix("refs/heads/")
          val branchCommit   = walk.parseCommit(ref.getObjectId)
          val when           = branchCommit.getCommitterIdent.getWhen
          val committer      = branchCommit.getCommitterIdent.getName
          val committerEmail = branchCommit.getCommitterIdent.getEmailAddress
          val mergeInfo      = if(origin && branchName == defaultBranch){
            None
          } else {
            walk.reset()
            walk.setRevFilter( RevFilter.MERGE_BASE )
            walk.markStart(branchCommit)
            walk.markStart(defaultCommit)
            val mergeBase = walk.next()
            walk.reset()
            walk.setRevFilter(RevFilter.ALL)
            Some(BranchMergeInfo(
              ahead    = RevWalkUtils.count(walk, branchCommit, mergeBase),
              behind   = RevWalkUtils.count(walk, defaultCommit, mergeBase),
              isMerged = walk.isMergedInto(branchCommit, defaultCommit)))
          }
          BranchInfo(branchName, committer, when, committerEmail, mergeInfo, ref.getObjectId.name)
        } finally {
          walk.dispose();
        }
      }
    }
  }

  def getBlame(git: Git, id: String, path: String): Iterable[BlameInfo] = {
    Option(git.getRepository.resolve(id)).map{ commitId =>
      val blamer = new org.eclipse.jgit.api.BlameCommand(git.getRepository);
      blamer.setStartCommit(commitId)
      blamer.setFilePath(path)
      val blame = blamer.call()
      var blameMap = Map[String, JGitUtil.BlameInfo]()
      var idLine = List[(String, Int)]()
      val commits = 0.to(blame.getResultContents().size()-1).map{ i =>
        val c = blame.getSourceCommit(i)
        if(!blameMap.contains(c.name)){
          blameMap += c.name -> JGitUtil.BlameInfo(
            c.name,
            c.getAuthorIdent.getName,
            c.getAuthorIdent.getEmailAddress,
            c.getAuthorIdent.getWhen,
            Option(git.log.add(c).addPath(blame.getSourcePath(i)).setSkip(1).setMaxCount(2).call.iterator.next)
              .map(_.name),
            if(blame.getSourcePath(i)==path){ None }else{ Some(blame.getSourcePath(i)) },
            c.getCommitterIdent.getWhen,
            c.getShortMessage,
            Set.empty)
        }
        idLine :+= (c.name, i)
      }
      val limeMap = idLine.groupBy(_._1).mapValues(_.map(_._2).toSet)
      blameMap.values.map{b => b.copy(lines=limeMap(b.id))}
    }.getOrElse(Seq.empty)
  }

  /**
   * Returns sha1
   *
   * @param owner repository owner
   * @param name  repository name
   * @param revstr  A git object references expression
   * @return sha1
   */
  def getShaByRef(owner:String, name:String,revstr: String): Option[String] = {
    using(Git.open(getRepositoryDir(owner, name))){ git =>
      Option(git.getRepository.resolve(revstr)).map(ObjectId.toString(_))
    }
  }
}
