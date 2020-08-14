package gitbucket.core.util

import java.io._

import gitbucket.core.service.RepositoryService
import org.eclipse.jgit.api.Git
import Directory._
import StringUtil._
import SyntaxSugars._

import scala.annotation.tailrec
import scala.jdk.CollectionConverters._
import scala.util.Using
import org.eclipse.jgit.lib._
import org.eclipse.jgit.revwalk._
import org.eclipse.jgit.revwalk.filter._
import org.eclipse.jgit.treewalk._
import org.eclipse.jgit.treewalk.filter._
import org.eclipse.jgit.diff.DiffEntry.ChangeType
import org.eclipse.jgit.errors.{ConfigInvalidException, IncorrectObjectTypeException, MissingObjectException}
import org.eclipse.jgit.transport.RefSpec
import java.util.Date
import java.util.concurrent.TimeUnit

import org.cache2k.Cache2kBuilder
import org.eclipse.jgit.api.errors._
import org.eclipse.jgit.diff.{DiffEntry, DiffFormatter, RawTextComparator}
import org.eclipse.jgit.dircache.DirCacheEntry
import org.eclipse.jgit.util.io.DisabledOutputStream
import org.slf4j.LoggerFactory

import scala.util.Using.Releasable

/**
 * Provides complex JGit operations.
 */
object JGitUtil {

  private val logger = LoggerFactory.getLogger(JGitUtil.getClass)

  implicit val objectDatabaseReleasable = new Releasable[ObjectDatabase] {
    override def release(resource: ObjectDatabase): Unit = resource.close()
  }

  /**
   * The repository data.
   *
   * @param owner the user name of the repository owner
   * @param name the repository name
   * @param branchList the list of branch names
   * @param tags the list of tags
   */
  case class RepositoryInfo(owner: String, name: String, branchList: List[String], tags: List[TagInfo]) {
    def this(owner: String, name: String) = this(owner, name, Nil, Nil)
  }

  /**
   * The file data for the file list of the repository viewer.
   *
   * @param id the object id
   * @param isDirectory whether is it directory
   * @param name the file (or directory) name
   * @param path the file (or directory) complete path
   * @param message the last commit message
   * @param commitId the last commit id
   * @param time the last modified time
   * @param author the last committer name
   * @param mailAddress the committer's mail address
   * @param linkUrl the url of submodule
   */
  case class FileInfo(
    id: ObjectId,
    isDirectory: Boolean,
    name: String,
    path: String,
    message: String,
    commitId: String,
    time: Date,
    author: String,
    mailAddress: String,
    linkUrl: Option[String]
  )

  /**
   * The gpg commit sign data.
   * @param signArmored signature for commit
   * @param target string for verification target
   */
  case class GpgSignInfo(signArmored: Array[Byte], target: Array[Byte])

  /**
   * The verified gpg sign data.
   * @param signedUser
   * @param signedKeyId
   */
  case class GpgVerifyInfo(signedUser: String, signedKeyId: String)

  private def getSignTarget(rev: RevCommit): Array[Byte] = {
    val ascii = "ASCII"
    val os = new ByteArrayOutputStream()
    val w = new OutputStreamWriter(os, rev.getEncoding)
    os.write("tree ".getBytes(ascii))
    rev.getTree.copyTo(os)
    os.write('\n')

    rev.getParents.foreach { p =>
      os.write("parent ".getBytes(ascii))
      p.copyTo(os)
      os.write('\n')
    }

    os.write("author ".getBytes(ascii))
    w.write(rev.getAuthorIdent.toExternalString)
    w.flush()
    os.write('\n')

    os.write("committer ".getBytes(ascii))
    w.write(rev.getCommitterIdent.toExternalString)
    w.flush()
    os.write('\n')

    if (rev.getEncoding.name != "UTF-8") {
      os.write("encoding ".getBytes(ascii))
      os.write(Constants.encodeASCII(rev.getEncoding.name))
      os.write('\n')
    }

    os.write('\n')

    if (!rev.getFullMessage.isEmpty) {
      w.write(rev.getFullMessage)
      w.flush()
    }
    os.toByteArray
  }

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
  case class CommitInfo(
    id: String,
    shortMessage: String,
    fullMessage: String,
    parents: List[String],
    authorTime: Date,
    authorName: String,
    authorEmailAddress: String,
    commitTime: Date,
    committerName: String,
    committerEmailAddress: String,
    commitSign: Option[GpgSignInfo],
    verified: Option[GpgVerifyInfo]
  ) {

    def this(rev: org.eclipse.jgit.revwalk.RevCommit) =
      this(
        rev.getName,
        rev.getShortMessage,
        rev.getFullMessage,
        rev.getParents().map(_.name).toList,
        rev.getAuthorIdent.getWhen,
        rev.getAuthorIdent.getName,
        rev.getAuthorIdent.getEmailAddress,
        rev.getCommitterIdent.getWhen,
        rev.getCommitterIdent.getName,
        rev.getCommitterIdent.getEmailAddress,
        Option(rev.getRawGpgSignature).map { s =>
          GpgSignInfo(s, getSignTarget(rev))
        },
        None
      )

    val summary = getSummaryMessage(fullMessage, shortMessage)

    val description = defining(fullMessage.trim.indexOf('\n')) { i =>
      if (i >= 0) {
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
    tooLarge: Boolean,
    patch: Option[String]
  )

  /**
   * The file content data for the file content view of the repository viewer.
   *
   * @param viewType "image", "large" or "other"
   * @param size total size of object in bytes
   * @param content the string content
   * @param charset the character encoding
   */
  case class ContentInfo(viewType: String, size: Option[Long], content: Option[String], charset: Option[String]) {

    /**
     * the line separator of this content ("LF" or "CRLF")
     */
    val lineSeparator: String = if (content.exists(_.indexOf("\r\n") >= 0)) "CRLF" else "LF"
  }

  /**
   * The tag data.
   *
   * @param name the tag name
   * @param time the tagged date
   * @param id the commit id
   * @param message the message of the tagged commit
   */
  case class TagInfo(name: String, time: Date, id: String, message: String)

  /**
   * The submodule data
   *
   * @param name the module name
   * @param path the path in the repository
   * @param repositoryUrl the repository url of this module
   * @param viewerUrl the repository viewer url of this module
   */
  case class SubmoduleInfo(name: String, path: String, repositoryUrl: String, viewerUrl: String)

  case class BranchMergeInfo(ahead: Int, behind: Int, isMerged: Boolean)

  case class BranchInfo(
    name: String,
    committerName: String,
    commitTime: Date,
    committerEmailAddress: String,
    mergeInfo: Option[BranchMergeInfo],
    commitId: String
  )

  case class BlameInfo(
    id: String,
    authorName: String,
    authorEmailAddress: String,
    authorTime: java.util.Date,
    prev: Option[String],
    prevPath: Option[String],
    commitTime: java.util.Date,
    message: String,
    lines: Set[Int]
  )

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

  private val cache = new Cache2kBuilder[String, Int]() {}
    .name("commit-count")
    .expireAfterWrite(24, TimeUnit.HOURS)
    .entryCapacity(10000)
    .build()

  private val objectCommitCache = new Cache2kBuilder[ObjectId, RevCommit]() {}
    .name("object-commit")
    .entryCapacity(10000)
    .build()

  def removeCache(git: Git): Unit = {
    val dir = git.getRepository.getDirectory
    val keyPrefix = dir.getAbsolutePath + "@"

    cache.keys.forEach(key => {
      if (key.startsWith(keyPrefix)) {
        cache.remove(key)
      }
    })
  }

  /**
   * Returns the number of commits in the specified branch or commit.
   * If the specified branch has over 10000 commits, this method returns 100001.
   */
  def getCommitCount(git: Git, branch: String, max: Int = 10001): Int = {
    val dir = git.getRepository.getDirectory
    val key = dir.getAbsolutePath + "@" + branch
    val entry = cache.getEntry(key)

    if (entry == null) {
      val commitId = git.getRepository.resolve(branch)
      val commitCount = git.log.add(commitId).call.iterator.asScala.take(max).size
      cache.put(key, commitCount)
      commitCount
    } else {
      entry.getValue
    }
  }

  /**
   * Returns the repository information. It contains branch names and tag names.
   */
  def getRepositoryInfo(owner: String, repository: String): RepositoryInfo = {
    Using.resource(Git.open(getRepositoryDir(owner, repository))) { git =>
      try {
        RepositoryInfo(
          owner,
          repository,
          // branches
          git.branchList.call.asScala.map { ref =>
            ref.getName.stripPrefix("refs/heads/")
          }.toList,
          // tags
          git.tagList.call.asScala
            .flatMap { ref =>
              try {
                val revCommit = getRevCommitFromId(git, ref.getObjectId)
                Some(
                  TagInfo(
                    ref.getName.stripPrefix("refs/tags/"),
                    revCommit.getCommitterIdent.getWhen,
                    revCommit.getName,
                    revCommit.getShortMessage
                  )
                )
              } catch {
                case _: IncorrectObjectTypeException =>
                  None
              }
            }
            .sortBy(_.time)
            .toList
        )
      } catch {
        // not initialized
        case e: NoHeadException => RepositoryInfo(owner, repository, Nil, Nil)
      }
    }
  }

  /**
   * Returns the file list of the specified path.
   *
   * @param git the Git object
   * @param revision the branch name or commit id
   * @param path the directory path (optional)
   * @param baseUrl the base url of GitBucket instance. This parameter is used to generate links of submodules (optional)
   * @param commitCount the number of commit of this repository (optional). If this number is greater than threshold, the commit info is cached in memory.
   * @param maxFiles don't fetch commit info if the number of files in the directory is bigger than this number.
   * @return The list of files in the specified directory. If the number of files are greater than threshold, the returned file list won't include the commit info.
   */
  def getFileList(
    git: Git,
    revision: String,
    path: String = ".",
    baseUrl: Option[String] = None,
    commitCount: Int = 0,
    maxFiles: Int = 100
  ): List[FileInfo] = {
    Using.resource(new RevWalk(git.getRepository)) { revWalk =>
      val objectId = git.getRepository.resolve(revision)
      if (objectId == null) return Nil
      val revCommit = revWalk.parseCommit(objectId)

      def useTreeWalk(rev: RevCommit)(f: TreeWalk => Any): Unit =
        if (path == ".") {
          val treeWalk = new TreeWalk(git.getRepository)
          treeWalk.addTree(rev.getTree)
          Using.resource(treeWalk)(f)
        } else {
          val treeWalk = TreeWalk.forPath(git.getRepository, path, rev.getTree)
          if (treeWalk != null) {
            treeWalk.enterSubtree
            Using.resource(treeWalk)(f)
          }
        }

      @tailrec
      def simplifyPath(
        tuple: (ObjectId, FileMode, String, String, Option[String], Option[RevCommit])
      ): (ObjectId, FileMode, String, String, Option[String], Option[RevCommit]) =
        tuple match {
          case (oid, FileMode.TREE, name, path, _, commit) =>
            (Using.resource(new TreeWalk(git.getRepository)) { walk =>
              walk.addTree(oid)
              // single tree child, or None
              if (walk.next() && walk.getFileMode(0) == FileMode.TREE) {
                Some(
                  (
                    walk.getObjectId(0),
                    walk.getFileMode(0),
                    name + "/" + walk.getNameString,
                    path + "/" + walk.getNameString,
                    None,
                    commit
                  )
                ).filterNot(_ => walk.next())
              } else {
                None
              }
            }) match {
              case Some(child) => simplifyPath(child)
              case _           => tuple
            }
          case _ => tuple
        }

      def appendLastCommits(
        fileList: List[(ObjectId, FileMode, String, String, Option[String])]
      ): List[(ObjectId, FileMode, String, String, Option[String], Option[RevCommit])] = {
        fileList.map {
          case (id, mode, name, path, opt) =>
            if (maxFiles > 0 && fileList.size >= maxFiles) {
              // Don't attempt to get the last commit if the number of files is very large.
              (id, mode, name, path, opt, None)
            } else if (commitCount < 10000) {
              (id, mode, name, path, opt, Some(getCommit(path)))
            } else {
              // Use in-memory cache if the commit count is too big.
              val cached = objectCommitCache.getEntry(id)
              if (cached == null) {
                val commit = getCommit(path)
                objectCommitCache.put(id, commit)
                (id, mode, name, path, opt, Some(commit))
              } else {
                (id, mode, name, path, opt, Some(cached.getValue))
              }
            }
        }
      }

      def getCommit(path: String): RevCommit = {
        git
          .log()
          .addPath(path)
          .add(revCommit)
          .setMaxCount(1)
          .call()
          .iterator()
          .next()
      }

      var fileList: List[(ObjectId, FileMode, String, String, Option[String])] = Nil
      useTreeWalk(revCommit) { treeWalk =>
        while (treeWalk.next()) {
          val linkUrl = if (treeWalk.getFileMode(0) == FileMode.GITLINK) {
            getSubmodules(git, revCommit.getTree, baseUrl).find(_.path == treeWalk.getPathString).map(_.viewerUrl)
          } else None
          fileList +:= (treeWalk.getObjectId(0), treeWalk.getFileMode(
            0
          ), treeWalk.getNameString, treeWalk.getPathString, linkUrl)
        }
      }

      appendLastCommits(fileList)
        .map(simplifyPath)
        .map {
          case (objectId, fileMode, name, path, linkUrl, commit) =>
            FileInfo(
              objectId,
              fileMode == FileMode.TREE || fileMode == FileMode.GITLINK,
              name,
              path,
              getSummaryMessage(
                commit.map(_.getFullMessage).getOrElse(""),
                commit.map(_.getShortMessage).getOrElse("")
              ),
              commit.map(_.getName).getOrElse(""),
              commit.map(_.getAuthorIdent.getWhen).orNull,
              commit.map(_.getAuthorIdent.getName).getOrElse(""),
              commit.map(_.getAuthorIdent.getEmailAddress).getOrElse(""),
              linkUrl
            )
        }
        .sortWith { (file1, file2) =>
          (file1.isDirectory, file2.isDirectory) match {
            case (true, false) => true
            case (false, true) => false
            case _             => file1.name.compareTo(file2.name) < 0
          }
        }
    }
  }

  /**
   * Returns the first line of the commit message.
   */
  private def getSummaryMessage(fullMessage: String, shortMessage: String): String = {
    defining(fullMessage.trim.indexOf('\n')) { i =>
      defining(if (i >= 0) fullMessage.trim.substring(0, i).trim else fullMessage) { firstLine =>
        if (firstLine.length > shortMessage.length) shortMessage else firstLine
      }
    }
  }

  /**
   * get all file list by revision. only file.
   */
  def getTreeId(git: Git, revision: String): Option[String] = {
    Using.resource(new RevWalk(git.getRepository)) { revWalk =>
      val objectId = git.getRepository.resolve(revision)
      if (objectId == null) return None
      val revCommit = revWalk.parseCommit(objectId)
      Some(revCommit.getTree.name)
    }
  }

  /**
   * get all file list by tree object id.
   */
  def getAllFileListByTreeId(git: Git, treeId: String): List[String] = {
    Using.resource(new RevWalk(git.getRepository)) { revWalk =>
      val objectId = git.getRepository.resolve(treeId + "^{tree}")
      if (objectId == null) return Nil
      Using.resource(new TreeWalk(git.getRepository)) { treeWalk =>
        treeWalk.addTree(objectId)
        treeWalk.setRecursive(true)
        var ret: List[String] = Nil
        if (treeWalk != null) {
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
  def getCommitLog(
    git: Git,
    revision: String,
    page: Int = 1,
    limit: Int = 0,
    path: String = ""
  ): Either[String, (List[CommitInfo], Boolean)] = {
    val fixedPage = if (page <= 0) 1 else page

    @scala.annotation.tailrec
    def getCommitLog(
      i: java.util.Iterator[RevCommit],
      count: Int,
      logs: List[CommitInfo]
    ): (List[CommitInfo], Boolean) =
      i.hasNext match {
        case true if (limit <= 0 || logs.size < limit) => {
          val commit = i.next
          getCommitLog(
            i,
            count + 1,
            if (limit <= 0 || (fixedPage - 1) * limit <= count) logs :+ new CommitInfo(commit) else logs
          )
        }
        case _ => (logs, i.hasNext)
      }

    Using.resource(new RevWalk(git.getRepository)) { revWalk =>
      defining(git.getRepository.resolve(revision)) { objectId =>
        if (objectId == null) {
          Left(s"${revision} can't be resolved.")
        } else {
          revWalk.markStart(revWalk.parseCommit(objectId))
          if (path.nonEmpty) {
            revWalk.setTreeFilter(AndTreeFilter.create(PathFilter.create(path), TreeFilter.ANY_DIFF))
          }
          Right(getCommitLog(revWalk.iterator, 0, Nil))
        }
      }
    }
  }

  def getCommitLogs(git: Git, begin: String, includesLastCommit: Boolean = false)(
    endCondition: RevCommit => Boolean
  ): List[CommitInfo] = {
    @scala.annotation.tailrec
    def getCommitLog(i: java.util.Iterator[RevCommit], logs: List[CommitInfo]): List[CommitInfo] =
      i.hasNext match {
        case true => {
          val revCommit = i.next
          if (endCondition(revCommit)) {
            if (includesLastCommit) logs :+ new CommitInfo(revCommit) else logs
          } else {
            getCommitLog(i, logs :+ new CommitInfo(revCommit))
          }
        }
        case false => logs
      }

    Using.resource(new RevWalk(git.getRepository)) { revWalk =>
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

  def getPatch(git: Git, from: Option[String], to: String): String = {
    val out = new ByteArrayOutputStream()
    val df = new DiffFormatter(out)
    df.setRepository(git.getRepository)
    df.setDiffComparator(RawTextComparator.DEFAULT)
    df.setDetectRenames(true)
    getDiffEntries(git, from, to)
      .map { entry =>
        df.format(entry)
        new String(out.toByteArray, "UTF-8")
      }
      .mkString("\n")
  }

  private def getDiffEntries(git: Git, from: Option[String], to: String): Seq[DiffEntry] = {
    Using.resource(new RevWalk(git.getRepository)) { revWalk =>
      val df = new DiffFormatter(DisabledOutputStream.INSTANCE)
      df.setRepository(git.getRepository)

      val toCommit = revWalk.parseCommit(git.getRepository.resolve(to))
      (from match {
        case None => {
          toCommit.getParentCount match {
            case 0 =>
              df.scan(
                  new EmptyTreeIterator(),
                  new CanonicalTreeParser(null, git.getRepository.newObjectReader(), toCommit.getTree)
                )
                .asScala
            case _ => df.scan(toCommit.getParent(0), toCommit.getTree).asScala
          }
        }
        case Some(from) => {
          val fromCommit = revWalk.parseCommit(git.getRepository.resolve(from))
          df.scan(fromCommit.getTree, toCommit.getTree).asScala
        }
      }).toSeq
    }
  }

  def getParentCommitId(git: Git, id: String): Option[String] = {
    Using.resource(new RevWalk(git.getRepository)) { revWalk =>
      val commit = revWalk.parseCommit(git.getRepository.resolve(id))
      commit.getParentCount match {
        case 0 => None
        case _ => Some(commit.getParent(0).getName)
      }
    }
  }

  def getDiffs(
    git: Git,
    from: Option[String],
    to: String,
    fetchContent: Boolean,
    makePatch: Boolean
  ): List[DiffInfo] = {
    val diffs = getDiffEntries(git, from, to)
    diffs.map { diff =>
      if (diffs.size > 100) {
        DiffInfo(
          changeType = diff.getChangeType,
          oldPath = diff.getOldPath,
          newPath = diff.getNewPath,
          oldContent = None,
          newContent = None,
          oldIsImage = false,
          newIsImage = false,
          oldObjectId = Option(diff.getOldId).map(_.name),
          newObjectId = Option(diff.getNewId).map(_.name),
          oldMode = diff.getOldMode.toString,
          newMode = diff.getNewMode.toString,
          tooLarge = true,
          patch = None
        )
      } else {
        val oldIsImage = FileUtil.isImage(diff.getOldPath)
        val newIsImage = FileUtil.isImage(diff.getNewPath)
        if (!fetchContent || oldIsImage || newIsImage) {
          DiffInfo(
            changeType = diff.getChangeType,
            oldPath = diff.getOldPath,
            newPath = diff.getNewPath,
            oldContent = None,
            newContent = None,
            oldIsImage = oldIsImage,
            newIsImage = newIsImage,
            oldObjectId = Option(diff.getOldId).map(_.name),
            newObjectId = Option(diff.getNewId).map(_.name),
            oldMode = diff.getOldMode.toString,
            newMode = diff.getNewMode.toString,
            tooLarge = false,
            patch = (if (makePatch) Some(makePatchFromDiffEntry(git, diff)) else None) // TODO use DiffFormatter
          )
        } else {
          DiffInfo(
            changeType = diff.getChangeType,
            oldPath = diff.getOldPath,
            newPath = diff.getNewPath,
            oldContent = JGitUtil
              .getContentFromId(git, diff.getOldId.toObjectId, false)
              .filter(FileUtil.isText)
              .map(convertFromByteArray),
            newContent = JGitUtil
              .getContentFromId(git, diff.getNewId.toObjectId, false)
              .filter(FileUtil.isText)
              .map(convertFromByteArray),
            oldIsImage = oldIsImage,
            newIsImage = newIsImage,
            oldObjectId = Option(diff.getOldId).map(_.name),
            newObjectId = Option(diff.getNewId).map(_.name),
            oldMode = diff.getOldMode.toString,
            newMode = diff.getNewMode.toString,
            tooLarge = false,
            patch = (if (makePatch) Some(makePatchFromDiffEntry(git, diff)) else None) // TODO use DiffFormatter
          )
        }
      }
    }.toList
  }

  private def makePatchFromDiffEntry(git: Git, diff: DiffEntry): String = {
    val out = new ByteArrayOutputStream()
    Using.resource(new DiffFormatter(out)) { formatter =>
      formatter.setRepository(git.getRepository)
      formatter.format(diff)
      val patch = new String(out.toByteArray) // TODO charset???
      patch.split("\n").drop(4).mkString("\n")
    }
  }

  /**
   * Returns the list of branch names of the specified commit.
   */
  def getBranchesOfCommit(git: Git, commitId: String): List[String] =
    Using.resource(new RevWalk(git.getRepository)) { revWalk =>
      defining(revWalk.parseCommit(git.getRepository.resolve(commitId + "^0"))) { commit =>
        git.getRepository.getRefDatabase
          .getRefsByPrefix(Constants.R_HEADS)
          .asScala
          .filter { e =>
            (revWalk.isMergedInto(
              commit,
              revWalk.parseCommit(e.getObjectId)
            ))
          }
          .map { e =>
            e.getName.substring(Constants.R_HEADS.length)
          }
          .toList
          .sorted
      }
    }

  /**
   * Returns the list of tags which pointed on the specified commit.
   */
  def getTagsOnCommit(git: Git, commitId: String): List[String] = {
    git.getRepository.getAllRefsByPeeledObjectId.asScala
      .get(git.getRepository.resolve(commitId + "^0"))
      .map {
        _.asScala
          .collect {
            case x if x.getName.startsWith(Constants.R_TAGS) =>
              x.getName.substring(Constants.R_TAGS.length)
          }
          .toList
          .sorted
      }
      .getOrElse {
        List.empty
      }
  }

  /**
   * Returns the list of tags which contains the specified commit.
   */
  def getTagsOfCommit(git: Git, commitId: String): List[String] =
    Using.resource(new RevWalk(git.getRepository)) { revWalk =>
      defining(revWalk.parseCommit(git.getRepository.resolve(commitId + "^0"))) { commit =>
        git.getRepository.getRefDatabase
          .getRefsByPrefix(Constants.R_TAGS)
          .asScala
          .filter { e =>
            (revWalk.isMergedInto(
              commit,
              revWalk.parseCommit(e.getObjectId)
            ))
          }
          .map { e =>
            e.getName.substring(Constants.R_TAGS.length)
          }
          .toList
          .sorted
          .reverse
      }
    }

  def initRepository(dir: java.io.File): Unit =
    Using.resource(new RepositoryBuilder().setGitDir(dir).setBare.build) { repository =>
      repository.create(true)
      setReceivePack(repository)
    }

  def cloneRepository(from: java.io.File, to: java.io.File): Unit =
    Using.resource(Git.cloneRepository.setURI(from.toURI.toString).setDirectory(to).setBare(true).call) { git =>
      setReceivePack(git.getRepository)
    }

  def isEmpty(git: Git): Boolean = git.getRepository.resolve(Constants.HEAD) == null

  private def setReceivePack(repository: org.eclipse.jgit.lib.Repository): Unit =
    defining(repository.getConfig) { config =>
      config.setBoolean("http", null, "receivepack", true)
      config.save
    }

  def getDefaultBranch(
    git: Git,
    repository: RepositoryService.RepositoryInfo,
    revstr: String = ""
  ): Option[(ObjectId, String)] = {
    Seq(
      Some(if (revstr.isEmpty) repository.repository.defaultBranch else revstr),
      repository.branchList.headOption
    ).flatMap {
        case Some(rev) => Some((git.getRepository.resolve(rev), rev))
        case None      => None
      }
      .find(_._1 != null)
  }

  def createTag(git: Git, name: String, message: Option[String], commitId: String) = {
    try {
      val objectId: ObjectId = git.getRepository.resolve(commitId)
      Using.resource(new RevWalk(git.getRepository)) { walk =>
        val tagCommand = git.tag().setName(name).setObjectId(walk.parseCommit(objectId))
        message.foreach { message =>
          tagCommand.setMessage(message)
        }
        tagCommand.call()
      }
      Right("Tag added.")
    } catch {
      case e: ConcurrentRefUpdateException => Left("Sorry, some error occurs.")
      case e: InvalidTagNameException      => Left("Sorry, that name is invalid.")
      case e: NoHeadException              => Left("Sorry, this repo doesn't have HEAD reference")
      case e: GitAPIException              => Left("Sorry, some Git operation error occurs.")
    }
  }

  def createBranch(git: Git, fromBranch: String, newBranch: String) = {
    try {
      git.branchCreate().setStartPoint(fromBranch).setName(newBranch).call()
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

  def createNewCommit(
    git: Git,
    inserter: ObjectInserter,
    headId: AnyObjectId,
    treeId: AnyObjectId,
    ref: String,
    fullName: String,
    mailAddress: String,
    message: String
  ): ObjectId = {
    val newCommit = new CommitBuilder()
    newCommit.setCommitter(new PersonIdent(fullName, mailAddress))
    newCommit.setAuthor(new PersonIdent(fullName, mailAddress))
    newCommit.setMessage(message)
    if (headId != null) {
      newCommit.setParentIds(List(headId).asJava)
    }
    newCommit.setTreeId(treeId)

    val newHeadId = inserter.insert(newCommit)
    inserter.flush()
    inserter.close()

    val refUpdate = git.getRepository.updateRef(ref)
    refUpdate.setNewObjectId(newHeadId)
    refUpdate.update()

    removeCache(git)

    newHeadId
  }

  /**
   * Read submodule information from .gitmodules
   */
  def getSubmodules(git: Git, tree: RevTree, baseUrl: Option[String]): List[SubmoduleInfo] = {
    val repository = git.getRepository
    getContentFromPath(git, tree, ".gitmodules", true).map { bytes =>
      (try {
        val config = new BlobBasedConfig(repository.getConfig(), bytes)
        config.getSubsections("submodule").asScala.map { module =>
          val path = config.getString("submodule", module, "path")
          val url = config.getString("submodule", module, "url")
          SubmoduleInfo(module, path, url, StringUtil.getRepositoryViewerUrl(url, baseUrl))
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
    def getPathObjectId(path: String, walk: TreeWalk): Option[ObjectId] =
      walk.next match {
        case true if (walk.getPathString == path) => Some(walk.getObjectId(0))
        case true                                 => getPathObjectId(path, walk)
        case false                                => None
      }

    Using.resource(new TreeWalk(git.getRepository)) { treeWalk =>
      treeWalk.addTree(revTree)
      treeWalk.setRecursive(true)
      getPathObjectId(path, treeWalk)
    } flatMap { objectId =>
      getContentFromId(git, objectId, fetchLargeFile)
    }
  }

  def getLfsObjects(text: String): Map[String, String] = {
    if (text.startsWith("version https://git-lfs.github.com/spec/v1")) {
      // LFS objects
      text
        .split("\n")
        .map { line =>
          val dim = line.split(" ")
          dim(0) -> dim(1)
        }
        .toMap
    } else {
      Map.empty
    }
  }

  def getContentSize(loader: ObjectLoader): Long = {
    if (loader.isLarge) {
      loader.getSize
    } else {
      val bytes = loader.getCachedBytes
      val text = new String(bytes, "UTF-8")

      val attr = getLfsObjects(text)
      attr.get("size") match {
        case Some(size) => size.toLong
        case None       => loader.getSize
      }
    }
  }

  def isLfsPointer(loader: ObjectLoader): Boolean = {
    !loader.isLarge && new String(loader.getBytes(), "UTF-8").startsWith("version https://git-lfs.github.com/spec/v1")
  }

  def getContentInfo(git: Git, path: String, objectId: ObjectId): ContentInfo = {
    // Viewer
    Using.resource(git.getRepository.getObjectDatabase) { db =>
      val loader = db.open(objectId)
      val isLfs = isLfsPointer(loader)
      val large = FileUtil.isLarge(loader.getSize)
      val viewer = if (FileUtil.isImage(path)) "image" else if (large) "large" else "other"
      val bytes = if (viewer == "other") JGitUtil.getContentFromId(git, objectId, false) else None
      val size = Some(getContentSize(loader))

      if (viewer == "other") {
        if (!isLfs && bytes.isDefined && FileUtil.isText(bytes.get)) {
          // text
          ContentInfo(
            "text",
            size,
            Some(StringUtil.convertFromByteArray(bytes.get)),
            Some(StringUtil.detectEncoding(bytes.get))
          )
        } else {
          // binary
          ContentInfo("binary", size, None, None)
        }
      } else {
        // image or large
        ContentInfo(viewer, size, None, None)
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
  def getContentFromId(git: Git, id: ObjectId, fetchLargeFile: Boolean): Option[Array[Byte]] =
    try {
      Using.resource(git.getRepository.getObjectDatabase) { db =>
        val loader = db.open(id)
        if (loader.isLarge || (fetchLargeFile == false && FileUtil.isLarge(loader.getSize))) {
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
  def getObjectLoaderFromId[A](git: Git, id: ObjectId)(f: ObjectLoader => A): Option[A] =
    try {
      Using.resource(git.getRepository.getObjectDatabase) { db =>
        Some(f(db.open(id)))
      }
    } catch {
      case e: MissingObjectException => None
    }

  /**
   * Returns all commit id in the specified repository.
   */
  def getAllCommitIds(git: Git): Seq[String] =
    if (isEmpty(git)) {
      Nil
    } else {
      val existIds = new scala.collection.mutable.ListBuffer[String]()
      val i = git.log.all.call.iterator
      while (i.hasNext) {
        existIds += i.next.name
      }
      existIds.toSeq
    }

  def processTree[T](git: Git, id: ObjectId)(f: (String, CanonicalTreeParser) => T): Seq[T] = {
    Using.resource(new RevWalk(git.getRepository)) { revWalk =>
      Using.resource(new TreeWalk(git.getRepository)) { treeWalk =>
        val index = treeWalk.addTree(revWalk.parseTree(id))
        treeWalk.setRecursive(true)
        val result = new collection.mutable.ListBuffer[T]()
        while (treeWalk.next) {
          result += f(treeWalk.getPathString, treeWalk.getTree(index, classOf[CanonicalTreeParser]))
        }
        result.toSeq
      }
    }
  }

  /**
   * Returns the identifier of the root commit (or latest merge commit) of the specified branch.
   */
  def getForkedCommitId(
    oldGit: Git,
    newGit: Git,
    userName: String,
    repositoryName: String,
    branch: String,
    requestUserName: String,
    requestRepositoryName: String,
    requestBranch: String
  ): String =
    defining(getAllCommitIds(oldGit)) { existIds =>
      getCommitLogs(newGit, requestBranch, true) { commit =>
        existIds.contains(commit.name) && getBranchesOfCommit(oldGit, commit.getName).contains(branch)
      }.head.id
    }

  /**
   * Fetch pull request contents into refs/pull/${issueId}/head and return (commitIdTo, commitIdFrom)
   */
  // TODO should take Git instead of owner and username for testability
  def updatePullRequest(
    userName: String,
    repositoryName: String,
    branch: String,
    issueId: Int,
    requestUserName: String,
    requestRepositoryName: String,
    requestBranch: String
  ): (String, String) =
    Using.resources(
      Git.open(Directory.getRepositoryDir(userName, repositoryName)),
      Git.open(Directory.getRepositoryDir(requestUserName, requestRepositoryName))
    ) { (oldGit, newGit) =>
      oldGit.fetch
        .setRemote(Directory.getRepositoryDir(requestUserName, requestRepositoryName).toURI.toString)
        .setRefSpecs(new RefSpec(s"refs/heads/${requestBranch}:refs/pull/${issueId}/head").setForceUpdate(true))
        .call

      val commitIdTo = oldGit.getRepository.resolve(s"refs/pull/${issueId}/head").getName
      val commitIdFrom = getForkedCommitId(
        oldGit,
        newGit,
        userName,
        repositoryName,
        branch,
        requestUserName,
        requestRepositoryName,
        requestBranch
      )
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
    git.log.add(startCommit).addPath(path).setMaxCount(1).call.iterator.next
  }

  def getBranches(git: Git, defaultBranch: String, origin: Boolean): Seq[BranchInfo] = {
    val repo = git.getRepository
    val defaultObject = repo.resolve(defaultBranch)

    git.branchList.call.asScala.map { ref =>
      val walk = new RevWalk(repo)
      try {
        val defaultCommit = walk.parseCommit(defaultObject)
        val branchName = ref.getName.stripPrefix("refs/heads/")
        val branchCommit = walk.parseCommit(ref.getObjectId)
        val when = branchCommit.getCommitterIdent.getWhen
        val committer = branchCommit.getCommitterIdent.getName
        val committerEmail = branchCommit.getCommitterIdent.getEmailAddress
        val mergeInfo = if (origin && branchName == defaultBranch) {
          None
        } else {
          walk.reset()
          walk.setRevFilter(RevFilter.MERGE_BASE)
          walk.markStart(branchCommit)
          walk.markStart(defaultCommit)
          val mergeBase = walk.next()
          walk.reset()
          walk.setRevFilter(RevFilter.ALL)
          Some(
            BranchMergeInfo(
              ahead = RevWalkUtils.count(walk, branchCommit, mergeBase),
              behind = RevWalkUtils.count(walk, defaultCommit, mergeBase),
              isMerged = walk.isMergedInto(branchCommit, defaultCommit)
            )
          )
        }
        BranchInfo(branchName, committer, when, committerEmail, mergeInfo, ref.getObjectId.name)
      } finally {
        walk.dispose()
      }
    }.toSeq
  }

  def getBlame(git: Git, id: String, path: String): Iterable[BlameInfo] = {
    Option(git.getRepository.resolve(id))
      .map { commitId =>
        val blamer = new org.eclipse.jgit.api.BlameCommand(git.getRepository)
        blamer.setStartCommit(commitId)
        blamer.setFilePath(path)
        val blame = blamer.call()
        var blameMap = Map[String, JGitUtil.BlameInfo]()
        var idLine = List[(String, Int)]()
        0.to(blame.getResultContents().size() - 1).map { i =>
          val c = blame.getSourceCommit(i)
          if (!blameMap.contains(c.name)) {
            blameMap += c.name -> JGitUtil.BlameInfo(
              c.name,
              c.getAuthorIdent.getName,
              c.getAuthorIdent.getEmailAddress,
              c.getAuthorIdent.getWhen,
              Option(git.log.add(c).addPath(blame.getSourcePath(i)).setSkip(1).setMaxCount(2).call.iterator.next)
                .map(_.name),
              if (blame.getSourcePath(i) == path) { None } else { Some(blame.getSourcePath(i)) },
              c.getCommitterIdent.getWhen,
              c.getShortMessage,
              Set.empty
            )
          }
          idLine :+= (c.name, i)
        }
        val limeMap = idLine.groupBy(_._1).view.mapValues(_.map(_._2).toSet)
        blameMap.values.map { b =>
          b.copy(lines = limeMap(b.id))
        }
      }
      .getOrElse(Seq.empty)
  }

  /**
   * Returns sha1
   *
   * @param owner repository owner
   * @param name  repository name
   * @param revstr  A git object references expression
   * @return sha1
   */
  def getShaByRef(owner: String, name: String, revstr: String): Option[String] = {
    Using.resource(Git.open(getRepositoryDir(owner, name))) { git =>
      Option(git.getRepository.resolve(revstr)).map(ObjectId.toString(_))
    }
  }

  private def openFile[T](git: Git, repository: RepositoryService.RepositoryInfo, treeWalk: TreeWalk)(
    f: InputStream => T
  ): T = {
    val attrs = treeWalk.getAttributes
    val loader = git.getRepository.open(treeWalk.getObjectId(0))
    if (attrs.containsKey("filter") && attrs.get("filter").getValue == "lfs") {
      val lfsAttrs = getLfsAttributes(loader)
      if (lfsAttrs.nonEmpty) {
        val oid = lfsAttrs("oid").split(":")(1)

        Using.resource(new FileInputStream(FileUtil.getLfsFilePath(repository.owner, repository.name, oid))) { in =>
          f(in)
        }
      } else {
        throw new NoSuchElementException("LFS attribute is empty.")
      }
    } else {
      Using.resource(loader.openStream()) { in =>
        f(in)
      }
    }
  }

  def openFile[T](git: Git, repository: RepositoryService.RepositoryInfo, tree: RevTree, path: String)(
    f: InputStream => T
  ): T = {
    Using.resource(TreeWalk.forPath(git.getRepository, path, tree)) { treeWalk =>
      openFile(git, repository, treeWalk)(f)
    }
  }

  private def getLfsAttributes(loader: ObjectLoader): Map[String, String] = {
    val bytes = loader.getCachedBytes
    val text = new String(bytes, "UTF-8")

    JGitUtil.getLfsObjects(text)
  }

}
