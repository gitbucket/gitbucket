package gitbucket.core.api

import java.util.Date

case class CreateAMilestone(
  title: String,
  state: String = "open",
  description: Option[String],
  due_on: Option[Date]
) {
  def isValid: Boolean = {
    title.length <= 100 && title.matches("[a-zA-Z0-9\\-\\+_.]+")
  }
}
