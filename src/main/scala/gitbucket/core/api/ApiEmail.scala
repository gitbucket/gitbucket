package gitbucket.core.api
import gitbucket.core.model.Account

case class ApiEmail(
  email: String,
  primary: Boolean
) {
  val verified = true
  val visibility = "public"
}

object ApiEmail {
  def fromMailAddresses(primaryMailAddress: String, extraMailAddresses: List[String]): List[ApiEmail] = {
    ApiEmail(primaryMailAddress, true) :: extraMailAddresses.map { ApiEmail(_, false) }
  }
}
