package service

import scala.slick.driver.H2Driver.simple._
import Database.threadLocalSession
import scala.slick.jdbc.{StaticQuery => Q}
import Q.interpolation

import model._

trait IssuesService {
  def saveIssue(owner: String, repository: String, loginUser: String,
      title: String, content: String) = {
    // next id number
    val id = sql"SELECT ISSUE_ID + 1 FROM ISSUE_ID WHERE USER_NAME = $owner AND REPOSITORY_NAME = $repository FOR UPDATE".as[Int].first

    Issues insert Issue(
        owner,
        repository,
        id,
        loginUser,
        None,
        None,
        title,
        content,
        new java.sql.Date(System.currentTimeMillis),	// TODO
        new java.sql.Date(System.currentTimeMillis))

    // increment id
    IssueId filter { t =>
      (t.userName is owner.bind) && (t.repositoryName is repository.bind)
    } map (_.issueId) update(id)
  }

}