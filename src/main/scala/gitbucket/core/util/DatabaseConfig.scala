package gitbucket.core.util

import com.typesafe.config.ConfigFactory
import Directory.DatabaseHome

object DatabaseConfig {

  private val config = ConfigFactory.load("database")
  private val dbUrl = config.getString("db.url")

  def url(directory: Option[String]): String =
    dbUrl.replace("${DatabaseHome}", directory.getOrElse(DatabaseHome))

  val url: String = url(None)
  val user: String = config.getString("db.user")
  val password: String = config.getString("db.password")
  val driver: String = config.getString("db.driver")

}
