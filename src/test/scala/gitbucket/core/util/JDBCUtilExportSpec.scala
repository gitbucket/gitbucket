package gitbucket.core.util

import gitbucket.core.util.JDBCUtil.*
import org.scalatest.funsuite.AnyFunSuite

import java.nio.file.Files
import java.sql.DriverManager
import scala.util.Using

class JDBCUtilExportSpec extends AnyFunSuite {

  test("exportAsSQL exports TINYINT and SMALLINT columns") {
    org.h2.Driver.load()
    Using.resource(DriverManager.getConnection("jdbc:h2:mem:export-tinyint")) { conn =>
      conn.update("CREATE TABLE T (ID INT PRIMARY KEY, FLAG TINYINT, NUM SMALLINT)")
      conn.update("INSERT INTO T VALUES (1, 1, 300)")

      val file = conn.exportAsSQL(Seq("T"))
      try {
        assert(Files.readString(file.toPath).contains("INSERT INTO T (ID, FLAG, NUM) VALUES (1, 1, 300);"))
      } finally {
        file.delete()
      }
    }
  }
}
