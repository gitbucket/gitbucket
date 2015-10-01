package gitbucket.core.api

case class IssueOrPullRequest(isPullRequest:Boolean){
  val path = if(isPullRequest){ "pull" }else{ "issues" }
}