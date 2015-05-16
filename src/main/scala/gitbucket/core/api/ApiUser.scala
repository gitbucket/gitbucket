package gitbucket.core.api

import gitbucket.core.model.Account

import java.util.Date


case class ApiUser(
  login: String,
  email: String,
  `type`: String,
  site_admin: Boolean,
  created_at: Date) {
  val url                 = ApiPath(s"/api/v3/users/${login}")
  val html_url            = ApiPath(s"/${login}")
  // val followers_url       = ApiPath(s"/api/v3/users/${login}/followers")
  // val following_url       = ApiPath(s"/api/v3/users/${login}/following{/other_user}")
  // val gists_url           = ApiPath(s"/api/v3/users/${login}/gists{/gist_id}")
  // val starred_url         = ApiPath(s"/api/v3/users/${login}/starred{/owner}{/repo}")
  // val subscriptions_url   = ApiPath(s"/api/v3/users/${login}/subscriptions")
  // val organizations_url   = ApiPath(s"/api/v3/users/${login}/orgs")
  // val repos_url           = ApiPath(s"/api/v3/users/${login}/repos")
  // val events_url          = ApiPath(s"/api/v3/users/${login}/events{/privacy}")
  // val received_events_url = ApiPath(s"/api/v3/users/${login}/received_events")
}


object ApiUser{
  def apply(user: Account): ApiUser = ApiUser(
    login      = user.userName,
    email      = user.mailAddress,
    `type`     = if(user.isGroupAccount){ "Organization" }else{ "User" },
    site_admin = user.isAdmin,
    created_at = user.registeredDate
  )
}
