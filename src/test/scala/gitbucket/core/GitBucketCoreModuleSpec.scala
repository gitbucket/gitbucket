package gitbucket.core

import java.sql.{Clob, Connection, DriverManager, Timestamp, Types}
import java.time.Instant
import java.util.Locale
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

  private case class ColumnMetadata(name: String, sqlType: Int, size: Int, autoIncrement: Boolean)
  private case class TableSnapshot(columns: Seq[String], rows: Seq[Seq[String]])

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
  private val schemaPreservationAuthor = "shakespeare"
  private val schemaPreservationConstraints = "limited"
  private val schemaPreservationOwnerUserName = "preserve-owner"
  private val schemaPreservationActorUserName = "preserve-actor"
  private val schemaPreservationRepositoryName = "preserve-repository"
  private val schemaPreservationDefaultBranch = "main"
  private val schemaPreservationRequestBranch = "feature-preserve"
  private val schemaPreservationIssueId = 101
  private val schemaPreservationLabelId = 201
  private val schemaPreservationMilestoneId = 301
  private val schemaPreservationPriorityId = 401
  private val schemaPreservationFieldId = 501
  private val schemaPreservationOldLineNumber = 611
  private val schemaPreservationNewLineNumber = 622
  private val schemaPreservationOriginalOldLine = 633
  private val schemaPreservationOriginalNewLine = 644
  private val schemaPreservationCommitId = "1111111111111111111111111111111111111111"
  private val schemaPreservationOriginalCommitId = "2222222222222222222222222222222222222222"
  private val schemaPreservationCommitIdFrom = "3333333333333333333333333333333333333333"
  private val schemaPreservationCommitIdTo = "4444444444444444444444444444444444444444"
  private val schemaPreservationMergedCommitIds =
    "5555555555555555555555555555555555555555,6666666666666666666666666666666666666666"
  private val schemaPreservationTag = "v1.0.0"
  private val schemaPreservationUploader = "preserve-uploader"
  private val schemaPreservationWebHookUrl = "https://example.invalid/gitbucket-hook"
  private val schemaPreservationExternalIssuesUrl = "https://example.invalid/issues"
  private val schemaPreservationExternalWikiUrl = "https://example.invalid/wiki"
  private val fixedTimestamp = Timestamp.from(Instant.parse("2026-07-11T00:00:00Z"))
  private val schemaChangedTableNames = Seq(
    "REPOSITORY",
    "COLLABORATOR",
    "COMMIT_STATUS",
    "COMMIT_COMMENT",
    "LABEL",
    "MILESTONE",
    "ISSUE",
    "ISSUE_ID",
    "DEPLOY_KEY",
    "PRIORITY",
    "RELEASE_TAG",
    "WEB_HOOK",
    "CUSTOM_FIELD",
    "PROTECTED_BRANCH",
    "ISSUE_COMMENT",
    "ISSUE_LABEL",
    "PULL_REQUEST",
    "ISSUE_ASSIGNEE",
    "RELEASE_ASSET"
  )
  private val schemaPreservationSnapshotTables = "ACCOUNT" +: schemaChangedTableNames
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
      statement.setTimestamp(5, fixedTimestamp)
      statement.setTimestamp(6, fixedTimestamp)
      statement.setTimestamp(7, fixedTimestamp)
      statement.setString(8, missingOriginUserName)
      statement.setString(9, missingOriginRepositoryName)
      statement.setString(10, missingParentUserName)
      statement.setString(11, missingParentRepositoryName)
      statement.executeUpdate()
    }
  }

  private def canonicalColumnName(name: String): String =
    name.toUpperCase(Locale.ROOT)

  private def columnMetadata(conn: Connection, tableName: String): Seq[ColumnMetadata] =
    Using.resource(conn.prepareStatement(s"SELECT * FROM ${tableName} WHERE 1 = 0")) { statement =>
      Using.resource(statement.executeQuery()) { rs =>
        val metadata = rs.getMetaData
        (1 to metadata.getColumnCount).map { i =>
          ColumnMetadata(
            name = canonicalColumnName(metadata.getColumnName(i)),
            sqlType = metadata.getColumnType(i),
            size = metadata.getPrecision(i),
            autoIncrement = metadata.isAutoIncrement(i)
          )
        }
      }
    }

  private def generatedValue(tableName: String, column: ColumnMetadata): Any =
    column.name match {
      case "AUTHOR"                   => schemaPreservationAuthor
      case "CONSTRAINTS"              => schemaPreservationConstraints
      case "USER_NAME"                => schemaPreservationOwnerUserName
      case "REPOSITORY_NAME"          => schemaPreservationRepositoryName
      case "ORIGIN_USER_NAME"         => schemaPreservationOwnerUserName
      case "ORIGIN_REPOSITORY_NAME"   => schemaPreservationRepositoryName
      case "PARENT_USER_NAME"         => schemaPreservationOwnerUserName
      case "PARENT_REPOSITORY_NAME"   => schemaPreservationRepositoryName
      case "COLLABORATOR_NAME"        => schemaPreservationActorUserName
      case "OPENED_USER_NAME"         => schemaPreservationActorUserName
      case "COMMENTED_USER_NAME"      => schemaPreservationActorUserName
      case "ASSIGNEE_USER_NAME"       => schemaPreservationActorUserName
      case "CREATOR"                  => schemaPreservationActorUserName
      case "REQUEST_USER_NAME"        => schemaPreservationOwnerUserName
      case "REQUEST_REPOSITORY_NAME"  => schemaPreservationRepositoryName
      case "REQUEST_BRANCH"           => schemaPreservationRequestBranch
      case "BRANCH"                   => schemaPreservationDefaultBranch
      case "DEFAULT_BRANCH"           => schemaPreservationDefaultBranch
      case "TAG"                      => schemaPreservationTag
      case "ISSUE_ID"                 => schemaPreservationIssueId
      case "LABEL_ID"                 => schemaPreservationLabelId
      case "MILESTONE_ID"             => schemaPreservationMilestoneId
      case "PRIORITY_ID"              => schemaPreservationPriorityId
      case "FIELD_ID"                 => schemaPreservationFieldId
      case "OLD_LINE_NUMBER"          => schemaPreservationOldLineNumber
      case "NEW_LINE_NUMBER"          => schemaPreservationNewLineNumber
      case "ORIGINAL_OLD_LINE"        => schemaPreservationOriginalOldLine
      case "ORIGINAL_NEW_LINE"        => schemaPreservationOriginalNewLine
      case "URL"                      => schemaPreservationWebHookUrl
      case "COMMIT_ID"                => schemaPreservationCommitId
      case "ORIGINAL_COMMIT_ID"       => schemaPreservationOriginalCommitId
      case "COMMIT_ID_FROM"           => schemaPreservationCommitIdFrom
      case "COMMIT_ID_TO"             => schemaPreservationCommitIdTo
      case "MERGED_COMMIT_IDS"        => schemaPreservationMergedCommitIds
      case "EXTERNAL_ISSUES_URL"      => schemaPreservationExternalIssuesUrl
      case "EXTERNAL_WIKI_URL"        => schemaPreservationExternalWikiUrl
      case "ADMINISTRATOR"            => true
      case "GROUP_ACCOUNT"            => false
      case "REMOVED"                  => true
      case "PRIVATE"                  => true
      case "ALLOW_FORK"               => false
      case "SAFE_MODE"                => false
      case "ALLOW_WRITE"              => false
      case "CLOSED"                   => true
      case "PULL_REQUEST"             => false
      case "IS_DEFAULT"               => false
      case "ENABLE_FOR_ISSUES"        => true
      case "ENABLE_FOR_PULL_REQUESTS" => false
      case "STATUS_CHECK_ADMIN"       => true
      case "REQUIRED_STATUS_CHECK"    => false
      case "RESTRICTIONS"             => true
      case "IS_DRAFT"                 => true
      case "ORDERING"                 => 808
      case "SIZE"                     => 909L
      case "WIKI_OPTION"              => "PUBLIC"
      case "ISSUES_OPTION"            => "PUBLIC"
      case "MERGE_OPTIONS"            => "merge-commit,squash,rebase"
      case "DEFAULT_MERGE_OPTION"     => "merge-commit"
      case "COLOR"                    => "ccddee"
      case "PRIORITY_NAME"            => "Preserve Priority"
      case "LABEL_NAME"               => "preserve-label"
      case "PUBLIC_KEY"               => "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQCpreservationkey"
      case "TOKEN"                    => "preserve-token"
      case "CTYPE"                    => "json"
      case "TITLE"                    => s"${tableName.toLowerCase}-title"
      case "CONTENT"                  => s"${tableName.toLowerCase}-content"
      case "DESCRIPTION"              => s"${tableName.toLowerCase}-description"
      case "FILE_NAME"                => "preserve.txt"
      case "ACTION"                   => "comment"
      case "STATE"                    => "success"
      case "CONTEXT"                  => "ci/preserve"
      case "FIELD_NAME"               => "preserve-field"
      case "FIELD_TYPE"               => "text"
      case "ROLE"                     => "ADMIN"
      case "NAME"                     => s"${tableName.toLowerCase}-name"
      case "LABEL"                    => s"${tableName.toLowerCase}-label"
      case "UPLOADER"                 => schemaPreservationUploader
      case _                          =>
        column.sqlType match {
          case Types.TIMESTAMP | Types.TIMESTAMP_WITH_TIMEZONE | Types.DATE =>
            fixedTimestamp
          case _ =>
            throw new IllegalStateException(
              s"Unsupported generated value for ${tableName}.${column.name} (SQL type ${column.sqlType})"
            )
        }
    }

  private def setStatementValue(statement: java.sql.PreparedStatement, index: Int, value: Any): Unit =
    value match {
      case x: String    => statement.setString(index, x)
      case x: Int       => statement.setInt(index, x)
      case x: Long      => statement.setLong(index, x)
      case x: Boolean   => statement.setBoolean(index, x)
      case x: Timestamp => statement.setTimestamp(index, x)
      case x            => statement.setObject(index, x)
    }

  private def insertRow(conn: Connection, tableName: String, overrides: Map[String, Any] = Map.empty): Unit = {
    val columns = columnMetadata(conn, tableName).filterNot(_.autoIncrement)
    val normalizedOverrides = overrides.map { case (name, value) => canonicalColumnName(name) -> value }
    val sql =
      s"INSERT INTO ${tableName} (${columns.map(_.name).mkString(", ")}) VALUES (${List.fill(columns.size)("?").mkString(", ")})"

    Using.resource(conn.prepareStatement(sql)) { statement =>
      columns.zipWithIndex.foreach { case (column, index) =>
        setStatementValue(
          statement,
          index + 1,
          normalizedOverrides.getOrElse(column.name, generatedValue(tableName, column))
        )
      }
      statement.executeUpdate()
    }
  }

  private def selectSingleInt(conn: Connection, sql: String, params: Seq[Any]): Int =
    Using.resource(conn.prepareStatement(sql)) { statement =>
      params.zipWithIndex.foreach { case (value, index) =>
        setStatementValue(statement, index + 1, value)
      }
      Using.resource(statement.executeQuery()) { rs =>
        if (!rs.next()) {
          throw new IllegalStateException(s"No rows returned for query: ${sql}")
        }
        rs.getInt(1)
      }
    }

  private def snapshotTable(conn: Connection, tableName: String, columns: Seq[String] = Nil): TableSnapshot = {
    val selectedColumns =
      if (columns.nonEmpty) columns.map(canonicalColumnName)
      else columnMetadata(conn, tableName).map(_.name)

    val sql = s"SELECT ${selectedColumns.mkString(", ")} FROM ${tableName}"
    Using.resource(conn.prepareStatement(sql)) { statement =>
      Using.resource(statement.executeQuery()) { rs =>
        val rows = Iterator
          .continually(rs.next())
          .takeWhile(identity)
          .map { _ =>
            selectedColumns.map { columnName =>
              val value = rs.getObject(columnName)
              if (rs.wasNull()) {
                "<NULL>"
              } else {
                value match {
                  case x: Timestamp => x.toLocalDateTime.toString
                  case x: Clob      => x.getSubString(1, x.length().toInt)
                  case x            => x.toString
                }
              }
            }
          }
          .toSeq
          .sortBy(_.mkString("\u0000"))

        TableSnapshot(selectedColumns, rows)
      }
    }
  }

  private def insertSchemaPreservationData(conn: Connection): Unit = {
    insertRow(
      conn,
      "ACCOUNT",
      Map(
        "USER_NAME" -> schemaPreservationOwnerUserName,
        "MAIL_ADDRESS" -> "preserve-owner@example.com",
        "PASSWORD" -> "owner-password",
        "FULL_NAME" -> "Preserve Owner",
        "ADMINISTRATOR" -> false,
        "REGISTERED_DATE" -> fixedTimestamp,
        "UPDATED_DATE" -> fixedTimestamp,
        "LAST_LOGIN_DATE" -> fixedTimestamp,
        "IMAGE" -> "owner.png",
        "GROUP_ACCOUNT" -> false,
        "REMOVED" -> false,
        "DESCRIPTION" -> "owner description",
        "URL" -> "https://example.invalid/owner"
      )
    )
    insertRow(
      conn,
      "ACCOUNT",
      Map(
        "USER_NAME" -> schemaPreservationActorUserName,
        "MAIL_ADDRESS" -> "preserve-actor@example.com",
        "PASSWORD" -> "actor-password",
        "FULL_NAME" -> "Preserve Actor",
        "ADMINISTRATOR" -> false,
        "REGISTERED_DATE" -> fixedTimestamp,
        "UPDATED_DATE" -> fixedTimestamp,
        "LAST_LOGIN_DATE" -> fixedTimestamp,
        "IMAGE" -> "actor.png",
        "GROUP_ACCOUNT" -> false,
        "REMOVED" -> false,
        "DESCRIPTION" -> "actor description",
        "URL" -> "https://example.invalid/actor"
      )
    )

    insertRow(
      conn,
      "REPOSITORY",
      Map(
        "PRIVATE" -> false,
        "DESCRIPTION" -> "repository description",
        "DEFAULT_BRANCH" -> schemaPreservationDefaultBranch,
        "REGISTERED_DATE" -> fixedTimestamp,
        "UPDATED_DATE" -> fixedTimestamp,
        "LAST_ACTIVITY_DATE" -> fixedTimestamp,
        "EXTERNAL_ISSUES_URL" -> schemaPreservationExternalIssuesUrl,
        "ALLOW_FORK" -> true,
        "WIKI_OPTION" -> "PUBLIC",
        "ISSUES_OPTION" -> "PUBLIC",
        "EXTERNAL_WIKI_URL" -> schemaPreservationExternalWikiUrl,
        "MERGE_OPTIONS" -> "merge-commit,squash,rebase",
        "DEFAULT_MERGE_OPTION" -> "merge-commit",
        "SAFE_MODE" -> true
      )
    )

    insertRow(conn, "COLLABORATOR")
    insertRow(conn, "COMMIT_STATUS", Map("TARGET_URL" -> "https://example.invalid/status"))
    insertRow(conn, "COMMIT_COMMENT")
    insertRow(conn, "LABEL", Map("DESCRIPTION" -> "label description"))
    val labelId = selectSingleInt(
      conn,
      "SELECT LABEL_ID FROM LABEL WHERE USER_NAME = ? AND REPOSITORY_NAME = ? AND LABEL_NAME = ?",
      Seq(schemaPreservationOwnerUserName, schemaPreservationRepositoryName, "preserve-label")
    )

    insertRow(conn, "MILESTONE")
    val milestoneId = selectSingleInt(
      conn,
      "SELECT MILESTONE_ID FROM MILESTONE WHERE USER_NAME = ? AND REPOSITORY_NAME = ? AND TITLE = ?",
      Seq(schemaPreservationOwnerUserName, schemaPreservationRepositoryName, "milestone-title")
    )

    insertRow(conn, "PRIORITY", Map("ORDERING" -> 7))
    val priorityId = selectSingleInt(
      conn,
      "SELECT PRIORITY_ID FROM PRIORITY WHERE USER_NAME = ? AND REPOSITORY_NAME = ? AND PRIORITY_NAME = ?",
      Seq(schemaPreservationOwnerUserName, schemaPreservationRepositoryName, "Preserve Priority")
    )

    insertRow(conn, "ISSUE_ID", Map("ISSUE_ID" -> 777))
    insertRow(
      conn,
      "ISSUE",
      Map(
        "MILESTONE_ID" -> milestoneId,
        "PRIORITY_ID" -> priorityId,
        "PULL_REQUEST" -> true,
        "TITLE" -> "preserve issue",
        "CONTENT" -> "preserve issue content",
        "REGISTERED_DATE" -> fixedTimestamp,
        "UPDATED_DATE" -> fixedTimestamp,
        "CLOSED" -> false
      )
    )
    insertRow(conn, "DEPLOY_KEY", Map("ALLOW_WRITE" -> true))
    insertRow(conn, "RELEASE_TAG", Map("TARGET_COMMITISH" -> schemaPreservationDefaultBranch))
    insertRow(conn, "WEB_HOOK")
    insertRow(conn, "CUSTOM_FIELD", Map("ENABLE_ISSUE" -> true, "ENABLE_PR" -> true))
    insertRow(conn, "PROTECTED_BRANCH")
    insertRow(conn, "ISSUE_COMMENT")
    insertRow(conn, "ISSUE_LABEL", Map("LABEL_ID" -> labelId))
    insertRow(conn, "PULL_REQUEST")
    insertRow(conn, "ISSUE_ASSIGNEE")
    insertRow(conn, "RELEASE_ASSET", Map("SIZE" -> 12345L))
  }

  private def assertRepositoryDataPreservedBy447Migrations(conn: Connection, db: Database): Unit = {
    migrate(conn, db, moduleBeforeOrphanRepair)
    insertSchemaPreservationData(conn)

    val beforeSnapshots =
      schemaPreservationSnapshotTables.map(tableName => tableName -> snapshotTable(conn, tableName)).toMap
    val repositoryColumnsBeforeMigration = beforeSnapshots("REPOSITORY").columns

    migrate(conn, db, fullModule)

    schemaPreservationSnapshotTables.foreach { tableName =>
      val expected = beforeSnapshots(tableName)
      val actual =
        if (tableName == "REPOSITORY") snapshotTable(conn, tableName, repositoryColumnsBeforeMigration)
        else snapshotTable(conn, tableName)

      assert(actual.columns == expected.columns, s"${tableName} columns changed unexpectedly")
      assert(actual.rows == expected.rows, s"${tableName} rows changed unexpectedly")
    }

    val repositoryIdSnapshot =
      snapshotTable(conn, "REPOSITORY", Seq("USER_NAME", "REPOSITORY_NAME", "REPOSITORY_ID"))
    val repositoryIds = repositoryIdSnapshot.rows.map(_.last)
    assert(repositoryIds.forall(id => id.nonEmpty && id != "<NULL>" && id != "0"))
    assert(repositoryIds.distinct.size == repositoryIds.size)
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

  test("Migration H2 ensure repository data is preserved after 4.47 schema changes") {
    Using.resource(DriverManager.getConnection("jdbc:h2:mem:test-schema-preservation;DB_CLOSE_DELAY=-1", "sa", "sa")) {
      conn =>
        assertRepositoryDataPreservedBy447Migrations(conn, new H2Database())
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

    test(s"Migration MySQL $tag ensure repository data is preserved after 4.47 schema changes", ExternalDBTest) {
      val container = new MySQLContainer(s"mysql:$tag") {
        override def getDriverClassName = "org.mariadb.jdbc.Driver"
        override def getJdbcUrl: String = super.getJdbcUrl + "?permitMysqlScheme"
      }
      container.start()
      try {
        Using.resource(
          DriverManager.getConnection(container.getJdbcUrl, container.getUsername, container.getPassword)
        ) { conn =>
          assertRepositoryDataPreservedBy447Migrations(conn, new MySQLDatabase())
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

    test(
      s"Migration PostgreSQL $tag ensure repository data is preserved after 4.47 schema changes",
      ExternalDBTest
    ) {
      val container = new PostgreSQLContainer(DockerImageName.parse(s"postgres:$tag"))

      container.start()
      try {
        Using.resource(
          DriverManager.getConnection(container.getJdbcUrl, container.getUsername, container.getPassword)
        ) { conn =>
          assertRepositoryDataPreservedBy447Migrations(conn, new PostgresDatabase())
        }
      } finally {
        container.stop()
      }
    }
  }

}
