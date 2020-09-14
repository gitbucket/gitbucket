package gitbucket.core.service

import gitbucket.core.model.AccountHighlighter
import gitbucket.core.model.Profile._
import gitbucket.core.model.Profile.profile.blockingApi._

trait AccountHighlighterService {
  self: AccountService =>

  def getAccountHighlighter(userName: String)(
    implicit s: Session
  ): Option[AccountHighlighter] = {
    AccountHighlighters filter (_.byPrimaryKey(userName)) firstOption
  }

  def addAccountHighlighter(userName: String, theme: String)(implicit s: Session): Unit = {
    AccountHighlighters insert AccountHighlighter(userName = userName, theme = theme)
  }

  def updateAccountHighlighter(userName: String, theme: String)(implicit s: Session): Unit = {
    AccountHighlighters
      .filter(_.byPrimaryKey(userName))
      .map(t => t.theme)
      .update(theme)
  }

  def addOrUpdateAccountHighlighter(userName: String, theme: String)(implicit s: Session): Unit = {
    getAccountHighlighter(userName) match {
      case Some(_) => updateAccountHighlighter(userName, theme)
      case _       => addAccountHighlighter(userName, theme)
    }
  }

}
