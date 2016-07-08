package gitbucket.core.plugin

import gitbucket.core.controller.Context
import gitbucket.core.service.RepositoryService.RepositoryInfo
import gitbucket.core.util.EmojiUtil

trait TextDecorator {

  def decorate(text: String)(implicit context: Context): String

  def decorate(text: String, repositoryInfo: RepositoryInfo)(implicit context: Context): String = decorate(text)

}

class EmojiDecorator extends TextDecorator {

  override def decorate(text: String)(implicit context: Context): String = EmojiUtil.convertEmojis(text)

}

