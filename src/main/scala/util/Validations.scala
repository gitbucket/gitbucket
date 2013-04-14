package util

import org.json4s._
import org.json4s.jackson._

object Validations {
  
  /**
   * Runs form validation before action.
   * If there are validation error, action is not invoked and this method throws RuntimeException.
   * 
   * @param mapping the mapping definition
   * @param params the request parameters
   * @param action the action
   * @return the result of action
   */
  def withValidation[T](mapping: MappingValueType[T], params: Map[String, String])(action: T => Any): Any = {
    mapping.validate("form", params).isEmpty match {
      case true  => action(mapping.convert("form", params))
      case false => throw new RuntimeException("Invalid Request") // TODO show error page?
    }
  }
  
  /////////////////////////////////////////////////////////////////////////////////////////////
  // ValueTypes
  
  trait ValueType[T] {
    def convert(name: String, params: Map[String, String]): T
    def validate(name: String, params: Map[String, String]): Seq[(String, String)]
  }
  
  /**
   * The base class for the single field ValueTypes.
   */
  abstract class SingleValueType[T](constraints: Constraint*) extends ValueType[T]{
    
    def convert(name: String, params: Map[String, String]): T = convert(params(name))
    
    def convert(value: String): T
    
    def validate(name: String, params: Map[String, String]): Seq[(String, String)] = validate(name, params(name))
    
    def validate(name: String, value: String): Seq[(String, String)] = validaterec(name, value, Seq(constraints: _*))
    
    @scala.annotation.tailrec
    private def validaterec(name: String, value: String, constraints: Seq[Constraint]): Seq[(String, String)] = {
      constraints match {
        case (x :: rest) => x.validate(name, value) match {
          case Some(message) => Seq(name -> message)
          case None          => validaterec(name, value, rest)
        }
        case Nil => Nil
        case x => println(x); Nil
      }
    }
    
  }
  
  /**
   * The base class for the object field ValueTypes.
   */
  abstract class MappingValueType[T] extends ValueType[T]{

    def fields: Seq[(String, ValueType[_])]
    
    def validate(name: String, params: Map[String, String]): Seq[(String, String)] =
      fields.map { case (name, valueType) => valueType.validate(name, params) }.flatten
    
    def validateAsJSON(params: Map[String, String]): JObject = {
      JObject(validate("form", params).map { case (key, value) =>
        JField(key, JString(value))
      }.toList)
    }
  }
  
  def text(constraints: Constraint*): SingleValueType[String] = new SingleValueType[String](constraints: _*){
    def convert(value: String): String = value
  }
  
  def mapping[T, P1](f1: (String, ValueType[P1]))(factory: (P1) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1)
    def convert(name: String, params: Map[String, String]) = factory(f1._2.convert(f1._1, params))
  }
  
  def mapping[T, P1, P2](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]))(factory: (P1, P2) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2)
    def convert(name: String, params: Map[String, String]) = factory(f1._2.convert(f1._1, params), f2._2.convert(f2._1, params))
  }
  
  /////////////////////////////////////////////////////////////////////////////////////////////
  // Constraints
  
  trait Constraint {
    def validate(name: String, value: String): Option[String]
  }
  
  def required: Constraint = new Constraint(){
    def validate(name: String, value: String): Option[String] = {
      if(value.isEmpty) Some("%s is required.".format(name)) else None
    }
  }
  
  def required(message: String): Constraint = new Constraint(){
    def validate(name: String, value: String): Option[String] = 
      if(value.isEmpty) Some(message) else None
  }
  
  def maxlength(length: Int): Constraint = new Constraint(){
    def validate(name: String, value: String): Option[String] =
      if(value.length > length) Some("%s cannot be longer than %d characters.".format(name, length)) else None
  }
  
  def minlength(length: Int): Constraint = new Constraint(){
    def validate(name: String, value: String): Option[String] =
      if(value.length < length) Some("%s cannot be shorter than %d characters".format(name, length)) else None
  }
  
  def pattern(pattern: String, message: String = ""): Constraint = new Constraint {
    def validate(name: String, value: String): Option[String] =
      if(!value.matches("^" + pattern + "$")){
        if(message.isEmpty) Some("%s must be '%s'.".format(name, pattern)) else Some(message)
      } else None
  }
  
  /////////////////////////////////////////////////////////////////////////////////////////////
  // ValueType wrappers to provide additional features.
  
  def trim[T](valueType: SingleValueType[T]): SingleValueType[T] = new SingleValueType[T](){
    def convert(value: String): T = valueType.convert(value.trim)
    override def validate(name: String, value: String): Seq[(String, String)] = valueType.validate(name, value.trim)
  }
  
  def label[T](label: String, valueType: SingleValueType[T]): SingleValueType[T] = new SingleValueType[T](){
    def convert(value: String): T = valueType.convert(value)
    override def validate(name: String, value: String): Seq[(String, String)] = 
      valueType.validate(label, value).map { case (label, message) => name -> message }
  }
  
}