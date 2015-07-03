package gitbucket.core.util

import jp.sf.amateras.scalatra.forms._
import org.scalatra.i18n.Messages

trait Validations {

  /**
   * Constraint for the identifier such as user name or page name.
   */
  def identifier: Constraint = new Constraint(){
    override def validate(name: String, value: String, messages: Messages): Option[String] =
      if(!value.matches("[a-zA-Z0-9\\-_.]+")){
        Some(s"${name} contains invalid character.")
      } else if(value.startsWith("_") || value.startsWith("-")){
        Some(s"${name} starts with invalid character.")
      } else {
        None
      }
  }

  /**
   * Constraint for the repository identifier.
   */
  def repository: Constraint = new Constraint(){
    override def validate(name: String, value: String, messages: Messages): Option[String] =
      if(!value.matches("[a-zA-Z0-9\\-\\+_.]+")){
        Some(s"${name} contains invalid character.")
      } else if(value.startsWith("_") || value.startsWith("-")){
        Some(s"${name} starts with invalid character.")
      } else {
        None
      }
  }

  /**
   * Constraint for the color pattern.
   */
  def color = pattern("#[0-9a-fA-F]{6}")

  /**
   * ValueType for the java.util.Date property.
   */
  def date(constraints: Constraint*): SingleValueType[java.util.Date] =
    new SingleValueType[java.util.Date]((pattern("\\d{4}-\\d{2}-\\d{2}") +: constraints): _*){
      def convert(value: String, messages: Messages): java.util.Date = new java.text.SimpleDateFormat("yyyy-MM-dd").parse(value)
    }

}
