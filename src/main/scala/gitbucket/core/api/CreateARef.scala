package gitbucket.core.api

/**
 * https://docs.github.com/en/free-pro-team@latest/rest/reference/git#create-a-reference
 * api form
 */
case class CreateARef(ref: String, sha: String)
