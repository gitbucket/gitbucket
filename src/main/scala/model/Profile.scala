package model

import scala.slick.driver.ExtendedProfile

trait Profile {
  /**
   * Note: `ExtendedProfile` will be replaced with `JdbcProfile` from Slick 2.0.0
   */
  val profile: ExtendedProfile
}
