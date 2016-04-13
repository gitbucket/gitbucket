package gitbucket.core.util

import java.io._
import java.sql._
import java.text.SimpleDateFormat
import ControlUtil._
import scala.collection.mutable.ListBuffer

/**
 * Provides implicit class which extends java.sql.Connection.
 * This is used in automatic migration in [[servlet.AutoUpdateListener]].
 */
object JDBCUtil {

  implicit class RichConnection(conn: Connection){

    def update(sql: String, params: Any*): Int = {
      execute(sql, params: _*){ stmt =>
        stmt.executeUpdate()
      }
    }

    def find[T](sql: String, params: Any*)(f: ResultSet => T): Option[T] = {
      execute(sql, params: _*){ stmt =>
        using(stmt.executeQuery()){ rs =>
          if(rs.next) Some(f(rs)) else None
        }
      }
    }

    def select[T](sql: String, params: Any*)(f: ResultSet => T): Seq[T] = {
      execute(sql, params: _*){ stmt =>
        using(stmt.executeQuery()){ rs =>
          val list = new ListBuffer[T]
          while(rs.next){
            list += f(rs)
          }
          list.toSeq
        }
      }
    }

    def selectInt(sql: String, params: Any*): Int = {
      execute(sql, params: _*){ stmt =>
        using(stmt.executeQuery()){ rs =>
          if(rs.next) rs.getInt(1) else 0
        }
      }
    }

    private def execute[T](sql: String, params: Any*)(f: (PreparedStatement) => T): T = {
      using(conn.prepareStatement(sql)){ stmt =>
        params.zipWithIndex.foreach { case (p, i) =>
          p match {
            case x: Int    => stmt.setInt(i + 1, x)
            case x: String => stmt.setString(i + 1, x)
          }
        }
        f(stmt)
      }
    }

    def export(targetTables: Seq[String]): File = {
      val dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
      val file = File.createTempFile("gitbucket-export-", ".sql")

      using(new FileOutputStream(file)) { out =>
        val dbMeta = conn.getMetaData
        val allTablesInDatabase = allTablesOrderByDependencies(dbMeta)

        allTablesInDatabase.foreach { tableName =>
          if (targetTables.contains(tableName)) {
            val sb = new StringBuilder()
            select(s"SELECT * FROM ${tableName}") { rs =>
              sb.append(s"INSERT INTO ${tableName} (")

              val rsMeta = rs.getMetaData
              val columns = (1 to rsMeta.getColumnCount).map { i =>
                (rsMeta.getColumnName(i), rsMeta.getColumnType(i))
              }
              sb.append(columns.map(_._1).mkString(", "))
              sb.append(") VALUES (")

              val values = columns.map { case (columnName, columnType) =>
                columnType match {
                  case Types.BOOLEAN   => rs.getBoolean(columnName)
                  case Types.VARCHAR | Types.CLOB | Types.CHAR => rs.getString(columnName)
                  case Types.INTEGER   => rs.getInt(columnName)
                  case Types.TIMESTAMP => rs.getTimestamp(columnName)
                }
              }

              val columnValues = values.map { value =>
                value match {
                  case x: String    => "'" + x.replace("'", "''") + "'"
                  case x: Timestamp => "'" + dateFormat.format(x) + "'"
                  case null         => "NULL"
                  case x            => x
                }
              }
              sb.append(columnValues.mkString(", "))
              sb.append(");\n")
            }

            out.write(sb.toString.getBytes("UTF-8"))
          }
        }
      }

      file
    }

    def allTableNames(): Seq[String] = {
      using(conn.getMetaData.getTables(null, null, "%", Seq("TABLE").toArray)) { rs =>
        val tableNames = new ListBuffer[String]
        while (rs.next) {
          val name = rs.getString("TABLE_NAME")
          if (name != "VERSIONS") {
            tableNames += name
          }
        }
        tableNames.toSeq
      }
    }

    private def parentTables(meta: DatabaseMetaData, tableName: String): Seq[String] = {
      using(meta.getImportedKeys(null, null, tableName)) { rs =>
        val parents = new ListBuffer[String]
        while (rs.next) {
          val tableName = rs.getString("PKTABLE_NAME")
          parents += tableName
          parents ++= parentTables(meta, tableName)
        }
        parents.distinct.toSeq
      }
    }

    private def allTablesOrderByDependencies(meta: DatabaseMetaData): Seq[String] = {
      val tables = allTableNames.map { tableName =>
        ((tableName, parentTables(meta, tableName)))
      }
      tables.sortWith { (a, b) =>
        b._2.contains(a._1) || !a._2.contains(b._1)
      }.map(_._1)
    }
  }

}
