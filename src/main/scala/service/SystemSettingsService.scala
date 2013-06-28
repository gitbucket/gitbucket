package service

import util.Directory._
import SystemSettingsService._

trait SystemSettingsService {

  def saveSystemSettings(settings: SystemSettings): Unit = {
    val props = new java.util.Properties()
    props.setProperty(AllowAccountRegistration, settings.allowAccountRegistration.toString)
    props.store(new java.io.FileOutputStream(GitBucketConf), null)
  }


  def loadSystemSettings(): SystemSettings = {
    val props = new java.util.Properties()
    if(GitBucketConf.exists){
      props.load(new java.io.FileInputStream(GitBucketConf))
    }
    SystemSettings(getBoolean(props, "allow_account_registration"))
  }

}

object SystemSettingsService {

  case class SystemSettings(allowAccountRegistration: Boolean)

  private val AllowAccountRegistration = "allow_account_registration"

  private def getBoolean(props: java.util.Properties, key: String, default: Boolean = false): Boolean = {
    val value = props.getProperty(key)
    if(value == null || value.isEmpty){
      default
    } else {
      value.toBoolean
    }
  }

}
