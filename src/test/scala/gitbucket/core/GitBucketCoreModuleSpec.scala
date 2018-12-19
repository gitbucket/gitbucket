package gitbucket.core

import java.sql.DriverManager
import java.time.Duration
import java.time.temporal.ChronoUnit.SECONDS

import com.dimafeng.testcontainers.GenericContainer
import io.github.gitbucket.solidbase.Solidbase
import io.github.gitbucket.solidbase.model.Module
import liquibase.database.core.{H2Database, MySQLDatabase, PostgresDatabase}
import org.junit.runner.Description
import org.scalatest.{FunSuite, Tag}
import org.testcontainers.containers.ContainerLaunchException
import org.testcontainers.containers.wait.strategy.{HostPortWaitStrategy, Wait}

object ExternalDBTest extends Tag("ExternalDBTest")

class GitBucketCoreModuleSpec extends FunSuite {

  test("Migration H2") {
    new Solidbase().migrate(
      DriverManager.getConnection("jdbc:h2:mem:test", "sa", "sa"),
      Thread.currentThread().getContextClassLoader(),
      new H2Database(),
      new Module(GitBucketCoreModule.getModuleId, GitBucketCoreModule.getVersions)
    )
  }

  implicit private val suiteDescription = Description.createSuiteDescription(getClass)

  test("Migration MySQL 5.7", ExternalDBTest) {
    val container = GenericContainer(
      "mysql:5.7",
      env = Map("MYSQL_ROOT_PASSWORD" -> "my-secret-pw", "MYSQL_DATABASE" -> "gitbucket"),
      waitStrategy = new HostPortWaitStrategy {
        override def waitUntilReady(): Unit = {
          super.waitUntilReady()

          def readyForConnections(retry: Int = 0): Boolean = {
            var con: java.sql.Connection = null
            try {
              con = DriverManager.getConnection(
                s"jdbc:mysql://${waitStrategyTarget.getContainerIpAddress}:${waitStrategyTarget.getMappedPort(3306)}/gitbucket?useSSL=false",
                "root",
                "my-secret-pw"
              )
              con.createStatement().execute("SELECT 1")
            } catch {
              case _: Exception if retry < 3 =>
                Thread.sleep(10000)
                readyForConnections(retry + 1)
              case _: Exception => false
            } finally {
              Option(con).foreach(_.close())
            }
          }

          if (!readyForConnections()) throw new ContainerLaunchException("Timed out")
        }
      }
    )

    container.starting()
    try {
      new Solidbase().migrate(
        DriverManager.getConnection(
          s"jdbc:mysql://${container.containerIpAddress}:${container.mappedPort(3306)}/gitbucket?useSSL=false",
          "root",
          "my-secret-pw"
        ),
        Thread.currentThread().getContextClassLoader(),
        new MySQLDatabase(),
        new Module(GitBucketCoreModule.getModuleId, GitBucketCoreModule.getVersions)
      )
    } finally {
      container.finished()
    }
  }

  test("Migration PostgreSQL 11", ExternalDBTest) {
    val container = GenericContainer(
      "postgres:11",
      env = Map("POSTGRES_PASSWORD" -> "mysecretpassword", "POSTGRES_DB" -> "gitbucket"),
      waitStrategy = Wait
        .forLogMessage(".*database system is ready to accept connections.*\\s", 2)
        .withStartupTimeout(Duration.of(60, SECONDS))
    )

    container.starting()
    try {
      new Solidbase().migrate(
        DriverManager.getConnection(
          s"jdbc:postgresql://${container.containerIpAddress}:${container.mappedPort(5432)}/gitbucket",
          "postgres",
          "mysecretpassword"
        ),
        Thread.currentThread().getContextClassLoader(),
        new PostgresDatabase(),
        new Module(GitBucketCoreModule.getModuleId, GitBucketCoreModule.getVersions)
      )
    } finally {
      container.finished()
    }
  }

  test("Migration PostgreSQL 10", ExternalDBTest) {
    val container = GenericContainer(
      "postgres:10",
      env = Map("POSTGRES_PASSWORD" -> "mysecretpassword", "POSTGRES_DB" -> "gitbucket"),
      waitStrategy = Wait
        .forLogMessage(".*database system is ready to accept connections.*\\s", 2)
        .withStartupTimeout(Duration.of(60, SECONDS))
    )

    container.starting()
    try {
      new Solidbase().migrate(
        DriverManager.getConnection(
          s"jdbc:postgresql://${container.containerIpAddress}:${container.mappedPort(5432)}/gitbucket",
          "postgres",
          "mysecretpassword"
        ),
        Thread.currentThread().getContextClassLoader(),
        new PostgresDatabase(),
        new Module(GitBucketCoreModule.getModuleId, GitBucketCoreModule.getVersions)
      )
    } finally {
      container.finished()
    }
  }

}
