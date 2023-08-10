package gitbucket.core.service

import gitbucket.core.model.Activity
import gitbucket.core.util.Directory._
import org.json4s._
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization.{read, write}

import scala.util.Using
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import gitbucket.core.controller.Context
import gitbucket.core.util.ConfigUtil
import org.apache.commons.io.input.ReversedLinesFileReader
import ActivityService._

import scala.collection.mutable.ListBuffer

trait ActivityService {
  self: RequestCache =>

  private implicit val formats: Formats = Serialization.formats(NoTypeHints)

  private def writeLog(activity: Activity): Unit = {
    Using.resource(new FileOutputStream(ActivityLog, true)) { out =>
      out.write((write(activity) + "\n").getBytes(StandardCharsets.UTF_8))
    }
  }

  def getActivitiesByUser(activityUserName: String, publicOnly: Boolean)(implicit context: Context): List[Activity] = {
    getActivities(includePublic = false) { activity =>
      if (activity.activityUserName == activityUserName) {
        !publicOnly || isPublicActivity(activity)
      } else false
    }
  }

  def getRecentPublicActivities()(implicit context: Context): List[Activity] = {
    getActivities(includePublic = true) { _ =>
      false
    }
  }

  def getRecentActivitiesByRepos(repos: Set[(String, String)])(implicit context: Context): List[Activity] = {
    getActivities(includePublic = true) { activity =>
      repos.exists {
        case (userName, repositoryName) =>
          activity.userName == userName && activity.repositoryName == repositoryName
      }
    }
  }

  private def getActivities(
    includePublic: Boolean
  )(filter: Activity => Boolean)(implicit context: Context): List[Activity] = {
    if (!isNewsFeedEnabled || !ActivityLog.exists()) {
      List.empty
    } else {
      val list = new ListBuffer[Activity]
      Using.resource(
        ReversedLinesFileReader
          .builder()
          .setFile(ActivityLog)
          .setCharset(StandardCharsets.UTF_8)
          .get()
      ) { reader =>
        var json: String = null
        while (list.length < 50 && {
                 json = reader.readLine();
                 json
               } != null) {
          val activity = read[Activity](json)
          if (filter(activity)) {
            list += activity
          } else if (includePublic && isPublicActivity(activity)) {
            list += activity
          }
        }
      }
      list.toList
    }
  }

  private def isPublicActivity(activity: Activity)(implicit context: Context): Boolean = {
    !getRepositoryInfoFromCache(activity.userName, activity.repositoryName).forall(_.isPrivate)
  }

  def recordActivity[T <: { def toActivity: Activity }](info: T): Unit = {
    import scala.language.reflectiveCalls
    writeLog(info.toActivity)
  }
}

object ActivityService {
  def isNewsFeedEnabled: Boolean =
    !ConfigUtil.getConfigValue[Boolean]("gitbucket.disableNewsFeed").getOrElse(false)
}
