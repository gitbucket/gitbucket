package gitbucket.core.servlet

import javax.servlet._
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

import com.zaxxer.hikari._
import gitbucket.core.util.DatabaseConfig
import org.scalatra.ScalatraBase
import org.slf4j.LoggerFactory

import slick.jdbc.JdbcBackend.{Session, Database => SlickDatabase}
import gitbucket.core.util.Keys

import scala.util.matching.Regex

/**
 * Controls the transaction with the open session in view pattern.
 */
class TransactionFilter extends Filter {

  private val logger = LoggerFactory.getLogger(classOf[TransactionFilter])

  def init(config: FilterConfig) = {}

  def destroy(): Unit = {}

  def doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain): Unit = {
    if(req.asInstanceOf[HttpServletRequest].getServletPath().startsWith("/assets/")){
      val request = req.asInstanceOf[HttpServletRequest]
      val pattern = new Regex("""^(https?|ftp)://[^\s/$.?#].[^\s]*$""")
      def isValid (path: String): Boolean = {
          pattern.findAllIn(path).hasNext
      }

      def isValidQPair(a: Array[String]) = {
        val validRange = Range.Double.inclusive(0, 1, 0.1)
        a.length == 2 && a(0) == "q" && validRange.contains(a(1).toDouble)
      }

      import scala.collection.JavaConversions._

      val lis = scala.util.Try (request.getHeaders("Accept") map { s =>
        val fmts = s.split(",").map(_.trim)
        val accepted = fmts.foldLeft(Map.empty[Int, List[String]]) { (acc, f) =>
          val parts = f.split(";").map(_.trim)
          val i = if (parts.size > 1) {
            val pars = parts(1).split("=").map(_.trim).grouped(2).find(isValidQPair).getOrElse(Array("q", "0"))
            (pars(1).toDouble * 10).ceil.toInt
          } else 10
          acc + (i -> (parts(0) :: acc.get(i).getOrElse(List.empty)))
        }
        accepted.toList.sortWith((kv1, kv2) => kv1._1 > kv2._1).flatMap(_._2.reverse)
      })

      if(isValid(request.getServletPath)) {
        lis match {
          case scala.util.Success(c) => {
            // assets don't need transaction
            chain.doFilter(req, res)
          }
          case scala.util.Failure(e) =>
            val resp = res.asInstanceOf[HttpServletResponse]
            resp.reset()
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN)
        }
      }
      else{

        val resp = res.asInstanceOf[HttpServletResponse]
        resp.reset()
        resp.setStatus(HttpServletResponse.SC_FORBIDDEN)
        return
      }




    } else {
      Database() withTransaction { session =>
        // Register Scalatra error callback to rollback transaction
        ScalatraBase.onFailure { _ =>
          logger.debug("Rolled back transaction")
          session.rollback()
        }(req.asInstanceOf[HttpServletRequest])

        logger.debug("begin transaction")
        req.setAttribute(Keys.Request.DBSession, session)
        chain.doFilter(req, res)
        logger.debug("end transaction")
      }
    }

  }



}

object Database {

  private val logger = LoggerFactory.getLogger(Database.getClass)

  private val dataSource: HikariDataSource = {
    val config = new HikariConfig()
    config.setDriverClassName(DatabaseConfig.jdbcDriver)
    config.setJdbcUrl(DatabaseConfig.url)
    config.setUsername(DatabaseConfig.user)
    config.setPassword(DatabaseConfig.password)
    logger.debug("load database connection pool")
    new HikariDataSource(config)
  }

  private val db: SlickDatabase = {
    SlickDatabase.forDataSource(dataSource)
  }

  def apply(): SlickDatabase = db

  def getSession(req: ServletRequest): Session =
    req.getAttribute(Keys.Request.DBSession).asInstanceOf[Session]

  def closeDataSource(): Unit = dataSource.close

}
