package gitbucket.core.api

import gitbucket.core.util.JGitUtil.FileInfo

case class ApiContents(`type`: String, name: String)

object ApiContents{
  def apply(fileInfo: FileInfo): ApiContents =
    if(fileInfo.isDirectory) ApiContents("dir", fileInfo.name)
    else ApiContents("file", fileInfo.name)
}