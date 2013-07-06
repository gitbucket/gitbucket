package model

import scala.slick.driver.H2Driver.simple._

object Activities extends Table[Activity]("ACTIVITY") with BasicTemplate with Functions {
  def activityId = column[Int]("ACTIVITY_ID", O AutoInc)
  def activityUserName = column[String]("ACTIVITY_USER_NAME")
  def message = column[String]("MESSAGE")
  def activityDate = column[java.util.Date]("ACTIVITY_DATE")
  def * = activityId ~ userName ~ repositoryName ~ activityUserName ~ message ~ activityDate <> (Activity, Activity.unapply _)
  def autoInc = userName ~ repositoryName ~ activityUserName ~ message ~ activityDate returning activityId
}

case class Activity(
  activityId: Int,
  userName: String,
  repositoryName: String,
  activityUserName: String,
  message: String,
  activityDate: java.util.Date
)
