package plugin

object Security {
  sealed trait Security
  case class All() extends Security
  case class Login() extends Security
  case class Member() extends Security
  case class Owner() extends Security
  case class Admin() extends Security
}

