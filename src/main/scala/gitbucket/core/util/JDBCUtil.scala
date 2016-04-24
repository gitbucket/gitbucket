package gitbucket.core.util

import java.io._
import java.sql._
import java.text.SimpleDateFormat
import javax.xml.stream.{XMLStreamConstants, XMLInputFactory, XMLOutputFactory}
import ControlUtil._
import scala.StringBuilder
import scala.collection.mutable
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

    def importAsXML(in: InputStream): Unit = {
      conn.setAutoCommit(false)
      try {
        val factory = XMLInputFactory.newInstance()
        using(factory.createXMLStreamReader(in)){ reader =>
          // stateful objects
          var elementName   = ""
          var insertTable   = ""
          var insertColumns = Map.empty[String, (String, String)]

          while(reader.hasNext){
            reader.next()

            reader.getEventType match {
              case XMLStreamConstants.START_ELEMENT =>
                elementName = reader.getName.getLocalPart
                if(elementName == "insert"){
                  insertTable = reader.getAttributeValue(null, "table")
                } else if(elementName == "delete"){
                  val tableName = reader.getAttributeValue(null, "table")
                  conn.update(s"DELETE FROM ${tableName}")
                } else if(elementName == "column"){
                  val columnName  = reader.getAttributeValue(null, "name")
                  val columnType  = reader.getAttributeValue(null, "type")
                  val columnValue = reader.getElementText
                  insertColumns = insertColumns + (columnName -> (columnType, columnValue))
                }
              case XMLStreamConstants.END_ELEMENT =>
                // Execute insert statement
                reader.getName.getLocalPart match {
                  case "insert" => {
                    val sb = new StringBuilder()
                    sb.append(s"INSERT INTO ${insertTable} (")
                    sb.append(insertColumns.map { case (columnName, _) => columnName }.mkString(", "))
                    sb.append(") VALUES (")
                    sb.append(insertColumns.map { case (_, (columnType, columnValue)) =>
                      if(columnType == null || columnValue == null){
                        "NULL"
                      } else if(columnType == "string"){
                        "'" + columnValue.replace("'", "''") + "'"
                      } else if(columnType == "timestamp"){
                        "'" + columnValue + "'"
                      } else {
                        columnValue.toString
                      }
                    }.mkString(", "))
                    sb.append(")")

                    conn.update(sb.toString)

                    insertColumns = Map.empty[String, (String, String)] // Clear column information
                  }
                  case _ => // Nothing to do
                }
              case _ => // Nothing to do
            }
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

    def exportAsXML(targetTables: Seq[String]): File = {
      val dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
      val file = File.createTempFile("gitbucket-export-", ".xml")

      val factory = XMLOutputFactory.newInstance()
      using(factory.createXMLStreamWriter(new FileOutputStream(file))){ writer =>
        val dbMeta = conn.getMetaData
        val allTablesInDatabase = allTablesOrderByDependencies(dbMeta)

        writer.writeStartDocument("UTF-8", "1.0")
        writer.writeStartElement("tables")

        allTablesInDatabase.reverse.foreach { tableName =>
          if (targetTables.contains(tableName)) {
            writer.writeStartElement("delete")
            writer.writeAttribute("table", tableName)
            writer.writeEndElement()
          }
        }

        allTablesInDatabase.foreach { tableName =>
          if (targetTables.contains(tableName)) {
            select(s"SELECT * FROM ${tableName}") { rs =>
              writer.writeStartElement("insert")
              writer.writeAttribute("table", tableName)
              val rsMeta = rs.getMetaData
              (1 to rsMeta.getColumnCount).foreach { i =>
                val columnName = rsMeta.getColumnName(i)
                val (columnType, columnValue) = if(rs.getObject(columnName) == null){
                  (null, null)
                } else {
                  rsMeta.getColumnType(i) match {
                    case Types.BOOLEAN | Types.BIT => ("boolean", rs.getBoolean(columnName))
                    case Types.VARCHAR | Types.CLOB | Types.CHAR | Types.LONGVARCHAR => ("string", rs.getString(columnName))
                    case Types.INTEGER => ("int", rs.getInt(columnName))
                    case Types.TIMESTAMP => ("timestamp", dateFormat.format(rs.getTimestamp(columnName)))
                  }
                }
                writer.writeStartElement("column")
                writer.writeAttribute("name", columnName)
                if(columnType != null){
                  writer.writeAttribute("type", columnType)
                }
                if(columnValue != null){
                  writer.writeCharacters(columnValue.toString)
                }
                writer.writeEndElement()
              }
              writer.writeEndElement()
            }
          }
        }

        writer.writeEndElement()
        writer.writeEndDocument()
      }

      file
    }

    def exportAsSQL(targetTables: Seq[String]): File = {
      val dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
      val file = File.createTempFile("gitbucket-export-", ".sql")

      using(new FileOutputStream(file)) { out =>
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

              val values = columns.map { case (columnName, columnType) =>
                if(rs.getObject(columnName) == null){
                  null
                } else {
                  columnType match {
                    case Types.BOOLEAN | Types.BIT => rs.getBoolean(columnName)
                    case Types.VARCHAR | Types.CLOB | Types.CHAR | Types.LONGVARCHAR => rs.getString(columnName)
                    case Types.INTEGER   => rs.getInt(columnName)
                    case Types.TIMESTAMP => rs.getTimestamp(columnName)
                  }
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
        if(meta.getDatabaseProductName == "PostgreSQL"){
          tableName.toLowerCase
        } else {
          tableName
        }

      using(meta.getExportedKeys(null, null, normalizedTableName)) { rs =>
        val children = new ListBuffer[String]
        while (rs.next) {
          val childTableName = rs.getString("FKTABLE_NAME").toUpperCase
          if(!children.contains(childTableName)){
            children += childTableName
            children ++= childTables(meta, childTableName)
          }
        }
        children.distinct.toSeq
      }
    }


    private def allTablesOrderByDependencies(meta: DatabaseMetaData): Seq[String] = {
      val tables = allTableNames.map { tableName =>
        val result = TableDependency(tableName, childTables(meta, tableName))
        result
      }
      tables.sortWith { (a, b) =>
        a.children.contains(b.tableName)
      }.map(_.tableName)
    }

    case class TableDependency(tableName: String, children: Seq[String])
  }

}
