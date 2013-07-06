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

  def recordCreateRepository(userName: String, repositoryName: String, activityUserName: String): Unit = {
    Activities.autoInc insert(userName, repositoryName, activityUserName,
      "[[%s]] created [[%s/%s]]".format(activityUserName, userName, repositoryName),
      currentDate)
  }

}
