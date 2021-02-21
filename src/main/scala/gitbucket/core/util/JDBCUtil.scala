package gitbucket.core.util

import java.io._
import java.sql._
import java.text.SimpleDateFormat
import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer
import scala.util.Using

/**
 * Provides implicit class which extends java.sql.Connection.
 * This is used in following points:
 *
 * - Automatic migration in [[gitbucket.core.servlet.InitializeListener]]
 * - Data importing / exporting in [[gitbucket.core.controller.SystemSettingsController]] and [[gitbucket.core.controller.FileUploadController]]
 */
object JDBCUtil {

  implicit class RichConnection(private val conn: Connection) extends AnyVal {

    def update(sql: String, params: Any*): Int = {
      execute(sql, params: _*) { stmt =>
        stmt.executeUpdate()
      }
    }

    def find[T](sql: String, params: Any*)(f: ResultSet => T): Option[T] = {
      execute(sql, params: _*) { stmt =>
        Using.resource(stmt.executeQuery()) { rs =>
          if (rs.next) Some(f(rs)) else None
        }
      }
    }

    def select[T](sql: String, params: Any*)(f: ResultSet => T): Seq[T] = {
      execute(sql, params: _*) { stmt =>
        Using.resource(stmt.executeQuery()) { rs =>
          val list = new ListBuffer[T]
          while (rs.next) {
            list += f(rs)
          }
          list.toSeq
        }
      }
    }

    def selectInt(sql: String, params: Any*): Int = {
      execute(sql, params: _*) { stmt =>
        Using.resource(stmt.executeQuery()) { rs =>
          if (rs.next) rs.getInt(1) else 0
        }
      }
    }

    private def execute[T](sql: String, params: Any*)(f: (PreparedStatement) => T): T = {
      Using.resource(conn.prepareStatement(sql)) { stmt =>
        params.zipWithIndex.foreach {
          case (p, i) =>
            p match {
              case x: Int    => stmt.setInt(i + 1, x)
              case x: String => stmt.setString(i + 1, x)
            }
        }
        f(stmt)
      }
    }

    def importAsSQL(in: InputStream): Unit = {
      conn.setAutoCommit(false)
      try {
        Using.resource(in) { in =>
          var out = new ByteArrayOutputStream()

          var length = 0
          val bytes = new scala.Array[Byte](1024 * 8)
          var stringLiteral = false

          while ({ length = in.read(bytes); length != -1 }) {
            for (i <- 0 until length) {
              val c = bytes(i)
              if (c == '\'') {
                stringLiteral = !stringLiteral
              }
              if (c == ';' && !stringLiteral) {
                val sql = new String(out.toByteArray, "UTF-8")
                if (sql != null && !sql.isEmpty()) {
                  conn.update(sql.trim)
                }
                out = new ByteArrayOutputStream()
              } else {
                out.write(c)
              }
            }
          }

          val remain = out.toByteArray
          if (remain.length != 0) {
            val sql = new String(remain, "UTF-8")
            conn.update(sql.trim)
          }
        }
        conn.commit()

      } catch {
        case e: Exception => {
          conn.rollback()
          throw e
        }
      }
    }

    def exportAsSQL(targetTables: Seq[String]): File = {
      val dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
      val file = File.createTempFile("gitbucket-export-", ".sql")

      Using.resource(new FileOutputStream(file)) { out =>
        val dbMeta = conn.getMetaData
        val allTablesInDatabase = allTablesOrderByDependencies(dbMeta)

        allTablesInDatabase.reverse.foreach { tableName =>
          if (targetTables.contains(tableName)) {
            out.write(s"DELETE FROM ${tableName};\n".getBytes("UTF-8"))
          }
        }

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

              val values = columns.map {
                case (columnName, columnType) =>
                  if (rs.getObject(columnName) == null) {
                    null
                  } else {
                    columnType match {
                      case Types.BOOLEAN | Types.BIT                                   => rs.getBoolean(columnName)
                      case Types.VARCHAR | Types.CLOB | Types.CHAR | Types.LONGVARCHAR => rs.getString(columnName)
                      case Types.INTEGER                                               => rs.getInt(columnName)
                      case Types.BIGINT                                                => rs.getLong(columnName)
                      case Types.TIMESTAMP                                             => rs.getTimestamp(columnName)
                    }
                  }
              }

              val columnValues = values.map {
                case x: String    => "'" + x.replace("'", "''") + "'"
                case x: Timestamp => "'" + dateFormat.format(x) + "'"
                case null         => "NULL"
                case x            => x
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
      Using.resource(conn.getMetaData.getTables(null, null, "%", Seq("TABLE").toArray)) { rs =>
        val tableNames = new ListBuffer[String]
        while (rs.next) {
          val name = rs.getString("TABLE_NAME").toUpperCase
          if (name != "VERSIONS" && name != "PLUGIN") {
            tableNames += name
          }
        }
        tableNames.toSeq
      }
    }

    private def childTables(meta: DatabaseMetaData, tableName: String): Seq[String] = {
      val normalizedTableName =
        if (meta.getDatabaseProductName == "PostgreSQL") {
          tableName.toLowerCase
        } else {
          tableName
        }

      Using.resource(meta.getExportedKeys(null, null, normalizedTableName)) { rs =>
        val children = new ListBuffer[String]
        while (rs.next) {
          val childTableName = rs.getString("FKTABLE_NAME").toUpperCase
          if (!children.contains(childTableName)) {
            children += childTableName
            children ++= childTables(meta, childTableName)
          }
        }
        children.distinct.toSeq
      }
    }

    private def allTablesOrderByDependencies(meta: DatabaseMetaData): Seq[String] = {
      val tables = allTableNames().map { tableName =>
        TableDependency(tableName, childTables(meta, tableName))
      }

      val edges = tables.flatMap { table =>
        table.children.map { child =>
          (table.tableName, child)
        }
      }

      val ordered = tsort(edges).toSeq
      val orphans = tables.collect { case x if !ordered.contains(x.tableName) => x.tableName }

      ordered ++ orphans
    }

    def tsort[A](edges: Iterable[(A, A)]): Iterable[A] = {
      @tailrec
      def tsort(toPreds: Map[A, Set[A]], done: Iterable[A]): Iterable[A] = {
        val (noPreds, hasPreds) = toPreds.partition { _._2.isEmpty }
        if (noPreds.isEmpty) {
          if (hasPreds.isEmpty) done else sys.error(hasPreds.toString)
        } else {
          val found = noPreds.map { _._1 }
          tsort(hasPreds.map { case (k, v) => (k, v -- found) }, done ++ found)
        }
      }

      val toPred = edges.foldLeft(Map[A, Set[A]]()) { (acc, e) =>
        acc + (e._1 -> acc.getOrElse(e._1, Set())) + (e._2 -> (acc.getOrElse(e._2, Set()) + e._1))
      }
      tsort(toPred, Seq())
    }
  }

  private case class TableDependency(tableName: String, children: Seq[String])

}
