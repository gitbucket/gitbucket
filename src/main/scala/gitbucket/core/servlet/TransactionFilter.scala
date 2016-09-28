package gitbucket.core.servlet

import javax.servlet._
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

import com.zaxxer.hikari._
import gitbucket.core.util.DatabaseConfig
import org.scalatra.ScalatraBase
import org.slf4j.LoggerFactory

import slick.jdbc.JdbcBackend.{Session, Database => SlickDatabase}
import gitbucket.core.util.Keys
import org.owasp.esapi.ESAPI

import scala.util.matching.Regex

/**
 * Controls the transaction with the open session in view pattern.
 */
class TransactionFilter extends Filter {

  private val logger = LoggerFactory.getLogger(classOf[TransactionFilter])

  def init(config: FilterConfig) = {}

  def destroy(): Unit = {}

  def doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain): Unit = {
    val request = req.asInstanceOf[HttpServletRequest]

    if (isRedirect(request.getQueryString) || isRedirect(request.getRequestURI) || isInvalid(request.getHeader("Accept"))) {
      (res.asInstanceOf[HttpServletResponse])
        .sendError(HttpServletResponse.SC_BAD_REQUEST)
    }
    else{
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

  private def isRedirect(value: String) : Boolean = {
    if(value!=null && value.contains("redirect"))
      false
    else
      isInvalid(value)
  }

  private def isInvalid(value: String) : Boolean = {
    value != null && (value.indexOf('<') != -1
      || value.indexOf('>') != -1
      || value.indexOf("%3C") != -1
      || value.indexOf("%3c") != -1
      || value.indexOf("%3E") != -1
      || value.indexOf("%3e") != -1
      || stripXSS(value))

  }

  import java.util.regex.Pattern

  private def stripXSS(value: String) : Boolean = {
    if(value != null) {

      // avoid encoded attacks.
      var newValue =
      ESAPI.encoder().canonicalize(value)
        // Avoid null characters
        .replaceAll("","")

      // Avoid anything between script tags
      newValue =  Pattern.compile("<script>(.*?)</script>", Pattern.CASE_INSENSITIVE)
        .matcher(newValue).replaceAll("")
      // Avoid anything in a src='...' type of expression
      newValue = Pattern.compile("src[\r\n]*=[\r\n]*\\\'(.*?)\\\'", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL)
        .matcher(newValue).replaceAll("")
      newValue = Pattern.compile("src[\r\n]*=[\r\n]*\\\"(.*?)\\\"", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL)
        .matcher(newValue).replaceAll("")

      // Remove any lonesome </script> tag
      newValue = Pattern.compile("</script>", Pattern.CASE_INSENSITIVE)
        .matcher(newValue).replaceAll("")

      // Remove any lonesome <script ...> tag
      newValue = Pattern.compile("<script(.*?)>", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL)
        .matcher(newValue).replaceAll("")

      // Avoid eval(...) expressions
      newValue = Pattern.compile("eval\\((.*?)\\)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL)
        .matcher(newValue).replaceAll("")

      // Avoid expression(...) expressions
      newValue = Pattern.compile("expression\\((.*?)\\)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL)
        .matcher(newValue).replaceAll("")

      // Avoid javascript:... expressions
      newValue =  Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE)
        .matcher(newValue).replaceAll("")

      // Avoid vbscript:... expressions
      newValue = Pattern.compile("vbscript:", Pattern.CASE_INSENSITIVE)
        .matcher(newValue).replaceAll("")

      // Avoid onload= expressions
      newValue = Pattern.compile("onload(.*?)=", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL)
        .matcher(newValue).replaceAll("")

      !newValue.equalsIgnoreCase(value)

    } else false

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
