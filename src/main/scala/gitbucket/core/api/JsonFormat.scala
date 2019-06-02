package gitbucket.core.api

import java.time._
import java.time.format.DateTimeFormatter
import java.util.Date

import scala.util.Try

import org.json4s._
import org.json4s.jackson.Serialization

object JsonFormat {

  case class Context(baseUrl: String, sshUrl: Option[String])

  val parserISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")

  val jsonFormats = Serialization.formats(NoTypeHints) + new CustomSerializer[Date](
    format =>
      (
        {
          case JString(s) =>
            Try(Date.from(Instant.parse(s))).getOrElse(throw new MappingException("Can't convert " + s + " to Date"))
        }, { case x: Date => JString(OffsetDateTime.ofInstant(x.toInstant, ZoneId.of("UTC")).format(parserISO)) }
    )
  ) + FieldSerializer[ApiUser]() +
    FieldSerializer[ApiGroup]() +
    FieldSerializer[ApiPullRequest]() +
    FieldSerializer[ApiRepository]() +
    FieldSerializer[ApiCommitListItem.Parent]() +
    FieldSerializer[ApiCommitListItem]() +
    FieldSerializer[ApiCommitListItem.Commit]() +
    FieldSerializer[ApiCommitStatus]() +
    FieldSerializer[FieldSerializable]() +
    FieldSerializer[ApiCombinedCommitStatus]() +
    FieldSerializer[ApiPullRequest.Commit]() +
    FieldSerializer[ApiIssue]() +
    FieldSerializer[ApiComment]() +
    FieldSerializer[ApiContents]() +
    FieldSerializer[ApiLabel]() +
    FieldSerializer[ApiCommits]() +
    FieldSerializer[ApiCommits.Commit]() +
    FieldSerializer[ApiCommits.Tree]() +
    FieldSerializer[ApiCommits.Stats]() +
    FieldSerializer[ApiCommits.File]() +
    FieldSerializer[ApiRelease]() +
    FieldSerializer[ApiReleaseAsset]() +
    ApiBranchProtection.enforcementLevelSerializer

  def apiPathSerializer(c: Context) =
    new CustomSerializer[ApiPath](
      _ =>
        ({
          case JString(s) if s.startsWith(c.baseUrl) => ApiPath(s.substring(c.baseUrl.length))
          case JString(s)                            => throw new MappingException("Can't convert " + s + " to ApiPath")
        }, {
          case ApiPath(path) => JString(c.baseUrl + path)
        })
    )

  def sshPathSerializer(c: Context) =
    new CustomSerializer[SshPath](
      _ =>
        ({
          case JString(s) if c.sshUrl.exists(sshUrl => s.startsWith(sshUrl)) =>
            SshPath(s.substring(c.sshUrl.get.length))
          case JString(s) => throw new MappingException("Can't convert " + s + " to ApiPath")
        }, {
          case SshPath(path) =>
            c.sshUrl.map { sshUrl =>
              JString(sshUrl + path)
            } getOrElse JNothing
        })
    )

  /**
   * convert object to json string
   */
  def apply(obj: AnyRef)(implicit c: Context): String =
    Serialization.write(obj)(jsonFormats + apiPathSerializer(c) + sshPathSerializer(c))

}
