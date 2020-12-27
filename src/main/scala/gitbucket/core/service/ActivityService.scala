package gitbucket.core.service

import gitbucket.core.model.Activity
import gitbucket.core.util.JGitUtil
import gitbucket.core.model.Profile._
import gitbucket.core.util.Directory._
import org.json4s._
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization.{read, write}

import scala.util.Using
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID

import gitbucket.core.controller.Context
import gitbucket.core.model.activity.BaseActivityInfo
import org.apache.commons.io.input.ReversedLinesFileReader

import scala.collection.mutable.ListBuffer

trait ActivityService {
  self: RequestCache =>

  private implicit val formats = Serialization.formats(NoTypeHints)

  private def writeLog(activity: Activity): Unit = {
    Using.resource(new FileOutputStream(ActivityLog, true)) { out =>
      out.write((write(activity) + "\n").getBytes(StandardCharsets.UTF_8))
    }
  }

  def getActivitiesByUser(activityUserName: String, isPublic: Boolean)(implicit context: Context): List[Activity] = {
    if (!ActivityLog.exists()) {
      List.empty
    } else {
      val list = new ListBuffer[Activity]
      Using.resource(new ReversedLinesFileReader(ActivityLog, StandardCharsets.UTF_8)) { reader =>
        var json: String = null
        while (list.length < 50 && { json = reader.readLine(); json } != null) {
          val activity = read[Activity](json)
          if (activity.activityUserName == activityUserName) {
            if (isPublic == false) {
              list += activity
            } else {
              if (!getRepositoryInfoFromCache(activity.userName, activity.repositoryName)
                    .map(_.isPrivate)
                    .getOrElse(true)) {
                list += activity
              }
            }
          }
        }
      }
      list.toList
    }
  }

  def getRecentPublicActivities()(implicit context: Context): List[Activity] = {
    if (!ActivityLog.exists()) {
      List.empty
    } else {
      val list = new ListBuffer[Activity]
      Using.resource(new ReversedLinesFileReader(ActivityLog, StandardCharsets.UTF_8)) { reader =>
        var json: String = null
        while (list.length < 50 && { json = reader.readLine(); json } != null) {
          val activity = read[Activity](json)
          if (!getRepositoryInfoFromCache(activity.userName, activity.repositoryName)
                .map(_.isPrivate)
                .getOrElse(true)) {
            list += activity
          }
        }
      }
      list.toList
    }
  }

  def getRecentActivitiesByOwners(owners: Set[String])(implicit context: Context): List[Activity] = {
    if (!ActivityLog.exists()) {
      List.empty
    } else {
      val list = new ListBuffer[Activity]
      Using.resource(new ReversedLinesFileReader(ActivityLog, StandardCharsets.UTF_8)) { reader =>
        var json: String = null
        while (list.length < 50 && { json = reader.readLine(); json } != null) {
          val activity = read[Activity](json)
          if (owners.contains(activity.userName)) {
            list += activity
          } else if (!getRepositoryInfoFromCache(activity.userName, activity.repositoryName)
                       .map(_.isPrivate)
                       .getOrElse(true)) {
            list += activity
          }
        }
      }
      list.toList
    }
  }

  def recordActivity[T <: { def toActivity: Activity }](info: T): Unit = {
    import scala.language.reflectiveCalls
    writeLog(info.toActivity)
  }
}
