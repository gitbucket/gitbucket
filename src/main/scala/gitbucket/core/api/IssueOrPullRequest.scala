package gitbucket.core.api

case class IssueOrPullRequest(isPullRequest:Boolean){
  val (html, api) = if(isPullRequest){ ("pull","pulls") }else{ ("issues","issues") }
}