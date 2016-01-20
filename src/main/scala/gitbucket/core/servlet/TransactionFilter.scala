package gitbucket.core.servlet

import javax.servlet._
import javax.servlet.http.HttpServletRequest
import com.mchange.v2.c3p0.ComboPooledDataSource
import gitbucket.core.util.DatabaseConfig
import org.scalatra.ScalatraBase
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
    if(req.asInstanceOf[HttpServletRequest].getServletPath().startsWith("/assets/")){
      // assets don't need transaction
      chain.doFilter(req, res)
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

  private val dataSource: ComboPooledDataSource = {
    val ds = new ComboPooledDataSource
    ds.setDriverClass(DatabaseConfig.driver)
    //ds.setJdbcUrl("jdbc:h2:tcp://H2-ELB-Public-1985951393.us-west-2.elb.amazonaws.com:1521//opt/h2-data/.gitbucket/data")
    ds.setJdbcUrl(DatabaseConfig.url)
    ds.setUser(DatabaseConfig.user)
    ds.setPassword(DatabaseConfig.password)
    //--- setting to keep the connection alive
    ds.setInitialPoolSize(10)
    ds.setMinPoolSize(1)
    ds.setMaxPoolSize(25)
    ds.setAcquireRetryAttempts(10)
    ds.setIdleConnectionTestPeriod(3600)
    ds.setPreferredTestQuery("SELECT 1;")
    ds.setTestConnectionOnCheckin(false)
    ds.setMaxConnectionAge(14400)
    ds.setMaxIdleTime(10800)
    //ds.setMaxIdleTimeExcessConnections(20)
    //ds.setTestConnectionOnCheckout(true)
    //ds.setTestConnectionOnCheckout(false)
    //ds.setAutoCommitOnClose(true)
    logger.debug("load database connection pool")
    ds
  }

  private val db: SlickDatabase = {
    SlickDatabase.forDataSource(dataSource)
  }

  def apply(): SlickDatabase = db

  def getSession(req: ServletRequest): Session =
    req.getAttribute(Keys.Request.DBSession).asInstanceOf[Session]

  def closeDataSource(): Unit = dataSource.close

}
