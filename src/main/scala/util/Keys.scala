package util

/**
 * Define key strings for request attributes, session attributes or flash attributes..
 */
object Keys {

  object Session {

    /**
     * Session key for the logged in account information.
     */
    val LoginAccount = "LOGIN_ACCOUNT"

    /**
     * Session key for the redirect URL.
     */
    val Redirect = "REDIRECT"

    /**
     * Session key for the issue search condition in dashboard.
     */
    val DashboardIssues = "dashboard/issues"

    /**
     * Session key for the pull request search condition in dashboard.
     */
    val DashboardPulls = "dashboard/pulls"

    /**
     * Generate session key for the issue search condition.
     */
    def Issues(owner: String, name: String) = s"${owner}/${name}/issues"

    /**
     * Generate session key for the pull request search condition.
     */
    def Pulls(owner: String, name: String) = s"${owner}/${name}/pulls"

    /**
     * Generate session key for the upload filename.
     */
    def Upload(fileId: String) = s"upload_${fileId}"

  }

}
