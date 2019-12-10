package gitbucket.core.api

import org.scalatest.FunSuite

class JsonFormatSpec extends FunSuite {
  import ApiSpecModels._

  private def expected(json: String) = json.replaceAll("\n", "")

  test("apiUser") {
    assert(JsonFormat(apiUser) == expected(jsonUser))
  }
  test("apiRepository") {
    assert(JsonFormat(apiRepository) == expected(jsonRepository))
  }
  test("apiCommit") {
    assert(JsonFormat(apiCommit) == expected(jsonCommit(sha1)))
  }
  test("apiComment") {
    assert(JsonFormat(apiComment) == expected(jsonComment))
    assert(JsonFormat(apiCommentPR) == expected(jsonCommentPR))
  }
  test("apiCommitListItem") {
    assert(JsonFormat(apiCommitListItem) == expected(jsonCommitListItem))
  }
  test("apiCommitStatus") {
    assert(JsonFormat(apiCommitStatus) == expected(jsonCommitStatus))
  }
  test("apiCombinedCommitStatus") {
    assert(JsonFormat(apiCombinedCommitStatus) == expected(jsonCombinedCommitStatus))
  }
  test("apiLabel") {
    assert(JsonFormat(apiLabel) == expected(jsonLabel))
  }
  test("apiIssue") {
    assert(JsonFormat(apiIssue) == expected(jsonIssue))
    assert(JsonFormat(apiNotAssignedIssue) == expected(jsonNotAssignedIssue))
    assert(JsonFormat(apiIssuePR) == expected(jsonIssuePR))
  }
  test("apiPullRequest") {
    assert(JsonFormat(apiPullRequest) == expected(jsonPullRequest))
  }
  test("apiPullRequestReviewComment") {
    assert(JsonFormat(apiPullRequestReviewComment) == expected(jsonPullRequestReviewComment))
  }
  test("apiBranchProtection") {
    assert(JsonFormat(apiBranchProtection) == expected(jsonBranchProtection))
  }
  test("apiBranch") {
    assert(JsonFormat(apiBranch) == expected(jsonBranch))
    assert(JsonFormat(apiBranchForList) == expected(jsonBranchForList))
  }
  test("apiCommits") {
    assert(JsonFormat(apiCommits) == expected(jsonCommits))
  }
  test("apiContents") {
    assert(JsonFormat(apiContents) == expected(jsonContents))
  }
  test("apiEndPoint") {
    assert(JsonFormat(apiEndPoint) == expected(jsonEndPoint))
  }
  test("apiError") {
    assert(JsonFormat(apiError) == expected(jsonError))
  }
  test("apiGroup") {
    assert(JsonFormat(apiGroup) == expected(jsonGroup))
  }
  test("apiPlugin") {
    assert(JsonFormat(apiPlugin) == expected(jsonPlugin))
  }
  test("apiPusher") {
    assert(JsonFormat(apiPusher) == expected(jsonPusher))
  }
  test("apiRef") {
    assert(JsonFormat(apiRef) == expected(jsonRef))
  }
  test("apiReleaseAsset") {
    assert(JsonFormat(apiReleaseAsset) == expected(jsonReleaseAsset))
  }
  test("apiRelease") {
    assert(JsonFormat(apiRelease) == expected(jsonRelease))
  }
}
