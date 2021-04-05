package gitbucket.core.util

/**
 * Provides control facilities.
 */
object SyntaxSugars {

  @deprecated("Use scala.util.Try instead", "4.36.0")
  def defining[A, B](value: A)(f: A => B): B = f(value)

  @deprecated("Use scala.util.Try instead", "4.36.0")
  def ignore[T](f: => Unit): Unit =
    try {
      f
    } catch {
      case _: Exception => ()
    }

  object ~ {
    def unapply[A, B](t: (A, B)): Option[(A, B)] = Some(t)
  }

  /**
   * Provides easier and explicit ways to access to a head value of `Map[String, Seq[String]]`.
   * This is intended to use in implementations of scalatra-forms's `Constraint` or `ValueType`.
   */
  implicit class HeadValueAccessibleMap(map: Map[String, Seq[String]]) {
    def value(key: String): String = map(key).head
    def optionValue(key: String): Option[String] = map.get(key).flatMap(_.headOption)
    def values(key: String): Seq[String] = map.getOrElse(key, Seq.empty)
  }

}
