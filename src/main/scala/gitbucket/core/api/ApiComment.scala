package gitbucket.core.api

import gitbucket.core.model.IssueComment

import java.util.Date


/**
 * https://developer.github.com/v3/issues/comments/
 */
case class ApiComment(
  id: Int,
  user: ApiUser,
  body: String,
  created_at: Date,
  updated_at: Date)

object ApiComment{
  def apply(comment: IssueComment, user: ApiUser): ApiComment =
    ApiComment(
      id = comment.commentId,
      user = user,
      body = comment.content,
      created_at = comment.registeredDate,
      updated_at = comment.updatedDate)
}
