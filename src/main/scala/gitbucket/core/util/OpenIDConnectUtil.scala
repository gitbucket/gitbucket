package gitbucket.core.util

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSAlgorithm.Family

import scala.collection.JavaConverters.asScalaSet

object OpenIDConnectUtil {
  val JWS_ALGORITHMS: Map[String, Set[JWSAlgorithm]] = Seq(
    "HMAC" -> Family.HMAC_SHA,
    "RSA" -> Family.RSA,
    "ECDSA" -> Family.EC,
    "EdDSA" -> Family.ED
  ).toMap.map { case (name, family) => (name, asScalaSet(family).toSet) }
}
