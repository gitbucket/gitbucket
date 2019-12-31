package gitbucket.core

import io.github.gitbucket.solidbase.migration.{SqlMigration, LiquibaseMigration}
import io.github.gitbucket.solidbase.model.{Version, Module}

object GitBucketCoreModule
    extends Module(
      "gitbucket-core",
      new Version(
        "4.0.0",
        new LiquibaseMigration("update/gitbucket-core_4.0.xml"),
        new SqlMigration("update/gitbucket-core_4.0.sql")
      ),
      new Version("4.1.0"),
      new Version("4.2.0", new LiquibaseMigration("update/gitbucket-core_4.2.xml")),
      new Version("4.2.1"),
      new Version("4.3.0"),
      new Version("4.4.0"),
      new Version("4.5.0"),
      new Version("4.6.0", new LiquibaseMigration("update/gitbucket-core_4.6.xml")),
      new Version(
        "4.7.0",
        new LiquibaseMigration("update/gitbucket-core_4.7.xml"),
        new SqlMigration("update/gitbucket-core_4.7.sql")
      ),
      new Version("4.7.1"),
      new Version("4.8"),
      new Version("4.9.0", new LiquibaseMigration("update/gitbucket-core_4.9.xml")),
      new Version("4.10.0"),
      new Version("4.11.0", new LiquibaseMigration("update/gitbucket-core_4.11.xml")),
      new Version("4.12.0"),
      new Version("4.12.1"),
      new Version("4.13.0"),
      new Version(
        "4.14.0",
        new LiquibaseMigration("update/gitbucket-core_4.14.xml"),
        new SqlMigration("update/gitbucket-core_4.14.sql")
      ),
      new Version("4.14.1"),
      new Version("4.15.0"),
      new Version("4.16.0"),
      new Version("4.17.0"),
      new Version("4.18.0"),
      new Version("4.19.0"),
      new Version("4.19.1"),
      new Version("4.19.2"),
      new Version("4.19.3"),
      new Version("4.20.0"),
      new Version("4.21.0", new LiquibaseMigration("update/gitbucket-core_4.21.xml")),
      new Version("4.21.1"),
      new Version("4.21.2"),
      new Version("4.22.0", new LiquibaseMigration("update/gitbucket-core_4.22.xml")),
      new Version("4.23.0", new LiquibaseMigration("update/gitbucket-core_4.23.xml")),
      new Version("4.23.1"),
      new Version("4.24.0", new LiquibaseMigration("update/gitbucket-core_4.24.xml")),
      new Version("4.24.1"),
      new Version("4.25.0", new LiquibaseMigration("update/gitbucket-core_4.25.xml")),
      new Version("4.26.0"),
      new Version("4.27.0", new LiquibaseMigration("update/gitbucket-core_4.27.xml")),
      new Version("4.28.0"),
      new Version("4.29.0"),
      new Version("4.30.0"),
      new Version("4.30.1"),
      new Version("4.31.0", new LiquibaseMigration("update/gitbucket-core_4.31.xml")),
      new Version("4.31.1"),
      new Version("4.31.2"),
      new Version("4.32.0", new LiquibaseMigration("update/gitbucket-core_4.32.xml")),
      new Version("4.33.0")
    )
