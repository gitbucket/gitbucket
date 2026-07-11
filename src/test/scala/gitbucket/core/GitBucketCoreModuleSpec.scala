package gitbucket.core

import java.sql.{Connection, DriverManager, Timestamp}
import java.time.Instant
import gitbucket.core.servlet.InitializeListener
import gitbucket.core.util.{Directory, JGitUtil}
import io.github.gitbucket.solidbase.Solidbase
import io.github.gitbucket.solidbase.model.Module
import liquibase.database.Database
import liquibase.database.core.{H2Database, MySQLDatabase, PostgresDatabase}
import org.apache.commons.io.FileUtils
import org.junit.runner.Description
import org.eclipse.jgit.api.Git
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.Tag
import org.testcontainers.mysql.MySQLContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

import scala.jdk.CollectionConverters.*
import scala.util.Using

object ExternalDBTest extends Tag("ExternalDBTest")

class GitBucketCoreModuleSpec extends AnyFunSuite {

  private case class AccountRow(
    userName: String,
    mailAddress: String,
    fullName: String,
    password: String,
    administrator: Boolean,
    groupAccount: Boolean,
    removed: Boolean
  )

  private case class RepositoryRow(
    userName: String,
    repositoryName: String,
    isPrivate: Boolean,
    defaultBranch: String,
    originUserName: Option[String],
    originRepositoryName: Option[String],
    parentUserName: Option[String],
    parentRepositoryName: Option[String]
  )

  private val orphanRepairStartVersion = "4.47.0.1"
  private val ownerUserName = "root"
  private val ownerRepositoryName = "repo-with-missing-parentage"
  private val missingOriginUserName = "missing-origin-user"
  private val missingOriginRepositoryName = "missing-origin-repo"
  private val missingParentUserName = "missing-parent-user"
  private val missingParentRepositoryName = "missing-parent-repo"
  private val expectedAccountNames = Set(ownerUserName, missingOriginUserName, missingParentUserName)
  private val expectedRepositoryKeys = Set(
    (ownerUserName, ownerRepositoryName),
    (missingOriginUserName, missingOriginRepositoryName),
    (missingParentUserName, missingParentRepositoryName)
  )
  private val ownerRepositoryDir = Directory.getRepositoryDir(ownerUserName, ownerRepositoryName)
  private val missingRepositoryDirs = Seq(
    Directory.getRepositoryDir(missingOriginUserName, missingOriginRepositoryName),
    Directory.getRepositoryDir(missingParentUserName, missingParentRepositoryName)
  )
  private val moduleBeforeOrphanRepair = new Module(
    GitBucketCoreModule.getModuleId,
    GitBucketCoreModule.getVersions.asScala
      .takeWhile(_.getVersion != orphanRepairStartVersion)
      .toList
      .asJava
  )
  private val fullModule = new Module(GitBucketCoreModule.getModuleId, GitBucketCoreModule.getVersions)

  private def migrate(conn: Connection, db: Database, module: Module): Unit =
    new Solidbase().migrate(conn, Thread.currentThread().getContextClassLoader(), db, module)

  private def withRepositoryDirCleanup[A](action: => A): A = {
    val dirs = ownerRepositoryDir +: missingRepositoryDirs
    dirs.foreach(FileUtils.deleteQuietly)
    try {
      action
    } finally {
      dirs.foreach(FileUtils.deleteQuietly)
    }
  }

  private def assertMissingRepositoryDirsCreated(): Unit = {
    assert(!ownerRepositoryDir.exists())

    missingRepositoryDirs.foreach { dir =>
      assert(dir.exists(), s"Expected repository directory to be created: ${dir.getAbsolutePath}")
      Using.resource(Git.open(dir)) { git =>
        assert(JGitUtil.isEmpty(git))
      }
    }
  }

  private def insertRepositoryWithMissingOriginAndParent(conn: Connection): Unit = {
    val now = Timestamp.from(Instant.parse("2026-07-11T00:00:00Z"))
    Using.resource(
      conn.prepareStatement(
        """
          |INSERT INTO REPOSITORY
          |(
          |  USER_NAME, REPOSITORY_NAME, PRIVATE, DEFAULT_BRANCH, REGISTERED_DATE, UPDATED_DATE, LAST_ACTIVITY_DATE,
          |  ORIGIN_USER_NAME, ORIGIN_REPOSITORY_NAME, PARENT_USER_NAME, PARENT_REPOSITORY_NAME
          |)
          |VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          |""".stripMargin
      )
    ) { statement =>
      statement.setString(1, ownerUserName)
      statement.setString(2, ownerRepositoryName)
      statement.setBoolean(3, false)
      statement.setString(4, "main")
      statement.setTimestamp(5, now)
      statement.setTimestamp(6, now)
      statement.setTimestamp(7, now)
      statement.setString(8, missingOriginUserName)
      statement.setString(9, missingOriginRepositoryName)
      statement.setString(10, missingParentUserName)
      statement.setString(11, missingParentRepositoryName)
      statement.executeUpdate()
    }
  }

  private def accountRows(conn: Connection): Seq[AccountRow] =
    Using.resource(
      conn.prepareStatement(
        """
          |SELECT USER_NAME, MAIL_ADDRESS, FULL_NAME, PASSWORD, ADMINISTRATOR, GROUP_ACCOUNT, REMOVED
          |FROM ACCOUNT
          |ORDER BY USER_NAME
          |""".stripMargin
      )
    ) { statement =>
      Using.resource(statement.executeQuery()) { rs =>
        Iterator
          .continually(rs.next())
          .takeWhile(identity)
          .map { _ =>
            AccountRow(
              userName = rs.getString("USER_NAME"),
              mailAddress = rs.getString("MAIL_ADDRESS"),
              fullName = rs.getString("FULL_NAME"),
              password = rs.getString("PASSWORD"),
              administrator = rs.getBoolean("ADMINISTRATOR"),
              groupAccount = rs.getBoolean("GROUP_ACCOUNT"),
              removed = rs.getBoolean("REMOVED")
            )
          }
          .toSeq
      }
    }

  private def repositoryRows(conn: Connection): Seq[RepositoryRow] =
    Using.resource(
      conn.prepareStatement(
        """
          |SELECT USER_NAME, REPOSITORY_NAME, PRIVATE, DEFAULT_BRANCH, ORIGIN_USER_NAME, ORIGIN_REPOSITORY_NAME,
          |       PARENT_USER_NAME, PARENT_REPOSITORY_NAME
          |FROM REPOSITORY
          |ORDER BY USER_NAME, REPOSITORY_NAME
          |""".stripMargin
      )
    ) { statement =>
      Using.resource(statement.executeQuery()) { rs =>
        Iterator
          .continually(rs.next())
          .takeWhile(identity)
          .map { _ =>
            RepositoryRow(
              userName = rs.getString("USER_NAME"),
              repositoryName = rs.getString("REPOSITORY_NAME"),
              isPrivate = rs.getBoolean("PRIVATE"),
              defaultBranch = rs.getString("DEFAULT_BRANCH"),
              originUserName = Option(rs.getString("ORIGIN_USER_NAME")),
              originRepositoryName = Option(rs.getString("ORIGIN_REPOSITORY_NAME")),
              parentUserName = Option(rs.getString("PARENT_USER_NAME")),
              parentRepositoryName = Option(rs.getString("PARENT_REPOSITORY_NAME"))
            )
          }
          .toSeq
      }
    }

  private def assertOrphanRepairMigration(conn: Connection, db: Database): Unit = {
    migrate(conn, db, moduleBeforeOrphanRepair)
    insertRepositoryWithMissingOriginAndParent(conn)
    migrate(conn, db, fullModule)
    InitializeListener.createMissingPrivateRepositories(conn)

    val accounts = accountRows(conn)
    assert(accounts.map(_.userName).toSet == expectedAccountNames)

    val ownerAccount = accounts.find(_.userName == ownerUserName).get
    assert(ownerAccount.mailAddress == "root@localhost")
    assert(ownerAccount.removed == false)

    val expectedMissingAccounts = Map(
      missingOriginUserName -> s"missing+$missingOriginUserName@invalid.local",
      missingParentUserName -> s"missing+$missingParentUserName@invalid.local"
    )
    accounts.filter(_.userName != ownerUserName).foreach { account =>
      assert(account.mailAddress == expectedMissingAccounts(account.userName))
      assert(account.fullName == account.userName)
      assert(account.password.length == 40)
      assert(account.administrator == false)
      assert(account.groupAccount == false)
      assert(account.removed)
    }

    val repositories = repositoryRows(conn)
    assert(repositories.map(row => row.userName -> row.repositoryName).toSet == expectedRepositoryKeys)

    val ownerRepository =
      repositories.find(row => row.userName == ownerUserName && row.repositoryName == ownerRepositoryName).get
    assert(ownerRepository.originUserName.contains(missingOriginUserName))
    assert(ownerRepository.originRepositoryName.contains(missingOriginRepositoryName))
    assert(ownerRepository.parentUserName.contains(missingParentUserName))
    assert(ownerRepository.parentRepositoryName.contains(missingParentRepositoryName))

    repositories.filterNot(row => row.userName == ownerUserName && row.repositoryName == ownerRepositoryName).foreach {
      repository =>
        assert(repository.isPrivate)
        assert(repository.defaultBranch == "main")
        assert(repository.originUserName.isEmpty)
        assert(repository.originRepositoryName.isEmpty)
        assert(repository.parentUserName.isEmpty)
        assert(repository.parentRepositoryName.isEmpty)
    }

    assertMissingRepositoryDirsCreated()
  }

  test("Migration H2") {
    new Solidbase().migrate(
      DriverManager.getConnection("jdbc:h2:mem:test", "sa", "sa"),
      Thread.currentThread().getContextClassLoader(),
      new H2Database(),
      new Module(GitBucketCoreModule.getModuleId, GitBucketCoreModule.getVersions)
    )
  }

  test("Migration H2 repairs missing origin and parent repositories before adding self-referential constraints") {
    withRepositoryDirCleanup {
      Using.resource(DriverManager.getConnection("jdbc:h2:mem:test-orphan-repair;DB_CLOSE_DELAY=-1", "sa", "sa")) {
        conn =>
          assertOrphanRepairMigration(conn, new H2Database())
      }
    }
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

    test(
      s"Migration MySQL $tag repairs missing origin and parent repositories before adding self-referential constraints",
      ExternalDBTest
    ) {
      val container = new MySQLContainer(s"mysql:$tag") {
        override def getDriverClassName = "org.mariadb.jdbc.Driver"
        override def getJdbcUrl: String = super.getJdbcUrl + "?permitMysqlScheme"
      }
      container.start()
      try {
        withRepositoryDirCleanup {
          Using.resource(
            DriverManager.getConnection(container.getJdbcUrl, container.getUsername, container.getPassword)
          ) { conn =>
            assertOrphanRepairMigration(conn, new MySQLDatabase())
          }
        }
      } finally {
        container.stop()
      }
    }
  }

  Seq("14", "18").foreach { tag =>
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

    test(
      s"Migration PostgreSQL $tag repairs missing origin and parent repositories before adding self-referential constraints",
      ExternalDBTest
    ) {
      val container = new PostgreSQLContainer(DockerImageName.parse(s"postgres:$tag"))

      container.start()
      try {
        withRepositoryDirCleanup {
          Using.resource(
            DriverManager.getConnection(container.getJdbcUrl, container.getUsername, container.getPassword)
          ) { conn =>
            assertOrphanRepairMigration(conn, new PostgresDatabase())
          }
        }
      } finally {
        container.stop()
      }
    }
  }

}
