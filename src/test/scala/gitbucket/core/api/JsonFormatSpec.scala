package gitbucket.core.api

import org.json4s.Formats
import org.json4s.jackson.JsonMethods
import org.json4s.jvalue2extractable
import org.scalatest.funsuite.AnyFunSuite

class JsonFormatSpec extends AnyFunSuite {
  import ApiSpecModels._
  implicit val format: Formats = JsonFormat.jsonFormats

  private def expected(json: String) = json.replaceAll("\n", "")
  def normalizeJson(json: String) = {
    org.json4s.jackson.parseJson(json)
  }
  def assertEqualJson(actual: String, expected: String) = {
    assert(normalizeJson(actual) == normalizeJson(expected))
  }

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
  test("serialize apiBranchProtection") {
    assert(JsonFormat(apiBranchProtectionOutput) == expected(jsonBranchProtectionOutput))
  }
  test("deserialize apiBranchProtection") {
    assert(JsonMethods.parse(jsonBranchProtectionInput).extract[ApiBranchProtection] == apiBranchProtectionInput)
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
  test("apiRefHead") {
    assertEqualJson(JsonFormat(apiRefHeadsMaster)(gitHubContext), jsonRefHeadsMain)
  }
  test("apiRefTag") {
    assertEqualJson(JsonFormat(apiRefTag)(gitHubContext), jsonRefTag)
  }
  test("apiReleaseAsset") {
    assert(JsonFormat(apiReleaseAsset) == expected(jsonReleaseAsset))
  }
  test("apiRelease") {
    assert(JsonFormat(apiRelease) == expected(jsonRelease))
  }
}
