package gitbucket.core.api

/**
 * https://docs.github.com/en/rest/repos/forks#create-a-fork
 * api form
 */
case class CreateAFork(organization: Option[String])
