package gitbucket.core.api

import gitbucket.core.service.ProtectedBranchService
import org.json4s._

/** https://developer.github.com/v3/repos/#enabling-and-disabling-branch-protection */
case class ApiBranchProtectionResponse(
  url: Option[ApiPath], // for output
  enabled: Boolean,
  required_status_checks: Option[ApiBranchProtectionResponse.Status],
  restrictions: Option[ApiBranchProtectionResponse.Restrictions],
  enforce_admins: Option[ApiBranchProtectionResponse.EnforceAdmins]
) {
  def status: ApiBranchProtectionResponse.Status =
    required_status_checks.getOrElse(ApiBranchProtectionResponse.statusNone)
}

object ApiBranchProtectionResponse {

  case class EnforceAdmins(enabled: Boolean)

//  /** form for enabling-and-disabling-branch-protection */
//  case class EnablingAndDisabling(protection: ApiBranchProtectionResponse)

  def apply(info: ProtectedBranchService.ProtectedBranchInfo): ApiBranchProtectionResponse =
    ApiBranchProtectionResponse(
      url = Some(
        ApiPath(
          s"/api/v3/repos/${info.owner}/${info.repository}/branches/${info.branch}/protection"
        )
      ),
      enabled = info.enabled,
      required_status_checks = info.contexts.map { contexts =>
        Status(
          Some(
            ApiPath(
              s"/api/v3/repos/${info.owner}/${info.repository}/branches/${info.branch}/protection/required_status_checks"
            )
          ),
          EnforcementLevel(info.enabled && info.contexts.nonEmpty, info.enforceAdmins),
          contexts,
          Some(
            ApiPath(
              s"/api/v3/repos/${info.owner}/${info.repository}/branches/${info.branch}/protection/required_status_checks/contexts"
            )
          )
        )
      },
      restrictions = info.restrictionsUsers.map { restrictionsUsers =>
        Restrictions(restrictionsUsers)
      },
      enforce_admins = if (info.enabled) Some(EnforceAdmins(info.enforceAdmins)) else None
    )

  val statusNone: Status = Status(None, Off, Seq.empty, None)

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

  case class Restrictions(users: Seq[String])

  implicit val enforcementLevelSerializer: CustomSerializer[EnforcementLevel] =
    new CustomSerializer[EnforcementLevel](format =>
      (
        {
          case JString("off")        => Off
          case JString("non_admins") => NonAdmins
          case JString("everyone")   => Everyone
        },
        { case x: EnforcementLevel =>
          JString(x.name)
        }
      )
    )
}
