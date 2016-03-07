package gitbucket.core.service

import gitbucket.core.model.WebHook
import org.scalatest.FunSuite


class WebHookServiceSpec extends FunSuite with ServiceSpecBase {
  lazy val service = new WebHookPullRequestService with AccountService with RepositoryService with PullRequestService with IssuesService

  test("WebHookPullRequestService.getPullRequestsByRequestForWebhook") { withTestDB { implicit session =>
    val user1 = generateNewUserWithDBRepository("user1","repo1")
    val user2 = generateNewUserWithDBRepository("user2","repo2")
    val user3 = generateNewUserWithDBRepository("user3","repo3")
    val issueUser = user("root")
    val (issue1, pullreq1) = generateNewPullRequest("user1/repo1/master1", "user2/repo2/master2", loginUser="root")
    val (issue3, pullreq3) = generateNewPullRequest("user3/repo3/master3", "user2/repo2/master2", loginUser="root")
    val (issue32, pullreq32) = generateNewPullRequest("user3/repo3/master32", "user2/repo2/master2", loginUser="root")
    generateNewPullRequest("user2/repo2/master2", "user1/repo1/master2")
    service.addWebHook("user1", "repo1", "webhook1-1", Set(WebHook.PullRequest), Some("key"))
    service.addWebHook("user1", "repo1", "webhook1-2", Set(WebHook.PullRequest), Some("key"))
    service.addWebHook("user2", "repo2", "webhook2-1", Set(WebHook.PullRequest), Some("key"))
    service.addWebHook("user2", "repo2", "webhook2-2", Set(WebHook.PullRequest), Some("key"))
    service.addWebHook("user3", "repo3", "webhook3-1", Set(WebHook.PullRequest), Some("key"))
    service.addWebHook("user3", "repo3", "webhook3-2", Set(WebHook.PullRequest), Some("key"))

    assert(service.getPullRequestsByRequestForWebhook("user1","repo1","master1") == Map.empty)

    val r = service.getPullRequestsByRequestForWebhook("user2","repo2","master2").mapValues(_.map(_.url).toSet)

    assert(r.size == 3)
    assert(r((issue1, issueUser, pullreq1, user1, user2)) == Set("webhook1-1","webhook1-2"))
    assert(r((issue3, issueUser, pullreq3, user3, user2)) == Set("webhook3-1","webhook3-2"))
    assert(r((issue32, issueUser, pullreq32, user3, user2)) == Set("webhook3-1","webhook3-2"))

    // when closed, it not founds.
    service.updateClosed("user1","repo1",issue1.issueId, true)

    val r2 = service.getPullRequestsByRequestForWebhook("user2","repo2","master2").mapValues(_.map(_.url).toSet)
    assert(r2.size == 2)
    assert(r2((issue3, issueUser, pullreq3, user3, user2)) == Set("webhook3-1","webhook3-2"))
    assert(r2((issue32, issueUser, pullreq32, user3, user2)) == Set("webhook3-1","webhook3-2"))
  } }

  test("add and get and update and delete") { withTestDB { implicit session =>
    val user1 = generateNewUserWithDBRepository("user1","repo1")
    service.addWebHook("user1", "repo1", "http://example.com", Set(WebHook.PullRequest), Some("key"))
    assert(service.getWebHooks("user1", "repo1") == List((WebHook("user1","repo1","http://example.com", Some("key")),Set(WebHook.PullRequest))))
    assert(service.getWebHook("user1", "repo1", "http://example.com") == Some((WebHook("user1","repo1","http://example.com", Some("key")),Set(WebHook.PullRequest))))
    assert(service.getWebHooksByEvent("user1", "repo1", WebHook.PullRequest) == List((WebHook("user1","repo1","http://example.com", Some("key")))))
    assert(service.getWebHooksByEvent("user1", "repo1", WebHook.Push) == Nil)
    assert(service.getWebHook("user1", "repo1", "http://example.com2") == None)
    assert(service.getWebHook("user2", "repo1", "http://example.com") == None)
    assert(service.getWebHook("user1", "repo2", "http://example.com") == None)
    service.updateWebHook("user1", "repo1", "http://example.com", Set(WebHook.Push, WebHook.Issues), Some("key"))
    assert(service.getWebHook("user1", "repo1", "http://example.com") == Some((WebHook("user1","repo1","http://example.com", Some("key")),Set(WebHook.Push, WebHook.Issues))))
    assert(service.getWebHooksByEvent("user1", "repo1", WebHook.PullRequest) == Nil)
    assert(service.getWebHooksByEvent("user1", "repo1", WebHook.Push) == List((WebHook("user1","repo1","http://example.com", Some("key")))))
    service.deleteWebHook("user1", "repo1", "http://example.com")
    assert(service.getWebHook("user1", "repo1", "http://example.com") == None)
  } }

  test("getWebHooks, getWebHooksByEvent") { withTestDB { implicit session =>
    val user1 = generateNewUserWithDBRepository("user1","repo1")
    service.addWebHook("user1", "repo1", "http://example.com/1", Set(WebHook.PullRequest), Some("key"))
    service.addWebHook("user1", "repo1", "http://example.com/2", Set(WebHook.Push), Some("key"))
    service.addWebHook("user1", "repo1", "http://example.com/3", Set(WebHook.PullRequest,WebHook.Push), Some("key"))
    assert(service.getWebHooks("user1", "repo1") == List(
      WebHook("user1","repo1","http://example.com/1", Some("key"))->Set(WebHook.PullRequest),
      WebHook("user1","repo1","http://example.com/2", Some("key"))->Set(WebHook.Push),
      WebHook("user1","repo1","http://example.com/3", Some("key"))->Set(WebHook.PullRequest,WebHook.Push)))
    assert(service.getWebHooksByEvent("user1", "repo1", WebHook.PullRequest) == List(
      WebHook("user1","repo1","http://example.com/1", Some("key")),
      WebHook("user1","repo1","http://example.com/3", Some("key"))))
  } }
}