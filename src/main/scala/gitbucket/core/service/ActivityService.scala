package gitbucket.core.service

import gitbucket.core.model.Activity
import gitbucket.core.model.Profile._
import gitbucket.core.util.JGitUtil
import profile.simple._

trait ActivityService {

  def deleteOldActivities(limit: Int)(implicit s: Session): Int = {
    Activities.map(_.activityId).sortBy(_ desc).drop(limit).firstOption.map { id =>
      Activities.filter(_.activityId <= id.bind).delete
    } getOrElse 0
  }

  def getActivitiesByUser(activityUserName: String, isPublic: Boolean)(implicit s: Session): List[Activity] =
    Activities
      .innerJoin(Repositories).on((t1, t2) => t1.byRepository(t2.userName, t2.repositoryName))
      .filter { case (t1, t2) =>
        if(isPublic){
          (t1.activityUserName === activityUserName.bind) && (t2.isPrivate === false.bind)
        } else {
          (t1.activityUserName === activityUserName.bind)
        }
      }
      .sortBy { case (t1, t2) => t1.activityId desc }
      .map    { case (t1, t2) => t1 }
      .take(30)
      .list

  def getRecentActivities()(implicit s: Session): List[Activity] =
    Activities
      .innerJoin(Repositories).on((t1, t2) => t1.byRepository(t2.userName, t2.repositoryName))
      .filter { case (t1, t2) =>  t2.isPrivate === false.bind }
      .sortBy { case (t1, t2) => t1.activityId desc }
      .map    { case (t1, t2) => t1 }
      .take(30)
      .list

  def getRecentActivitiesByOwners(owners : Set[String])(implicit s: Session): List[Activity] =
    Activities
      .innerJoin(Repositories).on((t1, t2) => t1.byRepository(t2.userName, t2.repositoryName))
      .filter { case (t1, t2) => (t2.isPrivate === false.bind) || (t2.userName inSetBind owners) }
      .sortBy { case (t1, t2) => t1.activityId desc }
      .map    { case (t1, t2) => t1 }
      .take(30)
      .list

  def recordCreateRepositoryActivity(userName: String, repositoryName: String, activityUserName: String)
                                    (implicit s: Session): Unit =
    Activities insert Activity(userName, repositoryName, activityUserName,
      "create_repository",
      s"[user:${activityUserName}] created [repo:${userName}/${repositoryName}]",
      None,
      currentDate)

  def recordCreateIssueActivity(userName: String, repositoryName: String, activityUserName: String, issueId: Int, title: String)
                               (implicit s: Session): Unit =
    Activities insert Activity(userName, repositoryName, activityUserName,
      "open_issue",
      s"[user:${activityUserName}] opened issue [issue:${userName}/${repositoryName}#${issueId}]",
      Some(title), 
      currentDate)

  def recordCloseIssueActivity(userName: String, repositoryName: String, activityUserName: String, issueId: Int, title: String)
                              (implicit s: Session): Unit =
    Activities insert Activity(userName, repositoryName, activityUserName,
      "close_issue",
      s"[user:${activityUserName}] closed issue [issue:${userName}/${repositoryName}#${issueId}]",
      Some(title),
      currentDate)

  def recordClosePullRequestActivity(userName: String, repositoryName: String, activityUserName: String, issueId: Int, title: String)
                                    (implicit s: Session): Unit =
    Activities insert Activity(userName, repositoryName, activityUserName,
      "close_issue",
      s"[user:${activityUserName}] closed pull request [pullreq:${userName}/${repositoryName}#${issueId}]",
      Some(title),
      currentDate)

  def recordReopenIssueActivity(userName: String, repositoryName: String, activityUserName: String, issueId: Int, title: String)
                               (implicit s: Session): Unit =
    Activities insert Activity(userName, repositoryName, activityUserName,
      "reopen_issue",
      s"[user:${activityUserName}] reopened issue [issue:${userName}/${repositoryName}#${issueId}]",
      Some(title),
      currentDate)

  def recordCommentIssueActivity(userName: String, repositoryName: String, activityUserName: String, issueId: Int, comment: String)
                                (implicit s: Session): Unit =
    Activities insert Activity(userName, repositoryName, activityUserName,
      "comment_issue",
      s"[user:${activityUserName}] commented on issue [issue:${userName}/${repositoryName}#${issueId}]",
      Some(cut(comment, 200)),
      currentDate)

  def recordCommentPullRequestActivity(userName: String, repositoryName: String, activityUserName: String, issueId: Int, comment: String)
                                      (implicit s: Session): Unit =
    Activities insert Activity(userName, repositoryName, activityUserName,
      "comment_issue",
      s"[user:${activityUserName}] commented on pull request [pullreq:${userName}/${repositoryName}#${issueId}]",
      Some(cut(comment, 200)),
      currentDate)

  def recordCommentCommitActivity(userName: String, repositoryName: String, activityUserName: String, commitId: String, comment: String)
                                 (implicit s: Session): Unit =
    Activities insert Activity(userName, repositoryName, activityUserName,
      "comment_commit",
      s"[user:${activityUserName}] commented on commit [commit:${userName}/${repositoryName}@${commitId}]",
      Some(cut(comment, 200)),
      currentDate
    )

  def recordCreateWikiPageActivity(userName: String, repositoryName: String, activityUserName: String, pageName: String)
                                  (implicit s: Session): Unit =
    Activities insert Activity(userName, repositoryName, activityUserName,
      "create_wiki",
      s"[user:${activityUserName}] created the [repo:${userName}/${repositoryName}] wiki",
      Some(pageName),
      currentDate)

  def recordEditWikiPageActivity(userName: String, repositoryName: String, activityUserName: String, pageName: String, commitId: String)
                                (implicit s: Session): Unit =
    Activities insert Activity(userName, repositoryName, activityUserName,
      "edit_wiki",
      s"[user:${activityUserName}] edited the [repo:${userName}/${repositoryName}] wiki",
      Some(pageName + ":" + commitId),
      currentDate)

  def recordPushActivity(userName: String, repositoryName: String, activityUserName: String,
      branchName: String, commits: List[JGitUtil.CommitInfo])(implicit s: Session): Unit =
    Activities insert Activity(userName, repositoryName, activityUserName,
      "push",
      s"[user:${activityUserName}] pushed to [branch:${userName}/${repositoryName}#${branchName}] at [repo:${userName}/${repositoryName}]",
      Some(commits.map { commit => commit.id + ":" + commit.shortMessage }.mkString("\n")),
      currentDate)

  def recordCreateTagActivity(userName: String, repositoryName: String, activityUserName: String, 
      tagName: String, commits: List[JGitUtil.CommitInfo])(implicit s: Session): Unit =
    Activities insert Activity(userName, repositoryName, activityUserName,
      "create_tag",
      s"[user:${activityUserName}] created tag [tag:${userName}/${repositoryName}#${tagName}] at [repo:${userName}/${repositoryName}]",
      None,
      currentDate)

  def recordDeleteTagActivity(userName: String, repositoryName: String, activityUserName: String,
                              tagName: String, commits: List[JGitUtil.CommitInfo])(implicit s: Session): Unit =
    Activities insert Activity(userName, repositoryName, activityUserName,
      "delete_tag",
      s"[user:${activityUserName}] deleted tag ${tagName} at [repo:${userName}/${repositoryName}]",
      None,
      currentDate)

  def recordCreateBranchActivity(userName: String, repositoryName: String, activityUserName: String, branchName: String)
                                (implicit s: Session): Unit =
    Activities insert Activity(userName, repositoryName, activityUserName,
      "create_branch",
      s"[user:${activityUserName}] created branch [branch:${userName}/${repositoryName}#${branchName}] at [repo:${userName}/${repositoryName}]",
      None,
      currentDate)

  def recordDeleteBranchActivity(userName: String, repositoryName: String, activityUserName: String, branchName: String)
                                (implicit s: Session): Unit =
    Activities insert Activity(userName, repositoryName, activityUserName,
      "delete_branch",
      s"[user:${activityUserName}] deleted branch ${branchName} at [repo:${userName}/${repositoryName}]",
      None,
      currentDate)

  def recordForkActivity(userName: String, repositoryName: String, activityUserName: String, forkedUserName: String)(implicit s: Session): Unit = 
    Activities insert Activity(userName, repositoryName, activityUserName,
      "fork",
      s"[user:${activityUserName}] forked [repo:${userName}/${repositoryName}] to [repo:${forkedUserName}/${repositoryName}]",
      None,
      currentDate)

  def recordPullRequestActivity(userName: String, repositoryName: String, activityUserName: String, issueId: Int, title: String)
                               (implicit s: Session): Unit =
    Activities insert Activity(userName, repositoryName, activityUserName,
      "open_pullreq",
      s"[user:${activityUserName}] opened pull request [pullreq:${userName}/${repositoryName}#${issueId}]",
      Some(title),
      currentDate)

  def recordMergeActivity(userName: String, repositoryName: String, activityUserName: String, issueId: Int, message: String)
                         (implicit s: Session): Unit =
    Activities insert Activity(userName, repositoryName, activityUserName,
      "merge_pullreq",
      s"[user:${activityUserName}] merged pull request [pullreq:${userName}/${repositoryName}#${issueId}]",
      Some(message),
      currentDate)

  private def cut(value: String, length: Int): String =
    if(value.length > length) value.substring(0, length) + "..." else value
}
