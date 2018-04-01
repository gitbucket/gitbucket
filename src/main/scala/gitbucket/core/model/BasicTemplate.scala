package gitbucket.core.model

protected[model] trait TemplateComponent { self: Profile =>
  import profile.api._

  trait BasicTemplate { self: Table[_] =>
    val userName = column[String]("USER_NAME")
    val repositoryName = column[String]("REPOSITORY_NAME")

    def byAccount(userName: String) = (this.userName === userName.bind)

    def byAccount(userName: Rep[String]) = (this.userName === userName)

    def byRepository(owner: String, repository: String) =
      (userName === owner.bind) && (repositoryName === repository.bind)

    def byRepository(userName: Rep[String], repositoryName: Rep[String]) =
      (this.userName === userName) && (this.repositoryName === repositoryName)
  }

  trait IssueTemplate extends BasicTemplate { self: Table[_] =>
    val issueId = column[Int]("ISSUE_ID")

    def byIssue(owner: String, repository: String, issueId: Int) =
      byRepository(owner, repository) && (this.issueId === issueId.bind)

    def byIssue(userName: Rep[String], repositoryName: Rep[String], issueId: Rep[Int]) =
      byRepository(userName, repositoryName) && (this.issueId === issueId)
  }

  trait LabelTemplate extends BasicTemplate { self: Table[_] =>
    val labelId = column[Int]("LABEL_ID")
    val labelName = column[String]("LABEL_NAME")

    def byLabel(owner: String, repository: String, labelId: Int) =
      byRepository(owner, repository) && (this.labelId === labelId.bind)

    def byLabel(userName: Rep[String], repositoryName: Rep[String], labelId: Rep[Int]) =
      byRepository(userName, repositoryName) && (this.labelId === labelId)

    def byLabel(owner: String, repository: String, labelName: String) =
      byRepository(owner, repository) && (this.labelName === labelName.bind)
  }

  trait PriorityTemplate extends BasicTemplate { self: Table[_] =>
    val priorityId = column[Int]("PRIORITY_ID")
    val priorityName = column[String]("PRIORITY_NAME")

    def byPriority(owner: String, repository: String, priorityId: Int) =
      byRepository(owner, repository) && (this.priorityId === priorityId.bind)

    def byPriority(userName: Rep[String], repositoryName: Rep[String], priorityId: Rep[Int]) =
      byRepository(userName, repositoryName) && (this.priorityId === priorityId)

    def byPriority(owner: String, repository: String, priorityName: String) =
      byRepository(owner, repository) && (this.priorityName === priorityName.bind)
  }

  trait MilestoneTemplate extends BasicTemplate { self: Table[_] =>
    val milestoneId = column[Int]("MILESTONE_ID")

    def byMilestone(owner: String, repository: String, milestoneId: Int) =
      byRepository(owner, repository) && (this.milestoneId === milestoneId.bind)

    def byMilestone(userName: Rep[String], repositoryName: Rep[String], milestoneId: Rep[Int]) =
      byRepository(userName, repositoryName) && (this.milestoneId === milestoneId)
  }

  trait CommitTemplate extends BasicTemplate { self: Table[_] =>
    val commitId = column[String]("COMMIT_ID")

    def byCommit(owner: String, repository: String, commitId: String) =
      byRepository(owner, repository) && (this.commitId === commitId)

    def byCommit(owner: Rep[String], repository: Rep[String], commitId: Rep[String]) =
      byRepository(userName, repositoryName) && (this.commitId === commitId)
  }

  trait BranchTemplate extends BasicTemplate { self: Table[_] =>
    val branch = column[String]("BRANCH")
    def byBranch(owner: String, repository: String, branchName: String) =
      byRepository(owner, repository) && (branch === branchName.bind)
    def byBranch(owner: Rep[String], repository: Rep[String], branchName: Rep[String]) =
      byRepository(owner, repository) && (this.branch === branchName)
  }
}
