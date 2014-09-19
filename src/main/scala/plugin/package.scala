import java.sql.PreparedStatement
import play.twirl.api.Html
import util.ControlUtil._
import scala.collection.mutable.ListBuffer

package object plugin {

  case class Redirect(path: String)
  case class Fragment(html: Html)
  case class RawData(contentType: String, content: Array[Byte])

  object db {
    // TODO labelled place holder support
    def select(sql: String, params: Any*): Seq[Map[String, String]] = {
      defining(PluginConnectionHolder.threadLocal.get){ conn =>
        using(conn.prepareStatement(sql)){ stmt =>
          setParams(stmt, params: _*)
          using(stmt.executeQuery()){ rs =>
            val list = new ListBuffer[Map[String, String]]()
            while(rs.next){
              defining(rs.getMetaData){ meta =>
                val map = Range(1, meta.getColumnCount + 1).map { i =>
                  val name = meta.getColumnName(i)
                  (name, rs.getString(name))
                }.toMap
                list += map
              }
            }
            list
          }
        }
      }
    }

    // TODO labelled place holder support
    def update(sql: String, params: Any*): Int = {
      defining(PluginConnectionHolder.threadLocal.get){ conn =>
        using(conn.prepareStatement(sql)){ stmt =>
          setParams(stmt, params: _*)
          stmt.executeUpdate()
        }
      }
    }

    private def setParams(stmt: PreparedStatement, params: Any*): Unit = {
      params.zipWithIndex.foreach { case (p, i) =>
        p match {
          case x: String  => stmt.setString(i + 1, x)
          case x: Int     => stmt.setInt(i + 1, x)
          case x: Boolean => stmt.setBoolean(i + 1, x)
        }
      }
    }
  }

}
