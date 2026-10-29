package gitbucket.core.util

import scala.reflect.ClassTag

object ConfigUtil {

  def getConfigValue[A: ClassTag](key: String): Option[A] = {
    getSystemProperty(key).orElse(getEnvironmentVariable(key))
  }

  def environmentVariableName(key: String): String =
    (if (key.startsWith("gitbucket.")) "" else "GITBUCKET_") + key.toUpperCase.replace('.', '_')

  def systemPropertyName(key: String): String =
    if (key.startsWith("gitbucket.")) key else "gitbucket." + key

  /** Where a value set outside gitbucket.conf comes from, if it is set there. */
  def getConfigSource(key: String): Option[String] =
    getSystemProperty[String](key)
      .map(_ => s"system property ${systemPropertyName(key)}")
      .orElse(getEnvironmentVariable[String](key).map(_ => s"environment variable ${environmentVariableName(key)}"))

  def getEnvironmentVariable[A: ClassTag](key: String): Option[A] = {
    val value = System.getenv(environmentVariableName(key))
    if (value != null && value.nonEmpty) {
      Some(convertType(value))
    } else {
      None
    }
  }

  def getSystemProperty[A: ClassTag](key: String): Option[A] = {
    val value = System.getProperty(systemPropertyName(key))
    if (value != null && value.nonEmpty) {
      Some(convertType(value))
    } else {
      None
    }
  }

  def convertType[A: ClassTag](value: String): A = {
    val c = implicitly[ClassTag[A]].runtimeClass
    if (c == classOf[Boolean]) value.toBoolean
    else if (c == classOf[Long]) value.toLong
    else if (c == classOf[Int]) value.toInt
    else value
  }.asInstanceOf[A]

}
