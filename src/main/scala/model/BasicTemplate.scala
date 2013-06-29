package model

import scala.slick.driver.H2Driver.simple._

protected[model] trait BasicTemplate { self: Table[_] =>
  def userName = column[String]("USER_NAME")
  def repositoryName = column[String]("REPOSITORY_NAME")

  def byRepository(owner: String, repository: String) =
    (userName is owner.bind) && (repositoryName is repository.bind)

  def byRepository(userName: Column[String], repositoryName: Column[String]) =
    (this.userName is userName) && (this.repositoryName is repositoryName)
}

protected[model] trait IssueTemplate extends BasicTemplate { self: Table[_] =>
  def issueId = column[Int]("ISSUE_ID")

  def byIssue(owner: String, repository: String, issueId: Int) =
    byRepository(owner, repository) && (this.issueId is issueId.bind)

  def byIssue(userName: Column[String], repositoryName: Column[String], issueId: Column[Int]) =
    byRepository(userName, repositoryName) && (this.issueId is issueId)
}

protected[model] trait LabelTemplate extends BasicTemplate { self: Table[_] =>
  def labelId = column[Int]("LABEL_ID")

  def byLabel(owner: String, repository: String, labelId: Int) =
    byRepository(owner, repository) && (this.labelId is labelId.bind)

  def byLabel(userName: Column[String], repositoryName: Column[String], labelId: Column[Int]) =
    byRepository(userName, repositoryName) && (this.labelId is labelId)
}

protected[model] trait MilestoneTemplate extends BasicTemplate { self: Table[_] =>
  def milestoneId = column[Int]("MILESTONE_ID")

  def byMilestone(owner: String, repository: String, milestoneId: Int) =
    byRepository(owner, repository) && (this.milestoneId is milestoneId.bind)

  def byMilestone(userName: Column[String], repositoryName: Column[String], milestoneId: Column[Int]) =
    byRepository(userName, repositoryName) && (this.milestoneId is milestoneId)
}