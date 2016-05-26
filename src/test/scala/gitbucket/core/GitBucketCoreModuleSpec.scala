package gitbucket.core

import java.sql.DriverManager

import io.github.gitbucket.solidbase.Solidbase
import io.github.gitbucket.solidbase.model.Module
import liquibase.database.core.{H2Database, MySQLDatabase, PostgresDatabase}
import org.scalatest.FunSuite
import com.wix.mysql.EmbeddedMysql._
import com.wix.mysql.config.Charset
import com.wix.mysql.config.MysqldConfig._
import com.wix.mysql.distribution.Version._
import ru.yandex.qatools.embed.postgresql.PostgresStarter
import ru.yandex.qatools.embed.postgresql.config.AbstractPostgresConfig.{Credentials, Net, Storage, Timeout}
import ru.yandex.qatools.embed.postgresql.config.PostgresConfig
import ru.yandex.qatools.embed.postgresql.distribution.Version.Main.PRODUCTION

class GitBucketCoreModuleSpec extends FunSuite {

  test("Migration H2"){
    new Solidbase().migrate(
      DriverManager.getConnection("jdbc:h2:mem:test", "sa", "sa"),
      Thread.currentThread().getContextClassLoader(),
      new H2Database(),
      new Module(GitBucketCoreModule.getModuleId, GitBucketCoreModule.getVersions)
    )
  }

  test("Migration MySQL"){
    val config = aMysqldConfig(v5_7_10)
      .withPort(3306)
      .withUser("gitbucket", "gitbucket")
      .withCharset(Charset.UTF8)
      .build()

    val mysqld = anEmbeddedMysql(config)
      .addSchema("gitbucket")
      .start()

    try {
      new Solidbase().migrate(
        DriverManager.getConnection("jdbc:mysql://localhost:3306/gitbucket", "gitbucket", "gitbucket"),
        Thread.currentThread().getContextClassLoader(),
        new MySQLDatabase(),
        new Module(GitBucketCoreModule.getModuleId, GitBucketCoreModule.getVersions)
      )
    } finally {
      mysqld.stop()
    }
  }

  test("Migration PostgreSQL"){
    val runtime = PostgresStarter.getDefaultInstance()
    val config = new PostgresConfig(
      PRODUCTION,
      new Net("localhost", 5432),
      new Storage("gitbucket"),
      new Timeout(),
      new Credentials("root", "root"))

    val exec = runtime.prepare(config)
    val process = exec.start()

    try {
      new Solidbase().migrate(
        DriverManager.getConnection("jdbc:postgresql://localhost:5432/gitbucket", "root", "root"),
        Thread.currentThread().getContextClassLoader(),
        new PostgresDatabase(),
        new Module(GitBucketCoreModule.getModuleId, GitBucketCoreModule.getVersions)
      )
    } finally {
      process.stop()
    }
  }

}
