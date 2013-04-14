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
    
    def convert(name: String, params: Map[String, String]): T = convert(params.get(name).orNull)
    
    def convert(value: String): T
    
    def validate(name: String, params: Map[String, String]): Seq[(String, String)] = validate(name, params.get(name).orNull)
    
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
  
  /**
   * ValueType for the String property.
   */
  def text(constraints: Constraint*): SingleValueType[String] = new SingleValueType[String](constraints: _*){
    def convert(value: String): String = value
  }
  
  /**
   * ValueType for the Boolean property.
   */
  def boolean(constraints: Constraint*): SingleValueType[Boolean] = new SingleValueType[Boolean](constraints: _*){
    def convert(value: String): Boolean = value match {
      case null|"false"|"FALSE" => false
      case _ => true
    }
  }
  
  /**
   * ValueType for the Int property.
   */
  def number(constraints: Constraint*): SingleValueType[Int] = new SingleValueType[Int](constraints: _*){
    
    def convert(value: String): Int = value match {
      case null|"" => 0
      case x => x.toInt
    }
    
    override def validate(name: String, value: String): Seq[(String, String)] = {
      try {
        value.toInt
        super.validate(name, value)
      } catch {
        case e: NumberFormatException => Seq(name -> "%s must be a number.".format(name))
      }
    }
  }
  
  def mapping[T, P1](f1: (String, ValueType[P1]))(factory: (P1) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1)
    def convert(name: String, params: Map[String, String]) = factory(p(f1, params))
  }
  
  def mapping[T, P1, P2](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]))(factory: (P1, P2) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2)
    def convert(name: String, params: Map[String, String]) = factory(p(f1, params), p(f2, params))
  }

  def mapping[T, P1, P2, P3](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]))(factory: (P1, P2, P3) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3)
    def convert(name: String, params: Map[String, String]) = factory(p(f1, params), p(f2, params), p(f3, params))
  }
  
  def mapping[T, P1, P2, P3, P4](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]))(factory: (P1, P2, P3, P4) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4)
    def convert(name: String, params: Map[String, String]) = factory(p(f1, params), p(f2, params), p(f3, params), p(f4, params))
  }
  
  def mapping[T, P1, P2, P3, P4, P5](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]))(factory: (P1, P2, P3, P4, P5) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5)
    def convert(name: String, params: Map[String, String]) = factory(p(f1, params), p(f2, params), p(f3, params), p(f4, params), p(f5, params))
  }
  
  def mapping[T, P1, P2, P3, P4, P5, P6](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]), f6: (String, ValueType[P6]))(factory: (P1, P2, P3, P4, P5, P6) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5, f6)
    def convert(name: String, params: Map[String, String]) = factory(p(f1, params), p(f2, params), p(f3, params), p(f4, params), p(f5, params), p(f6, params))
  }
  
  def mapping[T, P1, P2, P3, P4, P5, P6, P7](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]), f6: (String, ValueType[P6]), f7: (String, ValueType[P7]))(factory: (P1, P2, P3, P4, P5, P6, P7) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5, f6, f7)
    def convert(name: String, params: Map[String, String]) = factory(p(f1, params), p(f2, params), p(f3, params), p(f4, params), p(f5, params), p(f6, params), p(f7, params))
  }
  
  def mapping[T, P1, P2, P3, P4, P5, P6, P7, P8](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]), f6: (String, ValueType[P6]), f7: (String, ValueType[P7]), f8: (String, ValueType[P8]))(factory: (P1, P2, P3, P4, P5, P6, P7, P8) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5, f6, f7, f8)
    def convert(name: String, params: Map[String, String]) = factory(p(f1, params), p(f2, params), p(f3, params), p(f4, params), p(f5, params), p(f6, params), p(f7, params), p(f8, params))
  }
  
  def mapping[T, P1, P2, P3, P4, P5, P6, P7, P8, P9](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]), f6: (String, ValueType[P6]), f7: (String, ValueType[P7]), f8: (String, ValueType[P8]), f9: (String, ValueType[P9]))(factory: (P1, P2, P3, P4, P5, P6, P7, P8, P9) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5, f6, f7, f8, f9)
    def convert(name: String, params: Map[String, String]) = factory(p(f1, params), p(f2, params), p(f3, params), p(f4, params), p(f5, params), p(f6, params), p(f7, params), p(f8, params), p(f9, params))
  }
  
  def mapping[T, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]), f6: (String, ValueType[P6]), f7: (String, ValueType[P7]), f8: (String, ValueType[P8]), f9: (String, ValueType[P9]), f10: (String, ValueType[P10]))(factory: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5, f6, f7, f8, f9, f10)
    def convert(name: String, params: Map[String, String]) = factory(p(f1, params), p(f2, params), p(f3, params), p(f4, params), p(f5, params), p(f6, params), p(f7, params), p(f8, params), p(f9, params), p(f10, params))
  }
  
  def mapping[T, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]), f6: (String, ValueType[P6]), f7: (String, ValueType[P7]), f8: (String, ValueType[P8]), f9: (String, ValueType[P9]), f10: (String, ValueType[P10]), f11: (String, ValueType[P11]))(factory: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11)
    def convert(name: String, params: Map[String, String]) = factory(p(f1, params), p(f2, params), p(f3, params), p(f4, params), p(f5, params), p(f6, params), p(f7, params), p(f8, params), p(f9, params), p(f10, params), p(f11, params))
  }
  
  def mapping[T, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]), f6: (String, ValueType[P6]), f7: (String, ValueType[P7]), f8: (String, ValueType[P8]), f9: (String, ValueType[P9]), f10: (String, ValueType[P10]), f11: (String, ValueType[P11]), f12: (String, ValueType[P12]))(factory: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12)
    def convert(name: String, params: Map[String, String]) = factory(p(f1, params), p(f2, params), p(f3, params), p(f4, params), p(f5, params), p(f6, params), p(f7, params), p(f8, params), p(f9, params), p(f10, params), p(f11, params), p(f12, params))
  }
  
  def mapping[T, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]), f6: (String, ValueType[P6]), f7: (String, ValueType[P7]), f8: (String, ValueType[P8]), f9: (String, ValueType[P9]), f10: (String, ValueType[P10]), f11: (String, ValueType[P11]), f12: (String, ValueType[P12]), f13: (String, ValueType[P13]))(factory: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13)
    def convert(name: String, params: Map[String, String]) = factory(p(f1, params), p(f2, params), p(f3, params), p(f4, params), p(f5, params), p(f6, params), p(f7, params), p(f8, params), p(f9, params), p(f10, params), p(f11, params), p(f12, params), p(f13, params))
  }
  
  def mapping[T, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]), f6: (String, ValueType[P6]), f7: (String, ValueType[P7]), f8: (String, ValueType[P8]), f9: (String, ValueType[P9]), f10: (String, ValueType[P10]), f11: (String, ValueType[P11]), f12: (String, ValueType[P12]), f13: (String, ValueType[P13]), f14: (String, ValueType[P14]))(factory: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14)
    def convert(name: String, params: Map[String, String]) = factory(p(f1, params), p(f2, params), p(f3, params), p(f4, params), p(f5, params), p(f6, params), p(f7, params), p(f8, params), p(f9, params), p(f10, params), p(f11, params), p(f12, params), p(f13, params), p(f14, params))
  }
  
  def mapping[T, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]), f6: (String, ValueType[P6]), f7: (String, ValueType[P7]), f8: (String, ValueType[P8]), f9: (String, ValueType[P9]), f10: (String, ValueType[P10]), f11: (String, ValueType[P11]), f12: (String, ValueType[P12]), f13: (String, ValueType[P13]), f14: (String, ValueType[P14]), f15: (String, ValueType[P15]))(factory: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15)
    def convert(name: String, params: Map[String, String]) = factory(p(f1, params), p(f2, params), p(f3, params), p(f4, params), p(f5, params), p(f6, params), p(f7, params), p(f8, params), p(f9, params), p(f10, params), p(f11, params), p(f12, params), p(f13, params), p(f14, params), p(f15, params))
  }
  
  def mapping[T, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]), f6: (String, ValueType[P6]), f7: (String, ValueType[P7]), f8: (String, ValueType[P8]), f9: (String, ValueType[P9]), f10: (String, ValueType[P10]), f11: (String, ValueType[P11]), f12: (String, ValueType[P12]), f13: (String, ValueType[P13]), f14: (String, ValueType[P14]), f15: (String, ValueType[P15]), f16: (String, ValueType[P16]))(factory: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15, f16)
    def convert(name: String, params: Map[String, String]) = factory(p(f1, params), p(f2, params), p(f3, params), p(f4, params), p(f5, params), p(f6, params), p(f7, params), p(f8, params), p(f9, params), p(f10, params), p(f11, params), p(f12, params), p(f13, params), p(f14, params), p(f15, params), p(f16, params))
  }
  
  def mapping[T, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]), f6: (String, ValueType[P6]), f7: (String, ValueType[P7]), f8: (String, ValueType[P8]), f9: (String, ValueType[P9]), f10: (String, ValueType[P10]), f11: (String, ValueType[P11]), f12: (String, ValueType[P12]), f13: (String, ValueType[P13]), f14: (String, ValueType[P14]), f15: (String, ValueType[P15]), f16: (String, ValueType[P16]), f17: (String, ValueType[P17]))(factory: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15, f16, f17)
    def convert(name: String, params: Map[String, String]) = factory(p(f1, params), p(f2, params), p(f3, params), p(f4, params), p(f5, params), p(f6, params), p(f7, params), p(f8, params), p(f9, params), p(f10, params), p(f11, params), p(f12, params), p(f13, params), p(f14, params), p(f15, params), p(f16, params), p(f17, params))
  }
  
  def mapping[T, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]), f6: (String, ValueType[P6]), f7: (String, ValueType[P7]), f8: (String, ValueType[P8]), f9: (String, ValueType[P9]), f10: (String, ValueType[P10]), f11: (String, ValueType[P11]), f12: (String, ValueType[P12]), f13: (String, ValueType[P13]), f14: (String, ValueType[P14]), f15: (String, ValueType[P15]), f16: (String, ValueType[P16]), f17: (String, ValueType[P17]), f18: (String, ValueType[P18]))(factory: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15, f16, f17, f18)
    def convert(name: String, params: Map[String, String]) = factory(p(f1, params), p(f2, params), p(f3, params), p(f4, params), p(f5, params), p(f6, params), p(f7, params), p(f8, params), p(f9, params), p(f10, params), p(f11, params), p(f12, params), p(f13, params), p(f14, params), p(f15, params), p(f16, params), p(f17, params), p(f18, params))
  }
  
  def mapping[T, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]), f6: (String, ValueType[P6]), f7: (String, ValueType[P7]), f8: (String, ValueType[P8]), f9: (String, ValueType[P9]), f10: (String, ValueType[P10]), f11: (String, ValueType[P11]), f12: (String, ValueType[P12]), f13: (String, ValueType[P13]), f14: (String, ValueType[P14]), f15: (String, ValueType[P15]), f16: (String, ValueType[P16]), f17: (String, ValueType[P17]), f18: (String, ValueType[P18]), f19: (String, ValueType[P19]))(factory: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15, f16, f17, f18, f19)
    def convert(name: String, params: Map[String, String]) = factory(p(f1, params), p(f2, params), p(f3, params), p(f4, params), p(f5, params), p(f6, params), p(f7, params), p(f8, params), p(f9, params), p(f10, params), p(f11, params), p(f12, params), p(f13, params), p(f14, params), p(f15, params), p(f16, params), p(f17, params), p(f18, params), p(f19, params))
  }
  
  def mapping[T, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]), f6: (String, ValueType[P6]), f7: (String, ValueType[P7]), f8: (String, ValueType[P8]), f9: (String, ValueType[P9]), f10: (String, ValueType[P10]), f11: (String, ValueType[P11]), f12: (String, ValueType[P12]), f13: (String, ValueType[P13]), f14: (String, ValueType[P14]), f15: (String, ValueType[P15]), f16: (String, ValueType[P16]), f17: (String, ValueType[P17]), f18: (String, ValueType[P18]), f19: (String, ValueType[P19]), f20: (String, ValueType[P20]))(factory: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15, f16, f17, f18, f19, f20)
    def convert(name: String, params: Map[String, String]) = factory(p(f1, params), p(f2, params), p(f3, params), p(f4, params), p(f5, params), p(f6, params), p(f7, params), p(f8, params), p(f9, params), p(f10, params), p(f11, params), p(f12, params), p(f13, params), p(f14, params), p(f15, params), p(f16, params), p(f17, params), p(f18, params), p(f19, params), p(f20, params))
  }
  
  def mapping[T, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20, P21](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]), f6: (String, ValueType[P6]), f7: (String, ValueType[P7]), f8: (String, ValueType[P8]), f9: (String, ValueType[P9]), f10: (String, ValueType[P10]), f11: (String, ValueType[P11]), f12: (String, ValueType[P12]), f13: (String, ValueType[P13]), f14: (String, ValueType[P14]), f15: (String, ValueType[P15]), f16: (String, ValueType[P16]), f17: (String, ValueType[P17]), f18: (String, ValueType[P18]), f19: (String, ValueType[P19]), f20: (String, ValueType[P20]), f21: (String, ValueType[P21]))(factory: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20, P21) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15, f16, f17, f18, f19, f20, f21)
    def convert(name: String, params: Map[String, String]) = factory(p(f1, params), p(f2, params), p(f3, params), p(f4, params), p(f5, params), p(f6, params), p(f7, params), p(f8, params), p(f9, params), p(f10, params), p(f11, params), p(f12, params), p(f13, params), p(f14, params), p(f15, params), p(f16, params), p(f17, params), p(f18, params), p(f19, params), p(f20, params), p(f21, params))
  }
  
  def mapping[T, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20, P21, P22](f1: (String, ValueType[P1]), f2: (String, ValueType[P2]), f3: (String, ValueType[P3]), f4: (String, ValueType[P4]), f5: (String, ValueType[P5]), f6: (String, ValueType[P6]), f7: (String, ValueType[P7]), f8: (String, ValueType[P8]), f9: (String, ValueType[P9]), f10: (String, ValueType[P10]), f11: (String, ValueType[P11]), f12: (String, ValueType[P12]), f13: (String, ValueType[P13]), f14: (String, ValueType[P14]), f15: (String, ValueType[P15]), f16: (String, ValueType[P16]), f17: (String, ValueType[P17]), f18: (String, ValueType[P18]), f19: (String, ValueType[P19]), f20: (String, ValueType[P20]), f21: (String, ValueType[P21]), f22: (String, ValueType[P22]))(factory: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20, P21, P22) => T): MappingValueType[T] = new MappingValueType[T]{
    def fields = Seq(f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15, f16, f17, f18, f19, f20, f21, f22)
    def convert(name: String, params: Map[String, String]) = factory(p(f1, params), p(f2, params), p(f3, params), p(f4, params), p(f5, params), p(f6, params), p(f7, params), p(f8, params), p(f9, params), p(f10, params), p(f11, params), p(f12, params), p(f13, params), p(f14, params), p(f15, params), p(f16, params), p(f17, params), p(f18, params), p(f19, params), p(f20, params), p(f21, params), p(f22, params))
  }
  
  private def p[T](field: (String, ValueType[T]), params: Map[String, String]): T = field._2.convert(field._1, params)
  
  /////////////////////////////////////////////////////////////////////////////////////////////
  // ValueType wrappers to provide additional features.
  
  /**
   * ValueType wrapper for the Option property.
   */
  def optional[T](valueType: SingleValueType[T]): SingleValueType[Option[T]] = new SingleValueType[Option[T]](){
    def convert(value: String): Option[T] = if(value == null) None else Some(valueType.convert(value))
    override def validate(name: String, value: String): Seq[(String, String)] = if(value == null) Nil else valueType.validate(name, value)
  }
  
  /**
   * ValueType wrapper to trim a parameter.
   * 
   * {{{
   * val form = mapping(
   *   "name" -> trim(text(required)),
   *   "mail" -> trim(text(required))
   * )
   * }}}
   */
  def trim[T](valueType: SingleValueType[T]): SingleValueType[T] = new SingleValueType[T](){
    def convert(value: String): T = valueType.convert(value.trim)
    override def validate(name: String, value: String): Seq[(String, String)] = valueType.validate(name, value.trim)
  }
  
  /**
   * ValueType wrapper to trim all parameters.
   * 
   * {{{
   * val form = trim(mapping(
   *   "name" -> text(required),
   *   "mail" -> text(required)
   * ))
   * }}}
   */
  def trim[T](valueType: MappingValueType[T]): MappingValueType[T] = new MappingValueType[T](){
    def validate(name: String, params: Map[String, String]): Seq[(String, String)] =
      fields.map { case (name, valueType) => valueType.validate(name, trimMap(params)) }.flatten
    
    def validateAsJSON(params: Map[String, String]): JObject = {
      JObject(validate("form", trimMap(params)).map { case (key, value) =>
        JField(key, JString(value))
      }.toList)
    }
      
    private def trimMap(params: Map[String, String]): Map[String, String] = params.map{ x => x._1 -> x._2.trim }
  }
  
  /**
   * ValueType wrapper to specified a property name which is used in the error message. 
   * 
   * {{{
   * val form = trim(mapping(
   *   "name" -> label("User name"   , text(required)),
   *   "mail" -> label("Mail address", text(required))
   * ))
   * }}}
    */
  def label[T](label: String, valueType: SingleValueType[T]): SingleValueType[T] = new SingleValueType[T](){
    def convert(value: String): T = valueType.convert(value)
    override def validate(name: String, value: String): Seq[(String, String)] = 
      valueType.validate(label, value).map { case (label, message) => name -> message }
  }
  
  /////////////////////////////////////////////////////////////////////////////////////////////
  // Constraints
  
  trait Constraint {
    def validate(name: String, value: String): Option[String]
  }
  
  def required: Constraint = new Constraint(){
    def validate(name: String, value: String): Option[String] = {
      if(value == null || value.isEmpty) Some("%s is required.".format(name)) else None
    }
  }
  
  def required(message: String): Constraint = new Constraint(){
    def validate(name: String, value: String): Option[String] = 
      if(value == null || value.isEmpty) Some(message) else None
  }
  
  def maxlength(length: Int): Constraint = new Constraint(){
    def validate(name: String, value: String): Option[String] =
      if(value != null && value.length > length) Some("%s cannot be longer than %d characters.".format(name, length)) else None
  }
  
  def minlength(length: Int): Constraint = new Constraint(){
    def validate(name: String, value: String): Option[String] =
      if(value != null && value.length < length) Some("%s cannot be shorter than %d characters".format(name, length)) else None
  }
  
  def pattern(pattern: String, message: String = ""): Constraint = new Constraint {
    def validate(name: String, value: String): Option[String] =
      if(value != null && !value.matches("^" + pattern + "$")){
        if(message.isEmpty) Some("%s must be '%s'.".format(name, pattern)) else Some(message)
      } else None
  }
  
}