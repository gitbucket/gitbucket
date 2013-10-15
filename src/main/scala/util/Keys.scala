package util

/**
 * Define key strings for request attributes, session attributes or flash attributes.
 */
object Keys {

  /**
   * Define session keys.
   */
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

  /**
   * Define request keys.
   */
  object Request {

    /**
     * Request key for the Ajax request flag.
     */
    val Ajax = "AJAX"

    /**
     * Request key for the username which is used during Git repository access.
     */
    val UserName = "USER_NAME"

    /**
     * Generate request key for the request cache.
     */
    def Cache(key: String) = s"cache.${key}"

  }

}
