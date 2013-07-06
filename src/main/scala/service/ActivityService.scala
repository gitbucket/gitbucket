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
    .list
  }

  def recordCreateRepositoryActivity(userName: String, repositoryName: String, activityUserName: String): Unit =
    Activities.autoInc insert(userName, repositoryName, activityUserName,
      "[[%s]] created [[%s/%s]]".format(activityUserName, userName, repositoryName),
      None, currentDate)

  def recordCreateIssueActivity(userName: String, repositoryName: String, activityUserName: String, issueId: Int, title: String): Unit =
    Activities.autoInc insert(userName, repositoryName, activityUserName,
      "[[%s]] opened issue [[%s/%s#%d]]".format(activityUserName, userName, repositoryName, issueId),
      Some(title), currentDate)

  def recordCloseIssueActivity(userName: String, repositoryName: String, activityUserName: String, issueId: Int, title: String): Unit =
    Activities.autoInc insert(userName, repositoryName, activityUserName,
      "[[%s]] closed issue [[%s/%s#%d]]".format(activityUserName, userName, repositoryName, issueId),
      Some(title), currentDate)

  def recordReopenIssueActivity(userName: String, repositoryName: String, activityUserName: String, issueId: Int, title: String): Unit =
    Activities.autoInc insert(userName, repositoryName, activityUserName,
      "[[%s]] closed reopened [[%s/%s#%d]]".format(activityUserName, userName, repositoryName, issueId),
      Some(title), currentDate)

  def recordCommentIssueActivity(userName: String, repositoryName: String, activityUserName: String, issueId: Int, comment: String): Unit =
    Activities.autoInc insert(userName, repositoryName, activityUserName,
      "[[%s]] commented on issue [[%s/%s#%d]]".format(activityUserName, userName, repositoryName, issueId),
      Some(comment), currentDate)
}
