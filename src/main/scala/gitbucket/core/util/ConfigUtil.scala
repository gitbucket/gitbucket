package gitbucket.core.util

import gitbucket.core.util.SyntaxSugars.defining

import scala.reflect.ClassTag

object ConfigUtil {

  def getConfigValue[A: ClassTag](key: String): Option[A] = {
    getSystemProperty(key).orElse(getEnvironmentVariable(key))
  }

  def getEnvironmentVariable[A: ClassTag](key: String): Option[A] = {
    val name = (if (key.startsWith("gitbucket.")) "" else "GITBUCKET_") + key.toUpperCase.replace('.', '_')
    val value = System.getenv(name)
    if (value != null && value.nonEmpty) {
      Some(convertType(value))
    } else {
      None
    }
  }

  def getSystemProperty[A: ClassTag](key: String): Option[A] = {
    val name = if (key.startsWith("gitbucket.")) key else "gitbucket." + key
    val value = System.getProperty(name)
    if (value != null && value.nonEmpty) {
      Some(convertType(value))
    } else {
      None
    }
  }

  def convertType[A: ClassTag](value: String): A =
    defining(implicitly[ClassTag[A]].runtimeClass) { c =>
      if (c == classOf[Boolean]) value.toBoolean
      else if (c == classOf[Long]) value.toLong
      else if (c == classOf[Int]) value.toInt
      else value
    }.asInstanceOf[A]

}
