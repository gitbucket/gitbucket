package gitbucket.core.util

import gitbucket.core.service.SystemSettingsService.SshAddress

case class RepoBase(baseUrl:String, sshAddress:Option[SshAddress])
