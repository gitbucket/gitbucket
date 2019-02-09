package gitbucket.core.service

import java.io.ByteArrayInputStream

import gitbucket.core.model.GpgKey

import collection.JavaConverters._
import gitbucket.core.model.Profile._
import gitbucket.core.model.Profile.profile.blockingApi._
import org.bouncycastle.bcpg.ArmoredInputStream
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.bc.BcPGPObjectFactory

trait GpgKeyService {
  def getGpgPublicKeys(userName: String)(implicit s: Session): List[GpgKey] =
    GpgKeys.filter(_.userName === userName.bind).sortBy(_.gpgKeyId).list

  def addGpgPublicKey(userName: String, title: String, publicKey: String)(implicit s: Session): Unit = {
    println(publicKey)
    val pubKeyOf = new BcPGPObjectFactory(new ArmoredInputStream(new ByteArrayInputStream(publicKey.getBytes)))
    println(pubKeyOf)
    pubKeyOf.iterator().asScala.foreach { o =>
      o match {
        case keyRing: PGPPublicKeyRing =>
          println("PGPPublicKeyRing!")
          val key = keyRing.getPublicKey()
          println("[Key]")
          println(key.getValidSeconds)
          println(key.getKeyID.toHexString)
          println(key.getUserIDs.next)
          GpgKeys.insert(GpgKey(userName = userName, gpgKeyId = key.getKeyID, title = title, publicKey = publicKey))
        case x =>
          println(x.getClass)
          println(x)
      }
    }
  }

  def deleteGpgPublicKey(userName: String, keyId: Int)(implicit s: Session): Unit =
    GpgKeys.filter(_.byPrimaryKey(userName, keyId)).delete
}
