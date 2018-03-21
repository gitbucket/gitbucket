package gitbucket.core.controller

import gitbucket.core.api._
import gitbucket.core.model._
import gitbucket.core.service.IssuesService.IssueSearchCondition
import gitbucket.core.service.PullRequestService._
import gitbucket.core.service._
import gitbucket.core.util.Directory._
import gitbucket.core.util.Implicits._
import gitbucket.core.util.JGitUtil._
import gitbucket.core.util.SyntaxSugars._
import gitbucket.core.util._
import gitbucket.core.plugin.PluginRegistry
import gitbucket.core.view.helpers.{isRenderable, renderMarkup}
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.revwalk.RevWalk
import org.scalatra.{Created, NoContent, UnprocessableEntity}

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration.Duration

class ApiController extends ApiControllerBase
  with RepositoryService
  with AccountService
  with ProtectedBranchService
  with IssuesService
  with LabelsService
  with MilestonesService
  with PullRequestService
  with CommitsService
  with CommitStatusService
  with RepositoryCreationService
  with IssueCreationService
  with HandleCommentService
  with WebHookService
  with WebHookPullRequestService
  with WebHookIssueCommentService
  with WikiService
  with ActivityService
  with PrioritiesService
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
    with MilestonesService
    with PullRequestService
    with CommitsService
    with CommitStatusService
    with RepositoryCreationService
    with IssueCreationService
    with HandleCommentService
    with PrioritiesService
    with OwnerAuthenticator
    with UsersAuthenticator
    with GroupManagerAuthenticator
    with ReferrerAuthenticator
    with ReadableUsersAuthenticator
    with WritableUsersAuthenticator =>

  /**
    * 404 for non-implemented api
    */
  get("/api/v3/*") {
    NotFound()
  }

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
   * This API also returns group information (as GitHub).
   */
  get("/api/v3/users/:userName") {
    getAccountByUserName(params("userName")).map { account =>
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

  /**
    * https://developer.github.com/v3/repos/branches/#get-branch
    */
  get ("/api/v3/repos/:owner/:repo/branches/*")(referrersOnly { repository =>
    //import gitbucket.core.api._
    (for{
      branch <- params.get("splat") if repository.branchList.contains(branch)
      br <- getBranches(repository.owner, repository.name, repository.repository.defaultBranch, repository.repository.originUserName.isEmpty).find(_.name == branch)
    } yield {
      val protection = getProtectedBranchInfo(repository.owner, repository.name, branch)
      JsonFormat(ApiBranch(branch, ApiBranchCommit(br.commitId), ApiBranchProtection(protection))(RepositoryName(repository)))
    }) getOrElse NotFound()
  })

  /*
   * https://developer.github.com/v3/repos/contents/#get-contents
   */
  get("/api/v3/repos/:owner/:repo/contents/*")(referrersOnly { repository =>
    def getFileInfo(git: Git, revision: String, pathStr: String): Option[FileInfo] = {
      val (dirName, fileName) = pathStr.lastIndexOf('/') match {
        case -1 =>
          (".", pathStr)
        case n =>
          (pathStr.take(n), pathStr.drop(n + 1))
      }
      getFileList(git, revision, dirName).find(f => f.name.equals(fileName))
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
            case "application/vnd.github.v3.raw" => {
              contentType = "application/vnd.github.v3.raw"
              content
            }
            case "application/vnd.github.v3.html" if isRenderable(f.name) => {
              contentType = "application/vnd.github.v3.html"
              content.map(c =>
                List(
                  "<div data-path=\"", path, "\" id=\"file\">", "<article>",
                  renderMarkup(path.split("/").toList, new String(c), refStr, repository, false, false, true).body,
                  "</article>", "</div>"
                ).mkString
              )
            }
            case "application/vnd.github.v3.html" => {
              contentType = "application/vnd.github.v3.html"
              content.map(c =>
                List(
                  "<div data-path=\"", path, "\" id=\"file\">", "<div class=\"plain\">", "<pre>",
                  play.twirl.api.HtmlFormat.escape(new String(c)).body,
                  "</pre>", "</div>", "</div>"
                ).mkString
              )
            }
            case _ =>
              Some(JsonFormat(ApiContents(f, RepositoryName(repository), content)))
          }
        }).getOrElse(NotFound())
      } else { // directory
        JsonFormat(fileList.map{f => ApiContents(f, RepositoryName(repository), None)})
      }
    }
  })

  /*
   * https://developer.github.com/v3/git/refs/#get-a-reference
   */
  get("/api/v3/repos/:owner/:repo/git/refs/*") (referrersOnly { repository =>
    val revstr = multiParams("splat").head
    using(Git.open(getRepositoryDir(params("owner"), params("repo")))) { git =>
      val ref = git.getRepository().findRef(revstr)

      if(ref != null){
        val sha = ref.getObjectId().name()
        JsonFormat(ApiRef(revstr, ApiObject(sha)))

      } else {
        val refs = git.getRepository().getAllRefs().asScala
          .collect { case (str, ref) if str.startsWith("refs/" + revstr) => ref }

        JsonFormat(refs.map { ref =>
          val sha = ref.getObjectId().name()
          ApiRef(revstr, ApiObject(sha))
        })
      }
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
          val f = createRepository(context.loginAccount.get, owner, data.name, data.description, data.`private`, data.auto_init)
          Await.result(f, Duration.Inf)
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
          val f = createRepository(context.loginAccount.get, groupName, data.name, data.description, data.`private`, data.auto_init)
          Await.result(f, Duration.Inf)
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
  patch("/api/v3/repos/:owner/:repo/branches/*")(ownerOnly { repository =>
    import gitbucket.core.api._
    (for{
      branch     <- params.get("splat") if repository.branchList.contains(branch)
      protection <- extractFromJsonBody[ApiBranchProtection.EnablingAndDisabling].map(_.protection)
      br <- getBranches(repository.owner, repository.name, repository.repository.defaultBranch, repository.repository.originUserName.isEmpty).find(_.name == branch)
    } yield {
      if(protection.enabled){
        enableBranchProtection(repository.owner, repository.name, branch, protection.status.enforcement_level == ApiBranchProtection.Everyone, protection.status.contexts)
      } else {
        disableBranchProtection(repository.owner, repository.name, branch)
      }
      JsonFormat(ApiBranch(branch, ApiBranchCommit(br.commitId), protection)(RepositoryName(repository)))
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
    * https://developer.github.com/v3/issues/#list-issues-for-a-repository
    */
  get("/api/v3/repos/:owner/:repository/issues")(referrersOnly { repository =>
    val page = IssueSearchCondition.page(request)
    // TODO: more api spec condition
    val condition = IssueSearchCondition(request)
    val baseOwner = getAccountByUserName(repository.owner).get

    val issues: List[(Issue, Account)] =
      searchIssueByApi(
        condition = condition,
        offset    = (page - 1) * PullRequestLimit,
        limit     = PullRequestLimit,
        repos     = repository.owner -> repository.name
      )

    JsonFormat(issues.map { case (issue, issueUser) =>
      ApiIssue(
        issue          = issue,
        repositoryName = RepositoryName(repository),
        user           = ApiUser(issueUser),
        labels         = getIssueLabels(repository.owner, repository.name, issue.issueId).map(ApiLabel(_, RepositoryName(repository)))
      )
    })
  })

  /**
   * https://developer.github.com/v3/issues/#get-a-single-issue
   */
  get("/api/v3/repos/:owner/:repository/issues/:id")(referrersOnly { repository =>
    (for{
      issueId  <- params("id").toIntOpt
      issue <- getIssue(repository.owner, repository.name, issueId.toString)
      openedUser <- getAccountByUserName(issue.openedUserName)
    } yield {
      JsonFormat(ApiIssue(issue, RepositoryName(repository), ApiUser(openedUser),
        getIssueLabels(repository.owner, repository.name, issue.issueId).map(ApiLabel(_, RepositoryName(repository)))))
    }) getOrElse NotFound()
  })

  /**
    * https://developer.github.com/v3/issues/#create-an-issue
    */
  post("/api/v3/repos/:owner/:repository/issues")(readableUsersOnly { repository =>
    if(isIssueEditable(repository)){ // TODO Should this check is provided by authenticator?
      (for{
        data <- extractFromJsonBody[CreateAnIssue]
        loginAccount <- context.loginAccount
      } yield {
        val milestone = data.milestone.flatMap(getMilestone(repository.owner, repository.name, _))
        val issue = createIssue(
          repository,
          data.title,
          data.body,
          data.assignees.headOption,
          milestone.map(_.milestoneId),
          None,
          data.labels,
          loginAccount)
        JsonFormat(ApiIssue(issue, RepositoryName(repository), ApiUser(loginAccount),
          getIssueLabels(repository.owner, repository.name, issue.issueId).map(ApiLabel(_, RepositoryName(repository)))))
      }) getOrElse NotFound()
    } else Unauthorized()
  })

  /**
   * https://developer.github.com/v3/issues/comments/#list-comments-on-an-issue
   */
  get("/api/v3/repos/:owner/:repository/issues/:id/comments")(referrersOnly { repository =>
    (for{
      issueId  <- params("id").toIntOpt
      comments =  getCommentsForApi(repository.owner, repository.name, issueId)
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
              RepositoryName(repository)
            ))
          } else {
            // TODO ApiError should support errors field to enhance compatibility of GitHub API
            UnprocessableEntity(ApiError(
              "Validation Failed",
              Some("https://developer.github.com/v3/issues/labels/#create-a-label")
            ))
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

    val issues: List[(Issue, Account, Int, PullRequest, Repository, Account, Option[Account])] =
      searchPullRequestByApi(
        condition = condition,
        offset    = (page - 1) * PullRequestLimit,
        limit     = PullRequestLimit,
        repos     = repository.owner -> repository.name
      )

    JsonFormat(issues.map { case (issue, issueUser, commentCount, pullRequest, headRepo, headOwner, assignee) =>
      ApiPullRequest(
        issue         = issue,
        pullRequest   = pullRequest,
        headRepo      = ApiRepository(headRepo, ApiUser(headOwner)),
        baseRepo      = ApiRepository(repository, ApiUser(baseOwner)),
        user          = ApiUser(issueUser),
        labels        = getIssueLabels(repository.owner, repository.name, issue.issueId).map(ApiLabel(_, RepositoryName(repository))),
        assignee      = assignee.map(ApiUser.apply),
        mergedComment = getMergedComment(repository.owner, repository.name, issue.issueId)
      )
    })
  })

  /**
   * https://developer.github.com/v3/pulls/#get-a-single-pull-request
   */
  get("/api/v3/repos/:owner/:repository/pulls/:id")(referrersOnly { repository =>
    (for{
      issueId   <- params("id").toIntOpt
      (issue, pullRequest) <- getPullRequest(repository.owner, repository.name, issueId)
      users     =  getAccountsByUserNames(Set(repository.owner, pullRequest.requestUserName, issue.openedUserName), Set.empty)
      baseOwner <- users.get(repository.owner)
      headOwner <- users.get(pullRequest.requestUserName)
      issueUser <- users.get(issue.openedUserName)
      assignee  =  issue.assignedUserName.flatMap { userName => getAccountByUserName(userName, false) }
      headRepo  <- getRepository(pullRequest.requestUserName, pullRequest.requestRepositoryName)
    } yield {
      JsonFormat(ApiPullRequest(
        issue         = issue,
        pullRequest   = pullRequest,
        headRepo      = ApiRepository(headRepo, ApiUser(headOwner)),
        baseRepo      = ApiRepository(repository, ApiUser(baseOwner)),
        user          = ApiUser(issueUser),
        labels        = getIssueLabels(repository.owner, repository.name, issue.issueId).map(ApiLabel(_, RepositoryName(repository))),
        assignee      = assignee.map(ApiUser.apply),
        mergedComment = getMergedComment(repository.owner, repository.name, issue.issueId)
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
          val commits = git.log.addRange(oldId, newId).call.iterator.asScala.map { c => ApiCommitListItem(new CommitInfo(c), repoFullName) }.toList
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
      ref      <- params.get("sha")
      sha      <- JGitUtil.getShaByRef(repository.owner, repository.name, ref)
      data     <- extractFromJsonBody[CreateAStatus] if data.isValid
      creator  <- context.loginAccount
      state    <- CommitState.valueOf(data.state)
      statusId =  createCommitStatus(repository.owner, repository.name, sha, data.context.getOrElse("default"),
                                     state, data.target_url, data.description, new java.util.Date(), creator)
      status   <- getCommitStatus(repository.owner, repository.name, statusId)
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
      ref   <- params.get("ref")
      owner <- getAccountByUserName(repository.owner)
      sha   <- JGitUtil.getShaByRef(repository.owner, repository.name, ref)
    } yield {
      val statuses = getCommitStatuesWithCreator(repository.owner, repository.name, sha)
      JsonFormat(ApiCombinedCommitStatus(sha, statuses, ApiRepository(repository, owner)))
    }) getOrElse NotFound()
  })

  /**
   * https://developer.github.com/v3/repos/commits/#get-a-single-commit
   */
  get("/api/v3/repos/:owner/:repo/commits/:sha")(referrersOnly { repository =>
    val owner = repository.owner
    val name  = repository.name
    val sha   = params("sha")

    using(Git.open(getRepositoryDir(owner, name))){ git =>
      val repo = git.getRepository
      val objectId = repo.resolve(sha)
      val commitInfo = using(new RevWalk(repo)){ revWalk =>
        new CommitInfo(revWalk.parseCommit(objectId))
      }

      JsonFormat(ApiCommits(
        repositoryName = RepositoryName(repository),
        commitInfo     = commitInfo,
        diffs          = JGitUtil.getDiffs(git, Some(commitInfo.parents.head), commitInfo.id, false, true),
        author         = getAccount(commitInfo.authorName, commitInfo.authorEmailAddress),
        committer      = getAccount(commitInfo.committerName, commitInfo.committerEmailAddress),
        commentCount   = getCommitComment(repository.owner, repository.name, sha).size
      ))
    }
  })

  private def getAccount(userName: String, email: String): Account = {
    getAccountByMailAddress(email).getOrElse {
      Account(
        userName = userName,
        fullName = userName,
        mailAddress = email,
        password = "xxx",
        isAdmin = false,
        url = None,
        registeredDate = new java.util.Date(),
        updatedDate = new java.util.Date(),
        lastLoginDate = None,
        image = None,
        isGroupAccount = false,
        isRemoved = true,
        description = None
      )
    }
  }

  private def isEditable(owner: String, repository: String, author: String)(implicit context: Context): Boolean =
    hasDeveloperRole(owner, repository, context.loginAccount) || author == context.loginAccount.get.userName

  /**
    * non-GitHub compatible API for Jenkins-Plugin
    */
  get("/api/v3/repos/:owner/:repo/raw/*")(referrersOnly { repository =>
    val (id, path) = repository.splitPath(multiParams("splat").head)
    using(Git.open(getRepositoryDir(repository.owner, repository.name))){ git =>
      val revCommit = JGitUtil.getRevCommitFromId(git, git.getRepository.resolve(id))

      getPathObjectId(git, path, revCommit).map { objectId =>
        responseRawFile(git, objectId, path, repository)
      } getOrElse NotFound()
    }
  })

  /**
    * non-GitHub compatible API for listing plugins
    */
  get("/api/v3/gitbucket/plugins"){
    PluginRegistry().getPlugins().map{ApiPlugin(_)}
  }
}

