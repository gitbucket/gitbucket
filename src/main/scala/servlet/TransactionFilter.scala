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
      // TODO begin transaction!
      logger.debug("TODO begin transaction")
      chain.doFilter(req, res)
      logger.debug("TODO end transaction")
    }
  }
  
}