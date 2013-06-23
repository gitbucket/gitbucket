package util

import jp.sf.amateras.scalatra.forms._
import scala.Some

trait Validations {

  /**
   * Constraint for the identifier such as user name, repository name or page name.
   */
  def identifier: Constraint = new Constraint(){
    def validate(name: String, value: String): Option[String] =
      if(!value.matches("^[a-zA-Z0-9\\-_]+$")){
        Some("%s contains invalid character.".format(name))
      } else if(value.startsWith("_") || value.startsWith("-")){
        Some("%s starts with invalid character.".format(name))
      } else {
        None
      }
  }

  /**
   * ValueType for the java.util.Date property.
   */
  def date(constraints: Constraint*): SingleValueType[java.util.Date] =
    new SingleValueType[java.util.Date]((pattern("\\d{4}-\\d{2}-\\d{2}") +: constraints): _*){
      def convert(value: String): java.util.Date = {
        val formatter = new java.text.SimpleDateFormat("yyyy-MM-dd")
        formatter.parse(value)
      }
    }

}
