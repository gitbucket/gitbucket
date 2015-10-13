package gitbucket.core.service

import org.specs2.mutable.Specification
import gitbucket.core.model.WebHook


class WebHookServiceSpec extends Specification with ServiceSpecBase {
  lazy val service = new WebHookPullRequestService with AccountService with RepositoryService with PullRequestService with IssuesService

  "WebHookPullRequestService.getPullRequestsByRequestForWebhook" should {
    "find from request branch" in { withTestDB { implicit session =>
      val user1 = generateNewUserWithDBRepository("user1","repo1")
      val user2 = generateNewUserWithDBRepository("user2","repo2")
      val user3 = generateNewUserWithDBRepository("user3","repo3")
      val issueUser = user("root")
      val (issue1, pullreq1) = generateNewPullRequest("user1/repo1/master1", "user2/repo2/master2", loginUser="root")
      val (issue3, pullreq3) = generateNewPullRequest("user3/repo3/master3", "user2/repo2/master2", loginUser="root")
      val (issue32, pullreq32) = generateNewPullRequest("user3/repo3/master32", "user2/repo2/master2", loginUser="root")
      generateNewPullRequest("user2/repo2/master2", "user1/repo1/master2")
      service.addWebHook("user1", "repo1", "webhook1-1", Set(WebHook.PullRequest))
      service.addWebHook("user1", "repo1", "webhook1-2", Set(WebHook.PullRequest))
      service.addWebHook("user2", "repo2", "webhook2-1", Set(WebHook.PullRequest))
      service.addWebHook("user2", "repo2", "webhook2-2", Set(WebHook.PullRequest))
      service.addWebHook("user3", "repo3", "webhook3-1", Set(WebHook.PullRequest))
      service.addWebHook("user3", "repo3", "webhook3-2", Set(WebHook.PullRequest))

      service.getPullRequestsByRequestForWebhook("user1","repo1","master1") must_== Map.empty

      var r = service.getPullRequestsByRequestForWebhook("user2","repo2","master2").mapValues(_.map(_.url).toSet)

      r.size must_== 3
      r((issue1, issueUser, pullreq1, user1, user2)) must_== Set("webhook1-1","webhook1-2")
      r((issue3, issueUser, pullreq3, user3, user2)) must_== Set("webhook3-1","webhook3-2")
      r((issue32, issueUser, pullreq32, user3, user2)) must_== Set("webhook3-1","webhook3-2")

      // when closed, it not founds.
      service.updateClosed("user1","repo1",issue1.issueId, true)

      var r2 = service.getPullRequestsByRequestForWebhook("user2","repo2","master2").mapValues(_.map(_.url).toSet)
      r2.size must_== 2
      r2((issue3, issueUser, pullreq3, user3, user2)) must_== Set("webhook3-1","webhook3-2")
      r2((issue32, issueUser, pullreq32, user3, user2)) must_== Set("webhook3-1","webhook3-2")
    } }
  }

  "add and get and update and delete" in { withTestDB { implicit session =>
    val user1 = generateNewUserWithDBRepository("user1","repo1")
    service.addWebHook("user1", "repo1", "http://example.com", Set(WebHook.PullRequest))
    service.getWebHooks("user1", "repo1") must_== List((WebHook("user1","repo1","http://example.com"),Set(WebHook.PullRequest)))
    service.getWebHook("user1", "repo1", "http://example.com") must_== Some((WebHook("user1","repo1","http://example.com"),Set(WebHook.PullRequest)))
    service.getWebHooksByEvent("user1", "repo1", WebHook.PullRequest) must_== List((WebHook("user1","repo1","http://example.com")))
    service.getWebHooksByEvent("user1", "repo1", WebHook.Push) must_== Nil
    service.getWebHook("user1", "repo1", "http://example.com2") must_== None
    service.getWebHook("user2", "repo1", "http://example.com") must_== None
    service.getWebHook("user1", "repo2", "http://example.com") must_== None
    service.updateWebHook("user1", "repo1", "http://example.com", Set(WebHook.Push, WebHook.Issues))
    service.getWebHook("user1", "repo1", "http://example.com") must_== Some((WebHook("user1","repo1","http://example.com"),Set(WebHook.Push, WebHook.Issues)))
    service.getWebHooksByEvent("user1", "repo1", WebHook.PullRequest) must_== Nil
    service.getWebHooksByEvent("user1", "repo1", WebHook.Push) must_== List((WebHook("user1","repo1","http://example.com")))
    service.deleteWebHook("user1", "repo1", "http://example.com")
    service.getWebHook("user1", "repo1", "http://example.com") must_== None
  } }
  "getWebHooks, getWebHooksByEvent" in { withTestDB { implicit session =>
    val user1 = generateNewUserWithDBRepository("user1","repo1")
    service.addWebHook("user1", "repo1", "http://example.com/1", Set(WebHook.PullRequest))
    service.addWebHook("user1", "repo1", "http://example.com/2", Set(WebHook.Push))
    service.addWebHook("user1", "repo1", "http://example.com/3", Set(WebHook.PullRequest,WebHook.Push))
    service.getWebHooks("user1", "repo1") must_== List(
      WebHook("user1","repo1","http://example.com/1")->Set(WebHook.PullRequest),
      WebHook("user1","repo1","http://example.com/2")->Set(WebHook.Push),
      WebHook("user1","repo1","http://example.com/3")->Set(WebHook.PullRequest,WebHook.Push))
    service.getWebHooksByEvent("user1", "repo1", WebHook.PullRequest) must_== List(
      WebHook("user1","repo1","http://example.com/1"),
      WebHook("user1","repo1","http://example.com/3"))
  } }
}