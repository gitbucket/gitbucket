package gitbucket.core.model

case class Activity(
  userName: String,
  repositoryName: String,
  activityUserName: String,
  activityType: String,
  message: String,
  additionalInfo: Option[String],
  activityDate: java.util.Date,
  activityId: String
)
