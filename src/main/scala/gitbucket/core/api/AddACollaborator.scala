package gitbucket.core.api

case class AddACollaborator(permission: String) {
  val role: String = permission match {
    case "admin" => "ADMIN"
    case "push"  => "DEVELOPER"
    case "pull"  => "GUEST"
  }
}
