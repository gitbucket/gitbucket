package gitbucket.core.servlet

import java.sql.DriverManager

import gitbucket.core.GitBucketCoreModule
import io.github.gitbucket.solidbase.manager.JDBCVersionManager
import org.scalatest.funsuite.AnyFunSuite
import scala.util.Using

import scala.jdk.CollectionConverters.*

class InitializeListenerSpec extends AnyFunSuite {

  private val latestVersion = GitBucketCoreModule.getVersions.asScala.last.getVersion

  test("versionsTableExists is false before the VERSIONS table has been created") {
    Using.resource(DriverManager.getConnection("jdbc:h2:mem:versions-table-missing", "sa", "sa")) { conn =>
      assert(!InitializeListener.versionsTableExists(conn))
    }
  }

  test("versionsTableExists is true once JDBCVersionManager has initialized the VERSIONS table") {
    Using.resource(
      DriverManager.getConnection("jdbc:h2:mem:versions-table-present;DB_CLOSE_DELAY=-1", "sa", "sa")
    ) { conn =>
      new JDBCVersionManager(conn).initialize()
      assert(InitializeListener.versionsTableExists(conn))
    }
  }

  test("does not repair missing private repositories on a routine startup that is already fully migrated") {
    assert(!InitializeListener.shouldRepairMissingPrivateRepositories(Some(latestVersion), latestVersion))
  }

  test("repairs missing private repositories when upgrading from before 4.47.0.2 to the latest version") {
    assert(InitializeListener.shouldRepairMissingPrivateRepositories(Some("4.46.1"), latestVersion))
  }

  test("repairs missing private repositories when upgrading from exactly the version before the repair migration") {
    assert(InitializeListener.shouldRepairMissingPrivateRepositories(Some("4.47.0.1"), latestVersion))
  }

  test("does not repair missing private repositories when already at or past the repair migration") {
    assert(!InitializeListener.shouldRepairMissingPrivateRepositories(Some("4.47.0.2"), latestVersion))
    assert(!InitializeListener.shouldRepairMissingPrivateRepositories(Some("4.47.0.3"), latestVersion))
  }

  test("does not repair missing private repositories on a fresh install with no recorded previous version") {
    assert(!InitializeListener.shouldRepairMissingPrivateRepositories(None, latestVersion))
  }

}
