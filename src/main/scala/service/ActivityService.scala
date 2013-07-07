package service

import model._
import Activities._
import scala.slick.driver.H2Driver.simple._
import Database.threadLocalSession

trait ActivityService {

  def getActivitiesByUser(activityUserName: String, isPublic: Boolean): List[Activity] = {
    val q = Query(Activities)
      .innerJoin(Repositories).on((t1, t2) => t1.byRepository(t2.userName, t2.repositoryName))

    (if(isPublic){
      q filter { case (t1, t2) => (t1.activityUserName is activityUserName.bind) && (t2.isPrivate is false.bind) }
    } else {
      q filter { case (t1, t2) => t1.activityUserName is activityUserName.bind }
    })
    .sortBy { case (t1, t2) => t1.activityId desc }
    .map    { case (t1, t2) => t1 }
    .take(30)
    .list
  }
  
  def getRecentActivities(): List[Activity] =
    Query(Activities)
      .innerJoin(Repositories).on((t1, t2) => t1.byRepository(t2.userName, t2.repositoryName))
      .filter { case (t1, t2) => t2.isPrivate is false.bind }
      .sortBy { case (t1, t2) => t1.activityId desc }
      .map    { case (t1, t2) => t1 }
      .take(30)
      .list
  

  def recordCreateRepositoryActivity(userName: String, repositoryName: String, activityUserName: String): Unit =
    Activities.autoInc insert(userName, repositoryName, activityUserName,
      "create_repository",
      s"[user:${activityUserName}] created [repo:${userName}/${repositoryName}]",
      None,
      currentDate)

  def recordCreateIssueActivity(userName: String, repositoryName: String, activityUserName: String, issueId: Int, title: String): Unit =
    Activities.autoInc insert(userName, repositoryName, activityUserName,
      "open_issue",
      s"[user:${activityUserName}] opened issue [issue:${userName}/${repositoryName}#${issueId}]",
      Some(title), 
      currentDate)

  def recordCloseIssueActivity(userName: String, repositoryName: String, activityUserName: String, issueId: Int, title: String): Unit =
    Activities.autoInc insert(userName, repositoryName, activityUserName,
      "close_issue",
      s"[user:${activityUserName}] closed issue [issue:${userName}/${repositoryName}#${issueId}]",
      Some(title),
      currentDate)

  def recordReopenIssueActivity(userName: String, repositoryName: String, activityUserName: String, issueId: Int, title: String): Unit =
    Activities.autoInc insert(userName, repositoryName, activityUserName,
      "reopen_issue",
      s"[user:${activityUserName}] closed reopened [issue:${userName}/${repositoryName}#${issueId}]",
      Some(title),
      currentDate)

  def recordCommentIssueActivity(userName: String, repositoryName: String, activityUserName: String, issueId: Int, comment: String): Unit =
    Activities.autoInc insert(userName, repositoryName, activityUserName,
      "comment_issue",
      s"[user:${activityUserName}] commented on issue [issue:${userName}/${repositoryName}#${issueId}]",
      Some(cut(comment, 200)),
      currentDate)
  
  def recordCreateWikiPageActivity(userName: String, repositoryName: String, activityUserName: String, pageName: String) =
    Activities.autoInc insert(userName, repositoryName, activityUserName,
      "create_wiki",
      s"[user:${activityUserName}] created the [repo:${userName}/${repositoryName}] wiki",
      Some(pageName),
      currentDate)
  
  def recordEditWikiPageActivity(userName: String, repositoryName: String, activityUserName: String, pageName: String) =
    Activities.autoInc insert(userName, repositoryName, activityUserName,
      "edit_wiki",
      s"[user:${activityUserName}] edited the [repo:${userName}/${repositoryName}] wiki",
      Some(pageName),
      currentDate)
  
  def recordPushActivity(userName: String, repositoryName: String, activityUserName: String, 
      branchName: String, commits: List[util.JGitUtil.CommitInfo]) =
    Activities.autoInc insert(userName, repositoryName, activityUserName,
      "push",
      s"[user:${activityUserName}] pushed to [branch:${userName}/${repositoryName}#${branchName}] at [repo:${userName}/${repositoryName}]",
      Some(commits.map { commit => commit.id + ":" + commit.shortMessage }.mkString("\n")),
      currentDate)
  
  def recordCreateTagActivity(userName: String, repositoryName: String, activityUserName: String, 
      tagName: String, commits: List[util.JGitUtil.CommitInfo]) =
    Activities.autoInc insert(userName, repositoryName, activityUserName,
      "create_tag",
      s"[user:${activityUserName}] created tag [tag:${userName}/${repositoryName}#${tagName}] at [repo:${userName}/${repositoryName}]",
      None,
      currentDate)
  
  def recordCreateBranchActivity(userName: String, repositoryName: String, activityUserName: String, branchName: String) =
    Activities.autoInc insert(userName, repositoryName, activityUserName,
      "create_tag",
      s"[user:${activityUserName}] created branch [tag:${userName}/${repositoryName}#${branchName}] at [repo:${userName}/${repositoryName}]",
      None,
      currentDate)
  
  def insertCommitId(userName: String, repositoryName: String, commitId: String) = {
    CommitLog insert (userName, repositoryName, commitId)
  }
  
  def existsCommitId(userName: String, repositoryName: String, commitId: String): Boolean =
    Query(CommitLog).filter(_.byPrimaryKey(userName, repositoryName, commitId)).firstOption.isDefined
  
  private def cut(value: String, length: Int): String = 
    if(value.length > length) value.substring(0, length) + "..." else value
}
