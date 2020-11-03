package gitbucket.core.api

/**
 * https://docs.github.com/en/free-pro-team@latest/rest/reference/git#update-a-reference
 * api form
 */
case class UpdateARef(sha: String, force: Boolean)
