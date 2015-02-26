package api

import model.Account
import model.CommitStatus
import model.CommitState

/**
 * https://developer.github.com/v3/repos/statuses/#get-the-combined-status-for-a-specific-ref
 */
case class ApiCombinedCommitStatus(
  state: String,
  sha: String,
  total_count: Int,
  statuses: Iterable[ApiCommitStatus],
  repository: ApiRepository){
  // val commit_url = ApiPath(s"/api/v3/repos/${repository.full_name}/${sha}")
  val url = ApiPath(s"/api/v3/repos/${repository.full_name}/commits/${sha}/status")
}
object ApiCombinedCommitStatus {
  def apply(sha:String, statuses: Iterable[(CommitStatus, Account)], repository:ApiRepository): ApiCombinedCommitStatus = ApiCombinedCommitStatus(
    state      = CommitState.combine(statuses.map(_._1.state).toSet).name,
    sha        = sha,
    total_count= statuses.size,
    statuses   = statuses.map{ case (s, a)=> ApiCommitStatus(s, ApiUser(a)) },
    repository = repository)
}
