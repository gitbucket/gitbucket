package gitbucket.core.api

import java.util.Date

import gitbucket.core.model.Account

case class ApiGroup(login: String, description: Option[String], created_at: Date) {
  val id = 0 // dummy id
  val url = ApiPath(s"/api/v3/orgs/${login}")
  val html_url = ApiPath(s"/${login}")
  val avatar_url = ApiPath(s"/${login}/_avatar")
}

object ApiGroup {
  def apply(group: Account): ApiGroup = ApiGroup(
    login = group.userName,
    description = group.description,
    created_at = group.registeredDate
  )
}
