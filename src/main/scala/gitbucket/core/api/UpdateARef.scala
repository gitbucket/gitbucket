package gitbucket.core.api

/**
 * https://developer.github.com/v3/git/refs/#update-a-reference
 * api form
 */
case class UpdateARef(sha: String, force: Boolean)
