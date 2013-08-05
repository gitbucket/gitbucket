package service

import util.Directory._
import SystemSettingsService._

trait SystemSettingsService {

  def saveSystemSettings(settings: SystemSettings): Unit = {
    val props = new java.util.Properties()
    props.setProperty(AllowAccountRegistration, settings.allowAccountRegistration.toString)
    props.setProperty(Gravatar, settings.gravatar.toString)
    props.store(new java.io.FileOutputStream(GitBucketConf), null)
  }


  def loadSystemSettings(): SystemSettings = {
    val props = new java.util.Properties()
    if(GitBucketConf.exists){
      props.load(new java.io.FileInputStream(GitBucketConf))
    }
    SystemSettings(
      getBoolean(props, AllowAccountRegistration),
      getBoolean(props, Gravatar, true))
  }

}

object SystemSettingsService {

  case class SystemSettings(
    allowAccountRegistration: Boolean,
    gravatar: Boolean
  )

  private val AllowAccountRegistration = "allow_account_registration"
  private val Gravatar = "gravatar"

  private def getBoolean(props: java.util.Properties, key: String, default: Boolean = false): Boolean = {
    val value = props.getProperty(key)
    if(value == null || value.isEmpty){
      default
    } else {
      value.toBoolean
    }
  }

}
