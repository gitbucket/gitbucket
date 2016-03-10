package gitbucket.core.service

import gitbucket.core.model._
import gitbucket.core.model.Profile._
import gitbucket.core.util.JGitUtil
import profile.simple._

import gitbucket.core.servlet.Database._
import io.getquill._

trait ActivityService {

  def deleteOldActivities(limit: Int): Int =
    db.run (quote { (limit: Int) =>
      query[Activity].map(_.activityId).sortBy(x => x)(Ord.desc).drop(limit)
    })(limit).headOption.map { activityId =>
      db.run (
        quote { (activityId: Int) => query[Activity].filter(_.activityId <= activityId).delete }
      )(activityId)
    } getOrElse 0

  def getActivitiesByUser(activityUserName: String, isPublic: Boolean): List[Activity] =
    db.run(quote { (activityUserName: String, isPublic: Boolean) =>
      query[Activity].join(query[Repository]).on((a, r) => a.userName == r.userName && a.repositoryName == r.repositoryName)
        .filter { case (a, r) =>
          if(isPublic){
            a.activityUserName == activityUserName
          } else {
            a.activityUserName == activityUserName && r.`private` == false
          }
        }
        .sortBy { case (a, r) => a.activityId }(Ord.desc)
        .map    { case (a, r) => a }
        .take(30)
    })(activityUserName, isPublic)

  def getRecentActivities(): List[Activity] =
    db.run(quote {
      query[Activity].join(query[Repository]).on((a, r) => a.userName == r.userName && a.repositoryName == r.repositoryName)
        .filter { case (a, r) => r.`private` == false}
        .sortBy { case (a, r) => a.activityId }(Ord.desc)
        .map    { case (a, r) => a }
        .take(30)
    })

  def getRecentActivitiesByOwners(owners : Set[String]): List[Activity] =
    db.run(quote { (owners: Set[String]) =>
      query[Activity].join(query[Repository]).on((a, r) => a.userName == r.userName && a.repositoryName == r.repositoryName)
        .filter { case (a, r) => r.`private` == false || owners.contains(r.userName) }
        .sortBy { case (a, r) => a.activityId }(Ord.desc)
        .map    { case (a, r) => a }
        .take(30)
    })(owners)

  def recordCreateRepositoryActivity(userName: String, repositoryName: String, activityUserName: String): Unit =
    insertActivity(userName, repositoryName, activityUserName,
      "create_repository",
      s"[user:${activityUserName}] created [repo:${userName}/${repositoryName}]",
      None)

  def recordCreateIssueActivity(userName: String, repositoryName: String, activityUserName: String, issueId: Int, title: String): Unit =
    insertActivity(userName, repositoryName, activityUserName,
      "open_issue",
      s"[user:${activityUserName}] opened issue [issue:${userName}/${repositoryName}#${issueId}]",
      Some(title))

  def recordCloseIssueActivity(userName: String, repositoryName: String, activityUserName: String, issueId: Int, title: String): Unit =
    insertActivity(userName, repositoryName, activityUserName,
      "close_issue",
      s"[user:${activityUserName}] closed issue [issue:${userName}/${repositoryName}#${issueId}]",
      Some(title))

  def recordClosePullRequestActivity(userName: String, repositoryName: String, activityUserName: String, issueId: Int, title: String): Unit =
    insertActivity(userName, repositoryName, activityUserName,
      "close_issue",
      s"[user:${activityUserName}] closed pull request [pullreq:${userName}/${repositoryName}#${issueId}]",
      Some(title))

  def recordReopenIssueActivity(userName: String, repositoryName: String, activityUserName: String, issueId: Int, title: String): Unit =
    insertActivity(userName, repositoryName, activityUserName,
      "reopen_issue",
      s"[user:${activityUserName}] reopened issue [issue:${userName}/${repositoryName}#${issueId}]",
      Some(title))

  def recordCommentIssueActivity(userName: String, repositoryName: String, activityUserName: String, issueId: Int, comment: String): Unit =
    insertActivity(userName, repositoryName, activityUserName,
      "comment_issue",
      s"[user:${activityUserName}] commented on issue [issue:${userName}/${repositoryName}#${issueId}]",
      Some(cut(comment, 200)))

  def recordCommentPullRequestActivity(userName: String, repositoryName: String, activityUserName: String, issueId: Int, comment: String): Unit =
    insertActivity(userName, repositoryName, activityUserName,
      "comment_issue",
      s"[user:${activityUserName}] commented on pull request [pullreq:${userName}/${repositoryName}#${issueId}]",
      Some(cut(comment, 200)))

  def recordCommentCommitActivity(userName: String, repositoryName: String, activityUserName: String, commitId: String, comment: String): Unit =
    insertActivity(userName, repositoryName, activityUserName,
      "comment_commit",
      s"[user:${activityUserName}] commented on commit [commit:${userName}/${repositoryName}@${commitId}]",
      Some(cut(comment, 200)))

  def recordCreateWikiPageActivity(userName: String, repositoryName: String, activityUserName: String, pageName: String): Unit =
    insertActivity(userName, repositoryName, activityUserName,
      "create_wiki",
      s"[user:${activityUserName}] created the [repo:${userName}/${repositoryName}] wiki",
      Some(pageName))

  def recordEditWikiPageActivity(userName: String, repositoryName: String, activityUserName: String, pageName: String, commitId: String): Unit =
    insertActivity(userName, repositoryName, activityUserName,
      "edit_wiki",
      s"[user:${activityUserName}] edited the [repo:${userName}/${repositoryName}] wiki",
      Some(pageName + ":" + commitId))

  def recordPushActivity(userName: String, repositoryName: String, activityUserName: String,
      branchName: String, commits: List[JGitUtil.CommitInfo]): Unit =
    insertActivity(userName, repositoryName, activityUserName,
      "push",
      s"[user:${activityUserName}] pushed to [branch:${userName}/${repositoryName}#${branchName}] at [repo:${userName}/${repositoryName}]",
      Some(commits.map { commit => commit.id + ":" + commit.shortMessage }.mkString("\n")))

  def recordCreateTagActivity(userName: String, repositoryName: String, activityUserName: String, 
      tagName: String, commits: List[JGitUtil.CommitInfo]): Unit =
    insertActivity(userName, repositoryName, activityUserName,
      "create_tag",
      s"[user:${activityUserName}] created tag [tag:${userName}/${repositoryName}#${tagName}] at [repo:${userName}/${repositoryName}]",
      None)

  def recordDeleteTagActivity(userName: String, repositoryName: String, activityUserName: String,
                              tagName: String, commits: List[JGitUtil.CommitInfo]): Unit =
    insertActivity(userName, repositoryName, activityUserName,
      "delete_tag",
      s"[user:${activityUserName}] deleted tag ${tagName} at [repo:${userName}/${repositoryName}]",
      None)

  def recordCreateBranchActivity(userName: String, repositoryName: String, activityUserName: String, branchName: String): Unit =
    insertActivity(userName, repositoryName, activityUserName,
      "create_branch",
      s"[user:${activityUserName}] created branch [branch:${userName}/${repositoryName}#${branchName}] at [repo:${userName}/${repositoryName}]",
      None)

  def recordDeleteBranchActivity(userName: String, repositoryName: String, activityUserName: String, branchName: String): Unit =
    insertActivity(userName, repositoryName, activityUserName,
      "delete_branch",
      s"[user:${activityUserName}] deleted branch ${branchName} at [repo:${userName}/${repositoryName}]",
      None)

  def recordForkActivity(userName: String, repositoryName: String, activityUserName: String, forkedUserName: String): Unit =
    insertActivity(userName, repositoryName, activityUserName,
      "fork",
      s"[user:${activityUserName}] forked [repo:${userName}/${repositoryName}] to [repo:${forkedUserName}/${repositoryName}]",
      None)

  def recordPullRequestActivity(userName: String, repositoryName: String, activityUserName: String, issueId: Int, title: String): Unit =
    insertActivity(userName, repositoryName, activityUserName,
      "open_pullreq",
      s"[user:${activityUserName}] opened pull request [pullreq:${userName}/${repositoryName}#${issueId}]",
      Some(title))

  def recordMergeActivity(userName: String, repositoryName: String, activityUserName: String, issueId: Int, message: String): Unit =
    insertActivity(userName, repositoryName, activityUserName,
      "merge_pullreq",
      s"[user:${activityUserName}] merged pull request [pullreq:${userName}/${repositoryName}#${issueId}]",
      Some(message))

  private def insertActivity(userName: String, repositoryName: String, activityUserName: String, activityType: String,
                             message: String, additionalInfo: Option[String]): Unit = {
    db.run(quote { query[Activity].insert })(Activity(
      userName         = userName,
      repositoryName   = repositoryName,
      activityUserName = activityUserName,
      activityType     = activityType,
      message          = message,
      additionalInfo   = additionalInfo,
      activityDate     = currentDate
    ))
  }

  private def cut(value: String, length: Int): String =
    if(value.length > length) value.substring(0, length) + "..." else value
}
