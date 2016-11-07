package gitbucket.core.schneider

object ConsumerConfig {
  val consumerHost: String = System.getProperty("consumer.host") match {
    case host: String if (host.nonEmpty) => host
              // Dev "http://localhost:9000"
    case _ => "https://portal.simsci.io"
  }

}
