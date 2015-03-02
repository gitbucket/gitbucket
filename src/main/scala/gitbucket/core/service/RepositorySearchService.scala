package gitbucket.core.service

import gitbucket.core.model.Issue
import gitbucket.core.util._
import gitbucket.core.util.StringUtil
import Directory._
import ControlUtil._
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.lib.FileMode
import org.eclipse.jgit.api.Git
import gitbucket.core.model.Profile._
import profile.simple._

trait RepositorySearchService { self: IssuesService =>
  import RepositorySearchService._

  def countIssues(owner: String, repository: String, query: String)(implicit session: Session): Int =
    searchIssuesByKeyword(owner, repository, query).length

  def searchIssues(owner: String, repository: String, query: String)(implicit session: Session): List[IssueSearchResult] =
    searchIssuesByKeyword(owner, repository, query).map { case (issue, commentCount, content) =>
      IssueSearchResult(
        issue.issueId,
        issue.isPullRequest,
        issue.title,
        issue.openedUserName,
        issue.registeredDate,
        commentCount,
        getHighlightText(content, query)._1)
    }

  def countFiles(owner: String, repository: String, query: String): Int =
    using(Git.open(getRepositoryDir(owner, repository))){ git =>
      if(JGitUtil.isEmpty(git)) 0 else searchRepositoryFiles(git, query).length
    }

  def searchFiles(owner: String, repository: String, query: String): List[FileSearchResult] =
    using(Git.open(getRepositoryDir(owner, repository))){ git =>
      if(JGitUtil.isEmpty(git)){
        Nil
      } else {
        val files = searchRepositoryFiles(git, query)
        val commits = JGitUtil.getLatestCommitFromPaths(git, files.map(_._1), "HEAD")
        files.map { case (path, text) =>
          val (highlightText, lineNumber)  = getHighlightText(text, query)
          FileSearchResult(
            path,
            commits(path).getCommitterIdent.getWhen,
            highlightText,
            lineNumber)
        }
      }
    }

  private def searchRepositoryFiles(git: Git, query: String): List[(String, String)] = {
    val revWalk   = new RevWalk(git.getRepository)
    val objectId  = git.getRepository.resolve("HEAD")
    val revCommit = revWalk.parseCommit(objectId)
    val treeWalk  = new TreeWalk(git.getRepository)
    treeWalk.setRecursive(true)
    treeWalk.addTree(revCommit.getTree)

    val keywords = StringUtil.splitWords(query.toLowerCase)
    val list = new scala.collection.mutable.ListBuffer[(String, String)]

    while (treeWalk.next()) {
      val mode = treeWalk.getFileMode(0)
      if(mode == FileMode.REGULAR_FILE || mode == FileMode.EXECUTABLE_FILE){
        JGitUtil.getContentFromId(git, treeWalk.getObjectId(0), false).foreach { bytes =>
          if(FileUtil.isText(bytes)){
            val text      = StringUtil.convertFromByteArray(bytes)
            val lowerText = text.toLowerCase
            val indices   = keywords.map(lowerText.indexOf _)
            if(!indices.exists(_ < 0)){
              list.append((treeWalk.getPathString, text))
            }
          }
        }
      }
    }
    treeWalk.release
    revWalk.release

    list.toList
  }

}

object RepositorySearchService {

  val CodeLimit  = 10
  val IssueLimit = 10

  def getHighlightText(content: String, query: String): (String, Int) = {
    val keywords  = StringUtil.splitWords(query.toLowerCase)
    val lowerText = content.toLowerCase
    val indices   = keywords.map(lowerText.indexOf _)

    if(!indices.exists(_ < 0)){
      val lineNumber = content.substring(0, indices.min).split("\n").size - 1
      val highlightText = StringUtil.escapeHtml(content.split("\n").drop(lineNumber).take(5).mkString("\n"))
        .replaceAll("(?i)(" + keywords.map("\\Q" + _ + "\\E").mkString("|") +  ")",
        "<span class=\"highlight\">$1</span>")
      (highlightText, lineNumber + 1)
    } else {
      (content.split("\n").take(5).mkString("\n"), 1)
    }
  }

  case class SearchResult(
    files : List[(String, String)],
    issues: List[(Issue, Int, String)])

  case class IssueSearchResult(
    issueId: Int,
    isPullRequest: Boolean,
    title: String,
    openedUserName: String,
    registeredDate: java.util.Date,
    commentCount: Int,
    highlightText: String)

  case class FileSearchResult(
     path: String,
     lastModified: java.util.Date,
     highlightText: String,
     highlightLineNumber: Int)

}
