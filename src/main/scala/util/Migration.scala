package util

import java.io._
import org.apache.commons.io.FileUtils

object Migration {
  
  /**
   * Define Migrator interface.
   */
  trait Migrator {
    def migrate(): Unit
  }
  
  /**
   * Migrate H2 database by SQL files.
   */
  case class DatabaseMigrator(sqlPath: String) extends Migrator {
    def migrate(): Unit = {
      // TODO
    }
  }
  
  case class Version(majorVersion: Int, minorVersion: Int, migrators: Migrator*)
  
  /**
   * The history of versions. A head of this sequence is the current BitBucket version.
   * Migration#migrate() updates the data directory to move to the head version.
   */
  val versions = Seq(
      Version(1, 0, DatabaseMigrator("migration/1.0/createdb.sql"))
  )
  
  /**
   * The head version of BitBucket.
   */
  val headVersion = versions.head
  
  /**
   * The version file (GITBUCKET_HOME/version).
   */
  val versionFile = new File(Directory.GitBucketHome, "version")
  
  /**
   * Returns the current version
   */
  def getCurrentVersion(): Version = {
    FileUtils.readFileToString(versionFile).split(".") match {
      case Array(majorVersion, minorVersion) => {
        versions.find { v => v.majorVersion == majorVersion.toInt && v.minorVersion == minorVersion.toInt }.get
      }
    }
  }
  
  /**
   * Do migrate old data directory to the head version.
   */
  def migrate(): Unit = {
    val currentVersion = getCurrentVersion()
    versions.takeWhile(_ != currentVersion).reverse.foreach(_.migrators.foreach(_.migrate()))
    FileUtils.writeStringToFile(versionFile, headVersion.majorVersion + "." + headVersion.minorVersion)
  }
  
  
}