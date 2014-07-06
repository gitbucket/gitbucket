package servlet

import javax.servlet._
import org.slf4j.LoggerFactory
import javax.servlet.http.HttpServletRequest
import util.Keys

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
      Database(req.getServletContext) withTransaction { session =>
        logger.debug("begin transaction")
        req.setAttribute(Keys.Request.DBSession, session)
        chain.doFilter(req, res)
        logger.debug("end transaction")
      }
    }
  }

}

object Database {

  def apply(context: ServletContext): slick.jdbc.JdbcBackend.Database =
    slick.jdbc.JdbcBackend.Database.forURL(context.getInitParameter("db.url"),
        context.getInitParameter("db.user"),
        context.getInitParameter("db.password"))

  def getSession(req: ServletRequest): slick.jdbc.JdbcBackend#Session =
    req.getAttribute(Keys.Request.DBSession).asInstanceOf[slick.jdbc.JdbcBackend#Session]

}
