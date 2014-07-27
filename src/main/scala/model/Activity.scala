package model

trait ActivityComponent extends TemplateComponent { self: Profile =>
  import profile.simple._
  import self._

  lazy val Activities = TableQuery[Activities]

  class Activities(tag: Tag) extends Table[Activity](tag, "ACTIVITY") with BasicTemplate {
    val activityId = column[Int]("ACTIVITY_ID", O AutoInc)
    val activityUserName = column[String]("ACTIVITY_USER_NAME")
    val activityType = column[String]("ACTIVITY_TYPE")
    val message = column[String]("MESSAGE")
    val additionalInfo = column[String]("ADDITIONAL_INFO")
    val activityDate = column[java.util.Date]("ACTIVITY_DATE")
    def * = (userName, repositoryName, activityUserName, activityType, message, additionalInfo.?, activityDate, activityId) <> (Activity.tupled, Activity.unapply)
  }
}

case class Activity(
  userName: String,
  repositoryName: String,
  activityUserName: String,
  activityType: String,
  message: String,
  additionalInfo: Option[String],
  activityDate: java.util.Date,
  activityId: Int = 0
)
