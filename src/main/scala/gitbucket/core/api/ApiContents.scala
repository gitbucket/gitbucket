package gitbucket.core.api

import java.util.Base64

import gitbucket.core.util.JGitUtil.FileInfo
import gitbucket.core.util.RepositoryName

case class ApiContents(
  `type`: String,
  name: String,
  path: String,
  sha: String,
  content: Option[String],
  encoding: Option[String]
)(repositoryName: RepositoryName) {
  val download_url = ApiPath(s"/api/v3/repos/${repositoryName.fullName}/raw/${sha}/${path}")
}

object ApiContents {
  def apply(fileInfo: FileInfo, repositoryName: RepositoryName, content: Option[Array[Byte]]): ApiContents = {
    if (fileInfo.isDirectory) {
      ApiContents("dir", fileInfo.name, fileInfo.path, fileInfo.commitId, None, None)(repositoryName)
    } else {
      content
        .map(
          arr =>
            ApiContents(
              "file",
              fileInfo.name,
              fileInfo.path,
              fileInfo.commitId,
              Some(Base64.getEncoder.encodeToString(arr)),
              Some("base64")
            )(repositoryName)
        )
        .getOrElse(ApiContents("file", fileInfo.name, fileInfo.path, fileInfo.commitId, None, None)(repositoryName))
    }
  }
}
