package gitbucket.core.util

import gitbucket.core.service.ServiceSpecBase
import gitbucket.core.util.JDBCUtil.*
import org.scalatest.funsuite.AnyFunSuite

class JDBCUtilSpec extends AnyFunSuite with ServiceSpecBase {

  test("allTableNames excludes INFORMATION_SCHEMA tables") {
    withTestDB { session =>
      val tableNames = session.conn.allTableNames()
      assert(tableNames.contains("ACCOUNT"))
      assert(tableNames.contains("REPOSITORY"))
      assert(!tableNames.contains("CONSTANTS"))
      assert(!tableNames.contains("INFORMATION_SCHEMA_CATALOG_NAME"))
    }
  }

  test("exportAsSQL exports all tables") {
    withTestDB { session =>
      val file = session.conn.exportAsSQL(session.conn.allTableNames())
      try {
        assert(file.exists())
      } finally {
        file.delete()
      }
    }
  }
}
