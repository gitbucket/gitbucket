package gitbucket.core.api

/**
 * Path for API url.
 * If set path '/repos/aa/bb' then, expand 'http://server:port/repos/aa/bb' when converted to json.
 */
case class ApiPath(path: String)

/**
 * Path for git repository via SSH.
 * If set path '/aa/bb.git' then, expand 'git@server:port/aa/bb.git' when converted to json.
 */
case class SshPath(path: String)
