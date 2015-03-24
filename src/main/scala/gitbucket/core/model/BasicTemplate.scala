package gitbucket.core.model

protected[model] trait TemplateComponent { self: Profile =>
  import profile.simple._

  trait BasicTemplate { self: Table[_] =>
    val userName = column[String]("USER_NAME")
    val repositoryName = column[String]("REPOSITORY_NAME")

    def byRepository(owner: String, repository: String) =
      (userName === owner.bind) && (repositoryName === repository.bind)

    def byRepository(userName: Column[String], repositoryName: Column[String]) =
      (this.userName === userName) && (this.repositoryName === repositoryName)
  }

  trait IssueTemplate extends BasicTemplate { self: Table[_] =>
    val issueId = column[Int]("ISSUE_ID")

    def byIssue(owner: String, repository: String, issueId: Int) =
      byRepository(owner, repository) && (this.issueId === issueId.bind)

    def byIssue(userName: Column[String], repositoryName: Column[String], issueId: Column[Int]) =
      byRepository(userName, repositoryName) && (this.issueId === issueId)
  }

  trait LabelTemplate extends BasicTemplate { self: Table[_] =>
    val labelId = column[Int]("LABEL_ID")

    def byLabel(owner: String, repository: String, labelId: Int) =
      byRepository(owner, repository) && (this.labelId === labelId.bind)

    def byLabel(userName: Column[String], repositoryName: Column[String], labelId: Column[Int]) =
      byRepository(userName, repositoryName) && (this.labelId === labelId)
  }

  trait MilestoneTemplate extends BasicTemplate { self: Table[_] =>
    val milestoneId = column[Int]("MILESTONE_ID")

    def byMilestone(owner: String, repository: String, milestoneId: Int) =
      byRepository(owner, repository) && (this.milestoneId === milestoneId.bind)

    def byMilestone(userName: Column[String], repositoryName: Column[String], milestoneId: Column[Int]) =
      byRepository(userName, repositoryName) && (this.milestoneId === milestoneId)
  }

  trait CommitTemplate extends BasicTemplate { self: Table[_] =>
    val commitId = column[String]("COMMIT_ID")

    def byCommit(owner: String, repository: String, commitId: String) =
      byRepository(owner, repository) && (this.commitId === commitId)

    def byCommit(owner: Column[String], repository: Column[String], commitId: Column[String]) =
      byRepository(userName, repositoryName) && (this.commitId === commitId)
  }

}
