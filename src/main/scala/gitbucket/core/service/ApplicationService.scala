package gitbucket.core.service

import com.typesafe.config.ConfigFactory

trait ApplicationService {
  def getApplicationVersion() : String = {
    try  {
      ConfigFactory.load().getString("application.version")
    } catch {
      case e: Exception => null
    }
  }
}

object ApplicationService extends ApplicationService
