package gitbucket.core

import java.sql.DriverManager
import io.github.gitbucket.solidbase.Solidbase
import io.github.gitbucket.solidbase.model.Module
import liquibase.database.core.{H2Database, MySQLDatabase, PostgresDatabase}
import org.junit.runner.Description
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.Tag
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.mysql.MySQLContainer
import org.testcontainers.utility.DockerImageName

object ExternalDBTest extends Tag("ExternalDBTest")

class GitBucketCoreModuleSpec extends AnyFunSuite {

  test("Migration H2") {
    new Solidbase().migrate(
      DriverManager.getConnection("jdbc:h2:mem:test", "sa", "sa"),
      Thread.currentThread().getContextClassLoader(),
      new H2Database(),
      new Module(GitBucketCoreModule.getModuleId, GitBucketCoreModule.getVersions)
    )
  }

  implicit private val suiteDescription: Description = Description.createSuiteDescription(getClass)

  Seq("8.4", "5.7").foreach { tag =>
    test(s"Migration MySQL $tag", ExternalDBTest) {
      val container = new MySQLContainer(s"mysql:$tag") {
        override def getDriverClassName = "org.mariadb.jdbc.Driver"
        override def getJdbcUrl: String = super.getJdbcUrl + "?permitMysqlScheme"
      }
      container.start()
      try {
        new Solidbase().migrate(
          DriverManager.getConnection(
            container.getJdbcUrl,
            container.getUsername,
            container.getPassword
          ),
          Thread.currentThread().getContextClassLoader(),
          new MySQLDatabase(),
          new Module(GitBucketCoreModule.getModuleId, GitBucketCoreModule.getVersions)
        )
      } finally {
        container.stop()
      }
    }
  }

  Seq("11", "10").foreach { tag =>
    test(s"Migration PostgreSQL $tag", ExternalDBTest) {
      val container = new PostgreSQLContainer(DockerImageName.parse(s"postgres:$tag"))

      container.start()
      try {
        new Solidbase().migrate(
          DriverManager.getConnection(container.getJdbcUrl, container.getUsername, container.getPassword),
          Thread.currentThread().getContextClassLoader(),
          new PostgresDatabase(),
          new Module(GitBucketCoreModule.getModuleId, GitBucketCoreModule.getVersions)
        )
      } finally {
        container.stop()
      }
    }
  }

}
