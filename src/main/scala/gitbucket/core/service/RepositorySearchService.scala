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
import org.apache.lucene.analysis.cjk.CJKAnalyzer
import org.apache.lucene.document._
import org.apache.lucene.store._
import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.queryparser.classic.QueryParser
import profile.simple._

trait RepositorySearchService { self: IssuesService =>
  import RepositorySearchService._

  private val OwnerField = "owner"
  private val RepositoryField = "repository"
  private val IndexedObjectIdField = "indexedObjectId"
  private val PathField = "path"
  private val ContentField = "content"

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
      withIndexSearcher { indexSearcher =>
        if(JGitUtil.isEmpty(git)) 0 else searchIndexFiles(indexSearcher, owner, repository, query, 1).totalHits
      }
    }

  def searchFiles(owner: String, repository: String, query: String, page: Int): List[FileSearchResult] =
    using(Git.open(getRepositoryDir(owner, repository))){ git =>
      if(JGitUtil.isEmpty(git)){
        Nil
      } else {
        if(!existsIndex(owner, repository, git.getRepository.resolve("HEAD").name())){
          updateIndexFiles(owner, repository, git)
        }

        withIndexSearcher { indexSearcher =>
          val docs = searchIndexFiles(indexSearcher, owner, repository, query, page)
          summarizeSearchResults(indexSearcher, docs, git, query, page)
        }
      }
    }

  private def withIndexSearcher[T](f: IndexSearcher => T): T = {
    val directory = FSDirectory.open(getIndexDir.toPath)
    val reader = DirectoryReader.open(directory)
    val indexSearcher = new IndexSearcher(reader)

    try f(indexSearcher) finally {
      reader.close
      directory.close
    }
  }

  private def existsIndex(owner: String, repository: String, objectId: String): Boolean = {
    if(!getIndexDir.exists) return false

    withIndexSearcher { indexSearcher =>
      val booleanQuery = new BooleanQuery()
      booleanQuery.add(new TermQuery(new Term(OwnerField, owner)), BooleanClause.Occur.MUST)
      booleanQuery.add(new TermQuery(new Term(RepositoryField, repository)), BooleanClause.Occur.MUST)
      booleanQuery.add(new TermQuery(new Term(IndexedObjectIdField, objectId)), BooleanClause.Occur.MUST)
      val result = indexSearcher.search(booleanQuery, null, 1).scoreDocs

      result.length == 1
    }
  }

  private def updateIndexFiles(owner: String, repository: String, git: Git) = {
    val directory = FSDirectory.open(getIndexDir.toPath)
    val config = new IndexWriterConfig(new CJKAnalyzer())
    config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND)
    val booleanQuery = new BooleanQuery()
    booleanQuery.add(new TermQuery(new Term(OwnerField, owner)), BooleanClause.Occur.MUST)
    booleanQuery.add(new TermQuery(new Term(RepositoryField, repository)), BooleanClause.Occur.MUST)
    val writer = new IndexWriter(directory, config)
    writer.deleteDocuments(booleanQuery)

    val revWalk   = new RevWalk(git.getRepository)
    val objectId  = git.getRepository.resolve("HEAD")
    val revCommit = revWalk.parseCommit(objectId)
    val treeWalk  = new TreeWalk(git.getRepository)
    treeWalk.setRecursive(true)
    treeWalk.addTree(revCommit.getTree)

    while (treeWalk.next()) {
      val mode = treeWalk.getFileMode(0)
      if(mode == FileMode.REGULAR_FILE || mode == FileMode.EXECUTABLE_FILE){
        val tmpId = treeWalk.getObjectId(0)
        JGitUtil.getContentFromId(git, tmpId, false).foreach { bytes =>
          if(FileUtil.isText(bytes)){
            val content = StringUtil.convertFromByteArray(bytes)
            val path = treeWalk.getPathString

            val doc = new Document()
            doc.add(new StringField(OwnerField, owner, Field.Store.YES))
            doc.add(new StringField(RepositoryField, repository, Field.Store.YES))
            doc.add(new StringField(PathField, path, Field.Store.YES))
            doc.add(new TextField(ContentField, content, Field.Store.YES))
            writer.addDocument(doc)
          }
        }
      }
    }

    val doc = new Document()
    doc.add(new StringField(OwnerField, owner, Field.Store.YES))
    doc.add(new StringField(RepositoryField, repository, Field.Store.YES))
    doc.add(new StringField(IndexedObjectIdField, objectId.name(), Field.Store.YES))
    writer.addDocument(doc)

    treeWalk.release
    revWalk.release
    writer.close
    directory.close
  }

  private def searchIndexFiles(indexSearcher: IndexSearcher, owner: String, repository: String, query: String, page: Int): TopDocs = {
    var parsedQuery = new QueryParser(ContentField, new CJKAnalyzer()).parse(query)
    val booleanQuery = new BooleanQuery()
    booleanQuery.add(parsedQuery, BooleanClause.Occur.MUST)
    booleanQuery.add(new TermQuery(new Term(OwnerField, owner)), BooleanClause.Occur.MUST)
    booleanQuery.add(new TermQuery(new Term(RepositoryField, repository)), BooleanClause.Occur.MUST)

    withIndexSearcher { indexSearcher =>
      indexSearcher.search(booleanQuery, CodeLimit * page)
    }
  }

  private def summarizeSearchResults(indexSearcher: IndexSearcher, docs: TopDocs, git: Git, query: String, page: Int): List[FileSearchResult] = {
    val hits = docs.scoreDocs

    if(hits.length == 0){
      List.empty[FileSearchResult]
    } else {
      hits.drop((page - 1) * CodeLimit).take(CodeLimit).map { case hit =>
        val doc = indexSearcher.doc(hit.doc)
        val path = doc.get(PathField)
        val text = doc.get(ContentField)
        val revWalk = new RevWalk(git.getRepository)
        val revCommit = revWalk.parseCommit(git.getRepository.resolve("HEAD"))
        val lastModified = JGitUtil.getLastModifiedCommit(git, revCommit, path).getCommitterIdent.getWhen

        val (highlightText, lineNumber) = getHighlightText(text, query)
        FileSearchResult(
          path,
          lastModified,
          highlightText,
          lineNumber)
      }.toList
    }
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
