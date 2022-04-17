Automatic Schema Updating
========
GitBucket updates database schema automatically using [Solidbase](https://github.com/gitbucket/solidbase) in the first run after the upgrading.

To release a new version of GitBucket, add the version definition to [gitbucket.core.GitBucketCoreModule](https://github.com/gitbucket/gitbucket/blob/master/src/main/scala/gitbucket/core/GitBucketCoreModule.scala) at first.

```scala
object GitBucketCoreModule extends Module("gitbucket-core",
  new Version("4.0.0",
    new LiquibaseMigration("update/gitbucket-core_4.0.xml"),
    new SqlMigration("update/gitbucket-core_4.0.sql")
  ),
  new Version("4.1.0"),
  new Version("4.2.0",
    new LiquibaseMigration("update/gitbucket-core_4.2.xml")
  )
)
```

Next, add a XML file which updates database schema into [/src/main/resources/update/](https://github.com/gitbucket/gitbucket/tree/master/src/main/resources/update) with a filename defined in `GitBucketCoreModule`.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<changeSet>
    <addColumn tableName="REPOSITORY">
        <column name="ENABLE_WIKI" type="boolean" nullable="false" defaultValueBoolean="true"/>
        <column name="ENABLE_ISSUES" type="boolean" nullable="false" defaultValueBoolean="true"/>
        <column name="EXTERNAL_WIKI_URL" type="varchar(200)" nullable="true"/>
        <column name="EXTERNAL_ISSUES_URL" type="varchar(200)" nullable="true"/>
    </addColumn>
</changeSet>
```

Solidbase stores the current version to `VERSIONS` table and checks it at start-up. If the stored version different from the actual version, it executes differences between the stored version and the actual version.

We can add the SQL file instead of the XML file using `SqlMigration`. It tries to load a SQL file from classpath in the following order:

1. Specified path (if specified)
2. `${moduleId}_${version}_${database}.sql`
3. `${moduleId}_${version}.sql`

Also we can add any code by extending `Migration`:

```scala
object GitBucketCoreModule extends Module("gitbucket-core",
  new Version("4.0.0", new Migration(){
    override def migrate(moduleId: String, version: String, context: java.util.Map[String, String]): Unit = {
      ...
    }
  })
)
```

See more details at [README of Solidbase](https://github.com/gitbucket/solidbase).
