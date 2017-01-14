package gitbucket.core.plugin

import gitbucket.core.controller.Context
import gitbucket.core.service.RepositoryService.RepositoryInfo

trait SuggestionProvider {

  val id: String
  val prefix: String
  val suffix: String = " "
  val context: Seq[String]

  def values(repository: RepositoryInfo): Seq[String]
  def template(implicit context: Context): String = "value"
  def additionalScript(implicit context: Context): String = ""

}

class UserNameSuggestionProvider extends SuggestionProvider {
  override val id: String = "user"
  override val prefix: String = "@"
  override val context: Seq[String] = Seq("issues")
  override def values(repository: RepositoryInfo): Seq[String] = Nil
  override def template(implicit context: Context): String = "'@' + value"
  override def additionalScript(implicit context: Context): String =
    s"""$$.get('${context.path}/_user/proposals', { query: '', user: true, group: false }, function (data) { user = data.options; });"""
}