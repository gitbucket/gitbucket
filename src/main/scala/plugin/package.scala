import util.ControlUtil._
import scala.collection.mutable.ListBuffer

package object plugin {

  object db {
    // TODO Use JavaScript Map instead of java.util.Map
    def select(sql: String): Seq[Map[String, String]] = {
      defining(PluginConnectionHolder.threadLocal.get){ conn =>
        using(conn.prepareStatement(sql)){ stmt =>
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
  }

}
