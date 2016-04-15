package gitbucket.core.util

import com.typesafe.config.ConfigFactory
import Directory.DatabaseHome

object DatabaseConfig {

  private val config = ConfigFactory.load("database")
  private val localUrl = config.getString("db.local.url")
  private val serverUrl = config.getString("db.server.url")

  def url(directory: Option[String]): String = {
    (System.getProperty("h2.port") match {
      case null => localUrl
      case port => serverUrl.replace("${DatabasePort}", port)
    }).replace("${DatabaseHome}", directory.getOrElse(DatabaseHome))
  }

  val url: String = url(None)
  val user: String = config.getString("db.user")
  val password: String = config.getString("db.password")
  val driver: String = config.getString("db.driver")

}
