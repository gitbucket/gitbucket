package gitbucket.core

import io.github.gitbucket.solidbase.migration.{SqlMigration, LiquibaseMigration}
import io.github.gitbucket.solidbase.model.{Version, Module}

object GitBucketCoreModule extends Module("gitbucket-core",
  new Version("4.0.0",
    new LiquibaseMigration("update/gitbucket-core_4.0.xml"),
    new SqlMigration("update/gitbucket-core_4.0.sql")
  ),
  new Version("4.1.0"),
  new Version("4.2.0",
    new LiquibaseMigration("update/gitbucket-core_4.2.xml")
  ),
  new Version("4.2.1")
)
