package gitbucket.core.api

/**
 * path for api url. if set path '/repos/aa/bb' then, expand 'http://server:post/repos/aa/bb' when converted to json.
 */
case class ApiPath(path: String)
