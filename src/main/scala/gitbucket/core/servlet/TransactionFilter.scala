package gitbucket.core.servlet

import javax.servlet._
import javax.servlet.http.HttpServletRequest
import com.zaxxer.hikari._
import gitbucket.core.util.DatabaseConfig
import org.scalatra.ScalatraBase
import org.slf4j.LoggerFactory
import slick.jdbc.JdbcBackend.{Database => SlickDatabase, Session}
import gitbucket.core.util.Keys
import gitbucket.core.model.Profile.profile.blockingApi._

/**
 * Controls the transaction with the open session in view pattern.
 */
class TransactionFilter extends Filter {

  private val logger = LoggerFactory.getLogger(classOf[TransactionFilter])

  def init(config: FilterConfig) = {}

  def destroy(): Unit = {}

  def doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain): Unit = {
    val servletPath = req.asInstanceOf[HttpServletRequest].getServletPath()
    if (servletPath.startsWith("/assets/") || servletPath == "/git" || servletPath == "/git-lfs") {
      // assets and git-lfs don't need transaction
      chain.doFilter(req, res)
    } else {
      Database() withTransaction { session =>
        // Register Scalatra error callback to rollback transaction
        ScalatraBase.onFailure { _ =>
          logger.debug("Rolled back transaction")
          session.conn.rollback()
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
    config.setAutoCommit(false)
    DatabaseConfig.connectionTimeout.foreach(config.setConnectionTimeout)
    DatabaseConfig.idleTimeout.foreach(config.setIdleTimeout)
    DatabaseConfig.maxLifetime.foreach(config.setMaxLifetime)
    DatabaseConfig.minimumIdle.foreach(config.setMinimumIdle)
    DatabaseConfig.maximumPoolSize.foreach(config.setMaximumPoolSize)

    logger.debug("load database connection pool")
    new HikariDataSource(config)
  }

  private val db: SlickDatabase = {
    SlickDatabase.forDataSource(dataSource, Some(dataSource.getMaximumPoolSize))
  }

  def apply(): SlickDatabase = db

  def getSession(req: ServletRequest): Session =
    req.getAttribute(Keys.Request.DBSession).asInstanceOf[Session]

  def closeDataSource(): Unit = dataSource.close

}
