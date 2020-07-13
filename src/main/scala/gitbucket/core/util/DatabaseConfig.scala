package gitbucket.core.util

import com.typesafe.config.ConfigFactory
import java.io.File

import Directory._
import ConfigUtil._
import com.github.takezoe.slick.blocking.{BlockingH2Driver, BlockingJdbcProfile, BlockingMySQLDriver}
import liquibase.database.AbstractJdbcDatabase
import liquibase.database.core.{H2Database, MariaDBDatabase, MySQLDatabase, PostgresDatabase}
import org.apache.commons.io.FileUtils

import scala.reflect.ClassTag

object DatabaseConfig {

  private lazy val config = {
    val file = new File(GitBucketHome, "database.conf")
    if (!file.exists) {
      FileUtils.write(
        file,
        """db {
          |  url = "jdbc:h2:${DatabaseHome};MVCC=true"
          |  user = "sa"
          |  password = "sa"
          |#  connectionTimeout = 30000
          |#  idleTimeout = 600000
          |#  maxLifetime = 1800000
          |#  minimumIdle = 10
          |#  maximumPoolSize = 10
          |}
          |""".stripMargin,
        "UTF-8"
      )
    }
    ConfigFactory.parseFile(file)
  }

  private lazy val dbUrl = getValue("db.url", config.getString) //config.getString("db.url")

  def url(directory: Option[String]): String =
    dbUrl.replace("${DatabaseHome}", directory.getOrElse(DatabaseHome))

  lazy val url: String = url(None)
  lazy val user: String = getValue("db.user", config.getString)
  lazy val password: String = getValue("db.password", config.getString)
  lazy val jdbcDriver: String = DatabaseType(url).jdbcDriver
  lazy val slickDriver: BlockingJdbcProfile = DatabaseType(url).slickDriver
  lazy val liquiDriver: AbstractJdbcDatabase = DatabaseType(url).liquiDriver
  lazy val connectionTimeout: Option[Long] = getOptionValue("db.connectionTimeout", config.getLong)
  lazy val idleTimeout: Option[Long] = getOptionValue("db.idleTimeout", config.getLong)
  lazy val maxLifetime: Option[Long] = getOptionValue("db.maxLifetime", config.getLong)
  lazy val minimumIdle: Option[Int] = getOptionValue("db.minimumIdle", config.getInt)
  lazy val maximumPoolSize: Option[Int] = getOptionValue("db.maximumPoolSize", config.getInt)

  private def getValue[T: ClassTag](path: String, f: String => T): T = {
    getConfigValue(path).getOrElse(f(path))
  }

  private def getOptionValue[T: ClassTag](path: String, f: String => T): Option[T] = {
    getConfigValue(path).orElse {
      if (config.hasPath(path)) Some(f(path)) else None
    }
  }

}

sealed trait DatabaseType {
  val jdbcDriver: String
  val slickDriver: BlockingJdbcProfile
  val liquiDriver: AbstractJdbcDatabase
}

object DatabaseType {

  def apply(url: String): DatabaseType = {
    if (url.startsWith("jdbc:h2:")) {
      H2
    } else if (url.startsWith("jdbc:mysql:")) {
      MySQL
    } else if (url.startsWith("jdbc:mariadb:")) {
      MariaDb
    } else if (url.startsWith("jdbc:postgresql:")) {
      PostgreSQL
    } else {
      throw new IllegalArgumentException(s"${url} is not supported.")
    }
  }

  object H2 extends DatabaseType {
    val jdbcDriver = "org.h2.Driver"
    val slickDriver = BlockingH2Driver
    val liquiDriver = new H2Database()
  }

  object MySQL extends DatabaseType {
    val jdbcDriver = "org.mariadb.jdbc.Driver"
    val slickDriver = BlockingMySQLDriver
    val liquiDriver = new MySQLDatabase()
  }

  object MariaDb extends DatabaseType {
    val jdbcDriver = "org.mariadb.jdbc.Driver"
    val slickDriver = BlockingMySQLDriver
    val liquiDriver = new MariaDBDatabase()
  }

  object PostgreSQL extends DatabaseType {
    val jdbcDriver = "org.postgresql.Driver2"
    val slickDriver = BlockingPostgresDriver
    val liquiDriver = new PostgresDatabase()
  }

  object BlockingPostgresDriver extends slick.jdbc.PostgresProfile with BlockingJdbcProfile {
    override def quoteIdentifier(id: String): String = {
      val s = new StringBuilder(id.length + 4) append '"'
      for (c <- id) if (c == '"') s append "\"\"" else s append c.toLower
      (s append '"').toString
    }
  }
}
