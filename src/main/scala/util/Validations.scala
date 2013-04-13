package util

import org.json4s._
import org.json4s.jackson._

object Validations {
  
  def withValidation[T](form: Form[T], params: Map[String, String])(action: T => Any): Any = {
    form.validate(params).isEmpty match {
      case true  => action(form.create(params))
      case false => throw new RuntimeException("Invalid Request") // TODO show error page?
    }
  }
  
  def Form[T, P1](f1: (String, ValueType[P1]))(factory: (P1) => T): Form[T] = new Form[T]{
    def fields = Seq(f1)
    def create(params: Map[String, String]) = factory(f1._2.convert(params(f1._1)))
  }
  
  def Form[T, P1, P2](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]))(factory: (P1, P2) => T): Form[T] = new Form[T]{
    def fields = Seq(f1, f2)
    def create(params: Map[String, String]) = factory(f1._2.convert(params(f1._1)), f2._2.convert(params(f2._1)))
  }
  
  def Form[T, P1, P2, P3](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]))(factory: (P1, P2, P3) => T): Form[T] = new Form[T]{
    def fields = Seq(f1, f2)
    def create(params: Map[String, String]) = factory(f1._2.convert(params(f1._1)), f2._2.convert(params(f2._1)), f3._2.convert(params(f3._1)))
  }
  
  abstract class Form[T] {
    
    def fields: Seq[(String, ValueType[_])]
    
    def create(params: Map[String, String]): T
    
    def validate(params: Map[String, String]): Map[String, String] = {
      fields.map { case (name, valueType) =>
        valueType.validate(name, params(name)) match {
          case Some(message) => Some(name, message)
          case None => None
        }
      }.flatten.toMap
    }
    
    def validateAsJSON(params: Map[String, String]): JObject = {
      JObject(validate(params).map { case (key, value) =>
        JField(key, JString(value))
      }.toList)
    }
  }
  
  /////////////////////////////////////////////////////////////////////////////////////////////
  // Constraints
  
  trait Constraint {
    def validate(name: String, value: String): Option[String]
  }
  
  def required: Constraint = new Constraint(){
    def validate(name: String, value: String): Option[String] = 
      if(value.isEmpty) Some("%s is required.".format(name)) else None
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
  // ValueTypes
  
  abstract class ValueType[T](constraints: Constraint*) {
    
    def convert(value: String): T
    
    def validate(name: String, value: String): Option[String] = {
      constraints.map(_.validate(name, value)).flatten.headOption
    }
  }
  
  def text(constraints: Constraint*): ValueType[String] = new ValueType[String](constraints: _*){
    def convert(value: String): String = value
  }
  
  /////////////////////////////////////////////////////////////////////////////////////////////
  // ValueType wrappers to provide additional features.
  
  def trim[T](valueType: ValueType[T]): ValueType[T] = new ValueType[T](){
    def convert(value: String): T = valueType.convert(value.trim)
    override def validate(name: String, value: String): Option[String] = valueType.validate(name, value.trim)
  }
  
  /**
   * 
   */
  def label[T](label: String, valueType: ValueType[T]): ValueType[T] = new ValueType[T](){
    def convert(value: String): T = valueType.convert(value.trim)
    override def validate(name: String, value: String): Option[String] = valueType.validate(label, value.trim)
  }
  
}