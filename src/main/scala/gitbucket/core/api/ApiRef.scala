package gitbucket.core.api

case class ApiObject(sha: String)

case class ApiRef(ref: String, `object`: ApiObject)
