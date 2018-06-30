package gitbucket.core.api

import gitbucket.core.util.RepositoryName
import gitbucket.core.model.CommitComment

import java.util.Date

/**
 * https://developer.github.com/v3/activity/events/types/#pullrequestreviewcommentevent
 */
case class ApiPullRequestReviewComment(
  id: Int, // 29724692
  // "diff_hunk": "@@ -1 +1 @@\n-# public-repo",
  path: String, // "README.md",
  // "position": 1,
  // "original_position": 1,
  commit_id: String, // "0d1a26e67d8f5eaf1f6ba5c57fc3c7d91ac0fd1c",
  // "original_commit_id": "0d1a26e67d8f5eaf1f6ba5c57fc3c7d91ac0fd1c",
  user: ApiUser,
  body: String, // "Maybe you should use more emoji on this line.",
  created_at: Date, // "2015-05-05T23:40:27Z",
  updated_at: Date // "2015-05-05T23:40:27Z",
)(repositoryName: RepositoryName, issueId: Int)
    extends FieldSerializable {
  // "url": "https://api.github.com/repos/baxterthehacker/public-repo/pulls/comments/29724692",
  val url = ApiPath(s"/api/v3/repos/${repositoryName.fullName}/pulls/comments/${id}")
  // "html_url": "https://github.com/baxterthehacker/public-repo/pull/1#discussion_r29724692",
  val html_url = ApiPath(s"/${repositoryName.fullName}/pull/${issueId}#discussion_r${id}")
  // "pull_request_url": "https://api.github.com/repos/baxterthehacker/public-repo/pulls/1",
  val pull_request_url = ApiPath(s"/api/v3/repos/${repositoryName.fullName}/pulls/${issueId}")

  /*
    "_links": {
      "self": {
        "href": "https://api.github.com/repos/baxterthehacker/public-repo/pulls/comments/29724692"
      },
      "html": {
        "href": "https://github.com/baxterthehacker/public-repo/pull/1#discussion_r29724692"
      },
      "pull_request": {
        "href": "https://api.github.com/repos/baxterthehacker/public-repo/pulls/1"
      }
    }
   */
  val _links = Map(
    "self" -> Map("href" -> url),
    "html" -> Map("href" -> html_url),
    "pull_request" -> Map("href" -> pull_request_url)
  )
}

object ApiPullRequestReviewComment {
  def apply(
    comment: CommitComment,
    commentedUser: ApiUser,
    repositoryName: RepositoryName,
    issueId: Int
  ): ApiPullRequestReviewComment =
    new ApiPullRequestReviewComment(
      id = comment.commentId,
      path = comment.fileName.getOrElse(""),
      commit_id = comment.commitId,
      user = commentedUser,
      body = comment.content,
      created_at = comment.registeredDate,
      updated_at = comment.updatedDate
    )(repositoryName, issueId)
}
