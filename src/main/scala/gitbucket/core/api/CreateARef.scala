package gitbucket.core.api

/**
 * https://developer.github.com/v3/git/refs/#create-a-reference
 * api form
 */
case class CreateARef(ref: String, sha: String)
