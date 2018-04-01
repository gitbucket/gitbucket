package gitbucket.core.api

import gitbucket.core.model.Account

case class ApiPusher(name: String, email: String)

object ApiPusher {
  def apply(user: Account): ApiPusher = ApiPusher(name = user.userName, email = user.mailAddress)
}
