package plugin

/**
 * Defines enum case classes to specify permission for actions which is provided by plugin.
 */
object Security {

  sealed trait Security

  /**
   * All users and guests
   */
  case class All() extends Security

  /**
   * Only signed-in users
   */
  case class Login() extends Security

  /**
   * Only repository owner and collaborators
   */
  case class Member() extends Security

  /**
   * Only repository owner and managers of group repository
   */
  case class Owner() extends Security

  /**
   * Only administrators
   */
  case class Admin() extends Security

}

