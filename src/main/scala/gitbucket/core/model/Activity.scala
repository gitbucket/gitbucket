package gitbucket.core.model

/**
 * ActivityComponent has been deprecated, but keep it for binary compatibility.
 */
@deprecated("ActivityComponent has been deprecated, but keep it for binary compatibility.", "4.34.0")
trait ActivityComponent extends TemplateComponent { self: Profile =>
  import profile.api._
  import self._

  lazy val Activities = TableQuery[Activities]

  class Activities(tag: Tag) extends Table[Activity](tag, "ACTIVITY") with BasicTemplate {
    def * = ???
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
  activityId: String
)
