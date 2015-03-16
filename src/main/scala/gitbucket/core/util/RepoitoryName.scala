package gitbucket.core.util

case class RepositoryName(owner:String, name:String){
  val fullName = s"${owner}/${name}"
}

object RepositoryName{
  def apply(fullName: String): RepositoryName = {
    fullName.split("/").toList match {
      case owner :: name :: Nil => RepositoryName(owner, name)
      case _ => throw new IllegalArgumentException(s"${fullName} is not repositoryName (only 'owner/name')")
    }
  }
  def apply(repository: gitbucket.core.model.Repository): RepositoryName = RepositoryName(repository.userName, repository.repositoryName)
  def apply(repository: gitbucket.core.util.JGitUtil.RepositoryInfo): RepositoryName = RepositoryName(repository.owner, repository.name)
  def apply(repository: gitbucket.core.service.RepositoryService.RepositoryInfo): RepositoryName = RepositoryName(repository.owner, repository.name)
  def apply(repository: gitbucket.core.model.CommitStatus): RepositoryName = RepositoryName(repository.userName, repository.repositoryName)
}
