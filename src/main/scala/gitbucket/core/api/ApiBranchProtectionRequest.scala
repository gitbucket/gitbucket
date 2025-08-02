package gitbucket.core.api

/** https://developer.github.com/v3/repos/#enabling-and-disabling-branch-protection */
case class ApiBranchProtectionRequest(
  enabled: Boolean,
  required_status_checks: Option[ApiBranchProtectionRequest.Status],
  restrictions: Option[ApiBranchProtectionRequest.Restrictions],
  enforce_admins: Option[Boolean]
)

object ApiBranchProtectionRequest {

  /** form for enabling-and-disabling-branch-protection */
  case class EnablingAndDisabling(protection: ApiBranchProtectionRequest)

  case class Status(
    contexts: Seq[String]
  )

  case class Restrictions(users: Seq[String])
}
