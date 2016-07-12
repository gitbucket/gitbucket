package gitbucket.core.plugin

import gitbucket.core.controller.Context
import gitbucket.core.util.EmojiUtil

trait CompletionProposalProvider {

  val id: String
  val prefix: String
  val suffix: String = " "
  val values: Seq[String]
  val context: Seq[String]

  def template(implicit context: Context): String = "value"
  def additionalScript(implicit context: Context): String = ""
}

class EmojiCompletionProposalProvider extends CompletionProposalProvider {
  override val id: String = "emoji"
  override val values: Seq[String] = EmojiUtil.emojis.toSeq
  override val prefix: String = ":"
  override val suffix: String = ": "
  override val context: Seq[String] = Seq("wiki", "issues")
  override def template(implicit context: Context): String =
    s"""'<img src=\"${context.path}/assets/common/images/emojis/' + value + '.png\" class=\"emoji\"></img>' + value"""
}

class UserCompletionProposalProvider extends CompletionProposalProvider {
  override val id: String = "user"
  override val values: Seq[String] = Nil
  override val prefix: String = "@"
  override val context: Seq[String] = Seq("issues")
  override def template(implicit context: Context): String = "'@' + value"
  override def additionalScript(implicit context: Context): String =
    s"""$$.get('${context.path}/_user/proposals', { query: '' }, function (data) { user = data.options; });"""
}