package gitbucket.core.api

import gitbucket.core.util.JGitUtil.FileInfo
import org.apache.commons.codec.binary.Base64

case class ApiContents(`type`: String, name: String, content: Option[String], encoding: Option[String])

object ApiContents{
  def apply(fileInfo: FileInfo, content: Option[Array[Byte]]): ApiContents = {
    if(fileInfo.isDirectory) {
      ApiContents("dir", fileInfo.name, None, None)
    } else {
      content.map(arr =>
        ApiContents("file", fileInfo.name, Some(Base64.encodeBase64String(arr)), Some("base64"))
      ).getOrElse(ApiContents("file", fileInfo.name, None, None))
    }
  }
}
