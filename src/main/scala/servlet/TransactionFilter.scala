package servlet

import javax.servlet._
import org.slf4j.LoggerFactory
import javax.servlet.http.HttpServletRequest

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
      Database(req.getServletContext) withTransaction {
        logger.debug("begin transaction")
        chain.doFilter(req, res)
        logger.debug("end transaction")
      }
    }
  }

}

object Database {

  import scala.slick.driver.ExtendedDriver
  import scala.slick.session.Database

  def apply(context: ServletContext): Database =
    scala.slick.session.Database.forURL(context.getInitParameter("db.url"),
        context.getInitParameter("db.user"),
        context.getInitParameter("db.password"))

  def driver(context: ServletContext): ExtendedDriver = scala.slick.driver.H2Driver // TODO

}
