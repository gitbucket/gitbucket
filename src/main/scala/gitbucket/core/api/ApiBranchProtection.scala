package gitbucket.core.api

import gitbucket.core.service.ProtectedBranchService
import org.json4s._

/** https://developer.github.com/v3/repos/#enabling-and-disabling-branch-protection */
case class ApiBranchProtection(
  url: Option[ApiPath], // for output
  enabled: Boolean,
  required_status_checks: Option[ApiBranchProtection.Status]
) {
  def status: ApiBranchProtection.Status = required_status_checks.getOrElse(ApiBranchProtection.statusNone)
}

object ApiBranchProtection {

  /** form for enabling-and-disabling-branch-protection */
  case class EnablingAndDisabling(protection: ApiBranchProtection)

  def apply(info: ProtectedBranchService.ProtectedBranchInfo): ApiBranchProtection =
    ApiBranchProtection(
      url = Some(
        ApiPath(
          s"/api/v3/repos/${info.owner}/${info.repository}/branches/${info.branch}/protection"
        )
      ),
      enabled = info.enabled,
      required_status_checks = Some(
        Status(
          Some(
            ApiPath(
              s"/api/v3/repos/${info.owner}/${info.repository}/branches/${info.branch}/protection/required_status_checks"
            )
          ),
          EnforcementLevel(info.enabled && info.contexts.nonEmpty, info.includeAdministrators),
          info.contexts,
          Some(
            ApiPath(
              s"/api/v3/repos/${info.owner}/${info.repository}/branches/${info.branch}/protection/required_status_checks/contexts"
            )
          )
        )
      )
    )
  val statusNone = Status(None, Off, Seq.empty, None)
  case class Status(
    url: Option[ApiPath], // for output
    enforcement_level: EnforcementLevel,
    contexts: Seq[String],
    contexts_url: Option[ApiPath] // for output
  )
  sealed class EnforcementLevel(val name: String)
  case object Off extends EnforcementLevel("off")
  case object NonAdmins extends EnforcementLevel("non_admins")
  case object Everyone extends EnforcementLevel("everyone")
  object EnforcementLevel {
    def apply(enabled: Boolean, includeAdministrators: Boolean): EnforcementLevel =
      if (enabled) {
        if (includeAdministrators) {
          Everyone
        } else {
          NonAdmins
        }
      } else {
        Off
      }
  }

  implicit val enforcementLevelSerializer = new CustomSerializer[EnforcementLevel](
    format =>
      (
        {
          case JString("off")        => Off
          case JString("non_admins") => NonAdmins
          case JString("everyone")   => Everyone
        }, {
          case x: EnforcementLevel => JString(x.name)
        }
    )
  )
}
