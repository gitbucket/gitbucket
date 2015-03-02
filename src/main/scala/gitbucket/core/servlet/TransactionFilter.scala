package gitbucket.core.servlet

import javax.servlet._
import javax.servlet.http.HttpServletRequest
import com.mchange.v2.c3p0.ComboPooledDataSource
import gitbucket.core.util.DatabaseConfig
import org.slf4j.LoggerFactory
import slick.jdbc.JdbcBackend.{Database => SlickDatabase, Session}
import gitbucket.core.util.Keys

/**
 * Controls the transaction with the open session in view pattern.
 */
class TransactionFilter extends Filter {

  private val logger = LoggerFactory.getLogger(classOf[TransactionFilter])

  def init(config: FilterConfig) = {}

  def destroy(): Unit = {}

  def doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain): Unit = {
    if(req.asInstanceOf[HttpServletRequest].getRequestURI().startsWith("/assets/")){
      // assets don't need transaction
      chain.doFilter(req, res)
    } else {
      Database() withTransaction { session =>
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

  private val db: SlickDatabase = {
    val datasource = new ComboPooledDataSource

    datasource.setDriverClass(DatabaseConfig.driver)
    datasource.setJdbcUrl(DatabaseConfig.url)
    datasource.setUser(DatabaseConfig.user)
    datasource.setPassword(DatabaseConfig.password)

    logger.debug("load database connection pool")

    SlickDatabase.forDataSource(datasource)
  }

  def apply(): SlickDatabase = db

  def getSession(req: ServletRequest): Session =
    req.getAttribute(Keys.Request.DBSession).asInstanceOf[Session]

}
