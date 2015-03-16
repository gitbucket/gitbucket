package gitbucket.core.util

import java.sql._
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

  }

}
