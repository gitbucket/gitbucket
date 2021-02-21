Authentication in Controller
========
GitBucket provides many [authenticators](https://github.com/gitbucket/gitbucket/blob/master/src/main/scala/gitbucket/core/util/Authenticator.scala) to access controlling in the controller.

For example, in the case of `RepositoryViewerController`,
it references three authenticators: `ReadableUsersAuthenticator`, `ReferrerAuthenticator` and `CollaboratorsAuthenticator`.

```scala
class RepositoryViewerController extends RepositoryViewerControllerBase
  with RepositoryService with AccountService with ActivityService with IssuesService with WebHookService with CommitsService
  with ReadableUsersAuthenticator with ReferrerAuthenticator with CollaboratorsAuthenticator with PullRequestService with CommitStatusService
  with WebHookPullRequestService with WebHookPullRequestReviewCommentService

trait RepositoryViewerControllerBase extends ControllerBase {
  self: RepositoryService with AccountService with ActivityService with IssuesService with WebHookService with CommitsService
    with ReadableUsersAuthenticator with ReferrerAuthenticator with CollaboratorsAuthenticator with PullRequestService with CommitStatusService
    with WebHookPullRequestService with WebHookPullRequestReviewCommentService =>

  ...
```

Authenticators provide a method to add guard to actions in the controller:

- `ReadableUsersAuthenticator` provides `readableUsersOnly` method
- `ReferrerAuthenticator` provides `referrersOnly` method
- `CollaboratorsAuthenticator` provides `collaboratorsOnly` method

These methods are available in each action as below:

```scala
// Allows only the repository owner (or manager for group repository) and administrators.
get("/:owner/:repository/tree/*")(referrersOnly { repository =>
  ...
})

// Allows only collaborators and administrators.
get("/:owner/:repository/new/*")(collaboratorsOnly { repository =>
  ...
})

// Allows only signed-in users which can access the repository.
post("/:owner/:repository/commit/:id/comment/new", commentForm)(readableUsersOnly { (form, repository) =>
  ...
})
```

Currently, GitBucket provides below authenticators:

|Trait                     | Method          | Description                                                                          |
|--------------------------|-----------------|--------------------------------------------------------------------------------------|
|OneselfAuthenticator      |oneselfOnly      |Allows only oneself and administrators.                                               |
|OwnerAuthenticator        |ownerOnly        |Allows only the repository owner and administrators.                                  |
|UsersAuthenticator        |usersOnly        |Allows only signed-in users.                                                          |
|AdminAuthenticator        |adminOnly        |Allows only administrators.                                                           |
|CollaboratorsAuthenticator|collaboratorsOnly|Allows only collaborators and administrators.                                         |
|ReferrerAuthenticator     |referrersOnly    |Allows only the repository owner (or manager for group repository) and administrators.|
|ReadableUsersAuthenticator|readableUsersOnly|Allows only signed-in users which can access the repository.                          |
|GroupManagerAuthenticator |managersOnly     |Allows only the group managers.                                                       |

Of course, if you make a new plugin, you can implement your own authenticator according to requirement in your plugin.
