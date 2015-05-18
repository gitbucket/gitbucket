package gitbucket.core.api

import gitbucket.core.model.IssueComment
import gitbucket.core.util.RepositoryName

import java.util.Date


/**
 * https://developer.github.com/v3/issues/comments/
 */
case class ApiComment(
  id: Int,
  user: ApiUser,
  body: String,
  created_at: Date,
  updated_at: Date)(repositoryName: RepositoryName, issueId: Int){
  val html_url = ApiPath(s"/${repositoryName.fullName}/issues/${issueId}#comment-${id}")
}

object ApiComment{
  def apply(comment: IssueComment, repositoryName: RepositoryName, issueId: Int, user: ApiUser): ApiComment =
    ApiComment(
      id = comment.commentId,
      user = user,
      body = comment.content,
      created_at = comment.registeredDate,
      updated_at = comment.updatedDate)(repositoryName, issueId)
}
