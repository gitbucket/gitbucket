package gitbucket.core.util.schneider

import com.typesafe.config.ConfigFactory

object ConsumerConfig {

  private val config = ConfigFactory.load("schneider/consumer")

  val consumerHost: String = config.getString("consumer.host")

}
