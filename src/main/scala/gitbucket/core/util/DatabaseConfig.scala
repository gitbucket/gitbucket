package gitbucket.core.util

import com.typesafe.config.ConfigFactory
import java.io.File
import Directory._
import liquibase.database.AbstractJdbcDatabase
import liquibase.database.core.{PostgresDatabase, MySQLDatabase, H2Database}
import org.apache.commons.io.FileUtils

object DatabaseConfig {

  private lazy val config = {
    val file = new File(GitBucketHome, "database.conf")
    if(!file.exists){
      FileUtils.write(file,
        """db {
          |  url = "jdbc:h2:${DatabaseHome};MVCC=true"
          |  user = "sa"
          |  password = "sa"
          |}
          |""".stripMargin, "UTF-8")
    }
    ConfigFactory.parseFile(file)
  }

  private lazy val dbUrl = config.getString("db.url")

  def url(directory: Option[String]): String =
    dbUrl.replace("${DatabaseHome}", directory.getOrElse(DatabaseHome))

  lazy val url: String = url(None)
  lazy val user: String = config.getString("db.user")
  lazy val password: String = config.getString("db.password")
  lazy val jdbcDriver: String = DatabaseType(url).jdbcDriver
  lazy val slickDriver: slick.driver.JdbcProfile = DatabaseType(url).slickDriver
  lazy val liquiDriver: AbstractJdbcDatabase = DatabaseType(url).liquiDriver

}

sealed trait DatabaseType {
  val jdbcDriver: String
  val slickDriver: slick.driver.JdbcProfile
  val liquiDriver: AbstractJdbcDatabase
}

object DatabaseType {

  def apply(url: String): DatabaseType = {
    if(url.startsWith("jdbc:h2:")){
      H2
    } else if(url.startsWith("jdbc:mysql:")){
      MySQL
    } else if(url.startsWith("jdbc:postgresql:")){
      PostgreSQL
    } else {
      throw new IllegalArgumentException(s"${url} is not supported.")
    }
  }

  object H2 extends DatabaseType {
    val jdbcDriver = "org.h2.Driver"
    val slickDriver = slick.driver.H2Driver
    val liquiDriver = new H2Database()
  }

  object MySQL extends DatabaseType {
    val jdbcDriver = "com.mysql.jdbc.Driver"
    val slickDriver = slick.driver.MySQLDriver
    val liquiDriver = new MySQLDatabase()
  }

  object PostgreSQL extends DatabaseType {
    val jdbcDriver = "org.postgresql.Driver2"
    val slickDriver = new slick.driver.PostgresDriver {
      override def quoteIdentifier(id: String): String = {
        val s = new StringBuilder(id.length + 4) append '"'
        for(c <- id) if(c == '"') s append "\"\"" else s append c.toLower
        (s append '"').toString
      }
    }
    val liquiDriver = new PostgresDatabase()
  }
}
