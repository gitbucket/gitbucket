package gitbucket.core.api

import gitbucket.core.model.Label
import gitbucket.core.util.RepositoryName

/**
 * https://developer.github.com/v3/issues/labels/
 */
case class ApiLabel(name: String, color: String)(repositoryName: RepositoryName) {
  var url = ApiPath(s"/api/v3/repos/${repositoryName.fullName}/labels/${name}")
}

object ApiLabel {
  def apply(label: Label, repositoryName: RepositoryName): ApiLabel =
    ApiLabel(
      name = label.labelName,
      color = label.color
    )(repositoryName)
}
