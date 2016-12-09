package gitbucket.core.controller

import gitbucket.core.api._
import gitbucket.core.model._
import gitbucket.core.service.IssuesService.IssueSearchCondition
import gitbucket.core.service.PullRequestService._
import gitbucket.core.service._
import gitbucket.core.util.ControlUtil._
import gitbucket.core.util.Directory._
import gitbucket.core.util.JGitUtil._
import gitbucket.core.util._
import gitbucket.core.util.Implicits._
import gitbucket.core.view.helpers.{renderMarkup, isRenderable}
import org.eclipse.jgit.api.Git
import org.scalatra.{NoContent, UnprocessableEntity, Created}
import scala.collection.JavaConverters._

class ApiController extends ApiControllerBase
  with RepositoryService
  with AccountService
  with ProtectedBranchService
  with IssuesService
  with LabelsService
  with PullRequestService
  with CommitStatusService
  with RepositoryCreationService
  with HandleCommentService
  with WebHookService
  with WebHookPullRequestService
  with WebHookIssueCommentService
  with WikiService
  with ActivityService
  with OwnerAuthenticator
  with UsersAuthenticator
  with GroupManagerAuthenticator
  with ReferrerAuthenticator
  with ReadableUsersAuthenticator
  with WritableUsersAuthenticator

trait ApiControllerBase extends ControllerBase {
  self: RepositoryService
    with AccountService
    with ProtectedBranchService
    with IssuesService
    with LabelsService
    with PullRequestService
    with CommitStatusService
    with RepositoryCreationService
    with HandleCommentService
    with OwnerAuthenticator
    with UsersAuthenticator
    with GroupManagerAuthenticator
    with ReferrerAuthenticator
    with ReadableUsersAuthenticator
    with WritableUsersAuthenticator =>

  /**
    * https://developer.github.com/v3/#root-endpoint
    */
  get("/api/v3/") {
    JsonFormat(ApiEndPoint())
  }

  /**
    * https://developer.github.com/v3/orgs/#get-an-organization
    */
  get("/api/v3/orgs/:groupName") {
    getAccountByUserName(params("groupName")).filter(account => account.isGroupAccount).map { account =>
      JsonFormat(ApiUser(account))
    } getOrElse NotFound()
  }

  /**
   * https://developer.github.com/v3/users/#get-a-single-user
   */
  get("/api/v3/users/:userName") {
    getAccountByUserName(params("userName")).filterNot(account => account.isGroupAccount).map { account =>
      JsonFormat(ApiUser(account))
    } getOrElse NotFound()
  }

  /**
    * https://developer.github.com/v3/repos/#list-organization-repositories
    */
  get("/api/v3/orgs/:orgName/repos") {
    JsonFormat(getVisibleRepositories(context.loginAccount, Some(params("orgName"))).map{ r => ApiRepository(r, getAccountByUserName(r.owner).get)})
  }
  /**
   * https://developer.github.com/v3/repos/#list-user-repositories
   */
  get("/api/v3/users/:userName/repos") {
    JsonFormat(getVisibleRepositories(context.loginAccount, Some(params("userName"))).map{ r => ApiRepository(r, getAccountByUserName(r.owner).get)})
  }

  /*
   * https://developer.github.com/v3/repos/branches/#list-branches
   */
  get ("/api/v3/repos/:owner/:repo/branches")(referrersOnly { repository =>
    JsonFormat(JGitUtil.getBranches(
      owner         = repository.owner,
      name          = repository.name,
      defaultBranch = repository.repository.defaultBranch,
      origin        = repository.repository.originUserName.isEmpty
    ).map { br =>
        ApiBranchForList(br.name, ApiBranchCommit(br.commitId))
    })
  })

  /*
   * https://developer.github.com/v3/repos/contents/#get-contents
   */
  get("/api/v3/repos/:owner/:repo/contents/*")(referrersOnly { repository =>
    def getFileInfo(git: Git, revision: String, pathStr: String): Option[FileInfo] = {
      val path = new java.io.File(pathStr)
      val dirName = path.getParent match {
        case null => "."
        case s => s
      }
      getFileList(git, revision, dirName).find(f => f.name.equals(path.getName))
    }

    val path = multiParams("splat").head match {
      case s if s.isEmpty => "."
      case s => s
    }
    val refStr = params.getOrElse("ref", repository.repository.defaultBranch)

    using(Git.open(getRepositoryDir(params("owner"), params("repo")))){ git =>
      val fileList = getFileList(git, refStr, path)
      if (fileList.isEmpty) { // file or NotFound
        getFileInfo(git, refStr, path).flatMap(f => {
          val largeFile = params.get("large_file").exists(s => s.equals("true"))
          val content = getContentFromId(git, f.id, largeFile)
          request.getHeader("Accept") match {
            case "application/vnd.github.v3.raw" =>
              content
            case "application/vnd.github.v3.html" if isRenderable(f.name) =>
              content.map(c =>
                List(
                  "<div data-path=\"", path, "\" id=\"file\">", "<article>",
                  renderMarkup(path.split("/").toList, new String(c), refStr, repository, false, false, true).body,
                  "</article>", "</div>"
                ).mkString
              )
            case "application/vnd.github.v3.html" =>
              content.map(c =>
                List(
                  "<div data-path=\"", path, "\" id=\"file\">", "<div class=\"plain\">", "<pre>",
                  play.twirl.api.HtmlFormat.escape(new String(c)).body,
                  "</pre>", "</div>", "</div>"
                ).mkString
              )
            case _ =>
              Some(JsonFormat(ApiContents(f, content)))
          }
        }).getOrElse(NotFound())
      } else { // directory
        JsonFormat(fileList.map{f => ApiContents(f, None)})
      }
    }
  })

  /*
   * https://developer.github.com/v3/git/refs/#get-a-reference
   */
  get("/api/v3/repos/:owner/:repo/git/*") (referrersOnly { repository =>
    val revstr = multiParams("splat").head
    using(Git.open(getRepositoryDir(params("owner"), params("repo")))) { git =>
      //JsonFormat( (revstr, git.getRepository().resolve(revstr)) )
      // getRef is deprecated by jgit-4.2. use exactRef() or findRef()
      val sha = git.getRepository().getRef(revstr).getObjectId().name()
      JsonFormat(ApiRef(revstr, ApiObject(sha)))
    }
  })

  /**
   * https://developer.github.com/v3/repos/collaborators/#list-collaborators
   */
  get("/api/v3/repos/:owner/:repo/collaborators") (referrersOnly { repository =>
    // TODO Should ApiUser take permission? getCollaboratorUserNames does not return owner group members.
    JsonFormat(getCollaboratorUserNames(params("owner"), params("repo")).map(u => ApiUser(getAccountByUserName(u).get)))
  })

  /**
   * https://developer.github.com/v3/users/#get-the-authenticated-user
   */
  get("/api/v3/user") {
    context.loginAccount.map { account =>
      JsonFormat(ApiUser(account))
    } getOrElse Unauthorized()
  }

  /**
   * List user's own repository
   * https://developer.github.com/v3/repos/#list-your-repositories
   */
  get("/api/v3/user/repos")(usersOnly{
    JsonFormat(getVisibleRepositories(context.loginAccount, Option(context.loginAccount.get.userName)).map{
      r => ApiRepository(r, getAccountByUserName(r.owner).get)
    })
  })

  /**
   * Create user repository
   * https://developer.github.com/v3/repos/#create
   */
  post("/api/v3/user/repos")(usersOnly {
    val owner = context.loginAccount.get.userName
    (for {
      data <- extractFromJsonBody[CreateARepository] if data.isValid
    } yield {
      LockUtil.lock(s"${owner}/${data.name}") {
        if(getRepository(owner, data.name).isEmpty){
          createRepository(context.loginAccount.get, owner, data.name, data.description, data.`private`, data.auto_init)
          val repository = getRepository(owner, data.name).get
          JsonFormat(ApiRepository(repository, ApiUser(getAccountByUserName(owner).get)))
        } else {
          ApiError(
            "A repository with this name already exists on this account",
            Some("https://developer.github.com/v3/repos/#create")
          )
        }
      }
    }) getOrElse NotFound()
  })

  /**
   * Create group repository
   * https://developer.github.com/v3/repos/#create
   */
  post("/api/v3/orgs/:org/repos")(managersOnly {
    val groupName = params("org")
    (for {
      data <- extractFromJsonBody[CreateARepository] if data.isValid
    } yield {
      LockUtil.lock(s"${groupName}/${data.name}") {
        if(getRepository(groupName, data.name).isEmpty){
          createRepository(context.loginAccount.get, groupName, data.name, data.description, data.`private`, data.auto_init)
          val repository = getRepository(groupName, data.name).get
          JsonFormat(ApiRepository(repository, ApiUser(getAccountByUserName(groupName).get)))
        } else {
          ApiError(
            "A repository with this name already exists for this group",
            Some("https://developer.github.com/v3/repos/#create")
          )
        }
      }
    }) getOrElse NotFound()
  })

  /**
   * https://developer.github.com/v3/repos/#enabling-and-disabling-branch-protection
   */
  patch("/api/v3/repos/:owner/:repo/branches/:branch")(ownerOnly { repository =>
    import gitbucket.core.api._
    (for{
      branch <- params.get("branch") if repository.branchList.find(_ == branch).isDefined
      protection <- extractFromJsonBody[ApiBranchProtection.EnablingAndDisabling].map(_.protection)
    } yield {
      if(protection.enabled){
        enableBranchProtection(repository.owner, repository.name, branch, protection.status.enforcement_level == ApiBranchProtection.Everyone, protection.status.contexts)
      } else {
        disableBranchProtection(repository.owner, repository.name, branch)
      }
      JsonFormat(ApiBranch(branch, protection)(RepositoryName(repository)))
    }) getOrElse NotFound()
  })

  /**
   * @see https://developer.github.com/v3/rate_limit/#get-your-current-rate-limit-status
   * but not enabled.
   */
  get("/api/v3/rate_limit"){
    contentType = formats("json")
    // this message is same as github enterprise...
    org.scalatra.NotFound(ApiError("Rate limiting is not enabled."))
  }

  /**
   * https://developer.github.com/v3/issues/comments/#list-comments-on-an-issue
   */
  get("/api/v3/repos/:owner/:repository/issues/:id/comments")(referrersOnly { repository =>
    (for{
      issueId <- params("id").toIntOpt
      comments = getCommentsForApi(repository.owner, repository.name, issueId.toInt)
    } yield {
      JsonFormat(comments.map{ case (issueComment, user, issue) => ApiComment(issueComment, RepositoryName(repository), issueId, ApiUser(user), issue.isPullRequest) })
    }) getOrElse NotFound()
  })

  /**
   * https://developer.github.com/v3/issues/comments/#create-a-comment
   */
  post("/api/v3/repos/:owner/:repository/issues/:id/comments")(readableUsersOnly { repository =>
    (for{
      issueId      <- params("id").toIntOpt
      issue        <- getIssue(repository.owner, repository.name, issueId.toString)
      body         <- extractFromJsonBody[CreateAComment].map(_.body) if ! body.isEmpty
      action       =  params.get("action").filter(_ => isEditable(issue.userName, issue.repositoryName, issue.openedUserName))
      (issue, id)  <- handleComment(issue, Some(body), repository, action)
      issueComment <- getComment(repository.owner, repository.name, id.toString())
    } yield {
      JsonFormat(ApiComment(issueComment, RepositoryName(repository), issueId, ApiUser(context.loginAccount.get), issue.isPullRequest))
    }) getOrElse NotFound()
  })

  /**
   * List all labels for this repository
   * https://developer.github.com/v3/issues/labels/#list-all-labels-for-this-repository
   */
  get("/api/v3/repos/:owner/:repository/labels")(referrersOnly { repository =>
    JsonFormat(getLabels(repository.owner, repository.name).map { label =>
      ApiLabel(label, RepositoryName(repository))
    })
  })

  /**
   * Get a single label
   * https://developer.github.com/v3/issues/labels/#get-a-single-label
   */
  get("/api/v3/repos/:owner/:repository/labels/:labelName")(referrersOnly { repository =>
    getLabel(repository.owner, repository.name, params("labelName")).map { label =>
      JsonFormat(ApiLabel(label, RepositoryName(repository)))
    } getOrElse NotFound()
  })

  /**
   * Create a label
   * https://developer.github.com/v3/issues/labels/#create-a-label
   */
  post("/api/v3/repos/:owner/:repository/labels")(writableUsersOnly { repository =>
    (for{
      data <- extractFromJsonBody[CreateALabel] if data.isValid
    } yield {
      LockUtil.lock(RepositoryName(repository).fullName) {
        if (getLabel(repository.owner, repository.name, data.name).isEmpty) {
          val labelId = createLabel(repository.owner, repository.name, data.name, data.color)
          getLabel(repository.owner, repository.name, labelId).map { label =>
            Created(JsonFormat(ApiLabel(label, RepositoryName(repository))))
          } getOrElse NotFound()
        } else {
          // TODO ApiError should support errors field to enhance compatibility of GitHub API
          UnprocessableEntity(ApiError(
            "Validation Failed",
            Some("https://developer.github.com/v3/issues/labels/#create-a-label")
          ))
        }
      }
    }) getOrElse NotFound()
  })

  /**
   * Update a label
   * https://developer.github.com/v3/issues/labels/#update-a-label
   */
  patch("/api/v3/repos/:owner/:repository/labels/:labelName")(writableUsersOnly { repository =>
    (for{
      data <- extractFromJsonBody[CreateALabel] if data.isValid
    } yield {
      LockUtil.lock(RepositoryName(repository).fullName) {
        getLabel(repository.owner, repository.name, params("labelName")).map { label =>
          if (getLabel(repository.owner, repository.name, data.name).isEmpty) {
            updateLabel(repository.owner, repository.name, label.labelId, data.name, data.color)
            JsonFormat(ApiLabel(
              getLabel(repository.owner, repository.name, label.labelId).get,
              RepositoryName(repository)))
          } else {
            // TODO ApiError should support errors field to enhance compatibility of GitHub API
            UnprocessableEntity(ApiError(
              "Validation Failed",
              Some("https://developer.github.com/v3/issues/labels/#create-a-label")))
          }
        } getOrElse NotFound()
      }
    }) getOrElse NotFound()
  })

  /**
   * Delete a label
   * https://developer.github.com/v3/issues/labels/#delete-a-label
   */
  delete("/api/v3/repos/:owner/:repository/labels/:labelName")(writableUsersOnly { repository =>
    LockUtil.lock(RepositoryName(repository).fullName) {
      getLabel(repository.owner, repository.name, params("labelName")).map { label =>
        deleteLabel(repository.owner, repository.name, label.labelId)
        NoContent()
      } getOrElse NotFound()
    }
  })

  /**
   * https://developer.github.com/v3/pulls/#list-pull-requests
   */
  get("/api/v3/repos/:owner/:repository/pulls")(referrersOnly { repository =>
    val page = IssueSearchCondition.page(request)
    // TODO: more api spec condition
    val condition = IssueSearchCondition(request)
    val baseOwner = getAccountByUserName(repository.owner).get

    val issues: List[(Issue, Account, Int, PullRequest, Repository, Account)] =
      searchPullRequestByApi(
        condition = condition,
        offset    = (page - 1) * PullRequestLimit,
        limit     = PullRequestLimit,
        repos     = repository.owner -> repository.name
      )


    JsonFormat(issues.map { case (issue, issueUser, commentCount, pullRequest, headRepo, headOwner) =>
      val comments = getCommentsForApi(repository.owner, repository.name, issue.issueId)

      ApiPullRequest(
        issue       = issue,
        pullRequest = pullRequest,
        headRepo    = ApiRepository(headRepo, ApiUser(headOwner)),
        baseRepo    = ApiRepository(repository, ApiUser(baseOwner)),
        user        = ApiUser(issueUser),
        merged      = comments.exists { case (comment, _, _) => comment.action == "merged" },
        mergedAt    = comments.collectFirst { case (comment, _, _) if(comment.action == "merged") => comment.registeredDate }
      )
    })
  })

  /**
   * https://developer.github.com/v3/pulls/#get-a-single-pull-request
   */
  get("/api/v3/repos/:owner/:repository/pulls/:id")(referrersOnly { repository =>
    (for{
      issueId <- params("id").toIntOpt
      (issue, pullRequest) <- getPullRequest(repository.owner, repository.name, issueId)
      users = getAccountsByUserNames(Set(repository.owner, pullRequest.requestUserName, issue.openedUserName), Set())
      baseOwner <- users.get(repository.owner)
      headOwner <- users.get(pullRequest.requestUserName)
      issueUser <- users.get(issue.openedUserName)
      headRepo  <- getRepository(pullRequest.requestUserName, pullRequest.requestRepositoryName)
    } yield {
      val comments = getCommentsForApi(repository.owner, repository.name, issueId)

      JsonFormat(ApiPullRequest(
        issue       = issue,
        pullRequest = pullRequest,
        headRepo    = ApiRepository(headRepo, ApiUser(headOwner)),
        baseRepo    = ApiRepository(repository, ApiUser(baseOwner)),
        user        = ApiUser(issueUser),
        merged      = comments.exists { case (comment, _, _) => comment.action == "merged" },
        mergedAt    = comments.collectFirst { case (comment, _, _) if(comment.action == "merged") => comment.registeredDate }
      ))
    }) getOrElse NotFound()
  })

  /**
   * https://developer.github.com/v3/pulls/#list-commits-on-a-pull-request
   */
  get("/api/v3/repos/:owner/:repository/pulls/:id/commits")(referrersOnly { repository =>
    val owner = repository.owner
    val name = repository.name
    params("id").toIntOpt.flatMap{ issueId =>
      getPullRequest(owner, name, issueId) map { case(issue, pullreq) =>
        using(Git.open(getRepositoryDir(owner, name))){ git =>
          val oldId = git.getRepository.resolve(pullreq.commitIdFrom)
          val newId = git.getRepository.resolve(pullreq.commitIdTo)
          val repoFullName = RepositoryName(repository)
          val commits = git.log.addRange(oldId, newId).call.iterator.asScala.map(c => ApiCommitListItem(new CommitInfo(c), repoFullName)).toList
          JsonFormat(commits)
        }
      }
    } getOrElse NotFound()
  })

  /**
   * https://developer.github.com/v3/repos/#get
   */
  get("/api/v3/repos/:owner/:repository")(referrersOnly { repository =>
    JsonFormat(ApiRepository(repository, ApiUser(getAccountByUserName(repository.owner).get)))
  })

  /**
   * https://developer.github.com/v3/repos/statuses/#create-a-status
   */
  post("/api/v3/repos/:owner/:repo/statuses/:sha")(writableUsersOnly { repository =>
    (for{
      ref <- params.get("sha")
      sha <- JGitUtil.getShaByRef(repository.owner, repository.name, ref)
      data <- extractFromJsonBody[CreateAStatus] if data.isValid
      creator <- context.loginAccount
      state <- CommitState.valueOf(data.state)
      statusId = createCommitStatus(repository.owner, repository.name, sha, data.context.getOrElse("default"),
        state, data.target_url, data.description, new java.util.Date(), creator)
      status <- getCommitStatus(repository.owner, repository.name, statusId)
    } yield {
      JsonFormat(ApiCommitStatus(status, ApiUser(creator)))
    }) getOrElse NotFound()
  })

  /**
   * https://developer.github.com/v3/repos/statuses/#list-statuses-for-a-specific-ref
   *
   * ref is Ref to list the statuses from. It can be a SHA, a branch name, or a tag name.
   */
  val listStatusesRoute = get("/api/v3/repos/:owner/:repo/commits/:ref/statuses")(referrersOnly { repository =>
    (for{
      ref <- params.get("ref")
      sha <- JGitUtil.getShaByRef(repository.owner, repository.name, ref)
    } yield {
      JsonFormat(getCommitStatuesWithCreator(repository.owner, repository.name, sha).map{ case(status, creator) =>
        ApiCommitStatus(status, ApiUser(creator))
      })
    }) getOrElse NotFound()
  })

  /**
   * https://developer.github.com/v3/repos/statuses/#list-statuses-for-a-specific-ref
   *
   * legacy route
   */
  get("/api/v3/repos/:owner/:repo/statuses/:ref"){
    listStatusesRoute.action()
  }

  /**
   * https://developer.github.com/v3/repos/statuses/#get-the-combined-status-for-a-specific-ref
   *
   * ref is Ref to list the statuses from. It can be a SHA, a branch name, or a tag name.
   */
  get("/api/v3/repos/:owner/:repo/commits/:ref/status")(referrersOnly { repository =>
    (for{
      ref <- params.get("ref")
      owner <- getAccountByUserName(repository.owner)
      sha <- JGitUtil.getShaByRef(repository.owner, repository.name, ref)
    } yield {
      val statuses = getCommitStatuesWithCreator(repository.owner, repository.name, sha)
      JsonFormat(ApiCombinedCommitStatus(sha, statuses, ApiRepository(repository, owner)))
    }) getOrElse NotFound()
  })

  private def isEditable(owner: String, repository: String, author: String)(implicit context: Context): Boolean =
    hasDeveloperRole(owner, repository, context.loginAccount) || author == context.loginAccount.get.userName

}

