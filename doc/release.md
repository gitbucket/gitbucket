Release Operation
========

Update version number
--------

Note to update version number in files below:

### build.sbt

```scala
val Organization = "gitbucket"
val Name = "gitbucket"
val GitBucketVersion = "4.0.0" // <---- update version!!
val ScalatraVersion = "2.4.0"
val JettyVersion = "9.3.6.v20151106"
```

### src/main/scala/gitbucket/core/GitBucketCoreModule.scala

```scala
object GitBucketCoreModule extends Module("gitbucket-core",
  new Version("4.0.0",
    new LiquibaseMigration("update/gitbucket-core_4.0.xml"),
    new SqlMigration("update/gitbucket-core_4.0.sql")
  ),
  // add new version definition
  new Version("4.1.0",
    new LiquibaseMigration("update/gitbucket-core_4.1.xml")
  )
)
```

Generate release files
--------

Note: Release operation requires [Ant](http://ant.apache.org/) and [Maven](https://maven.apache.org/).

### Make release war file

Run `sbt executable`. The release war file and fingerprint are generated into `target/executable/gitbucket.war`.

```bash
$ sbt executable
```

### Deploy assembly jar file

For plug-in development, we have to publish the GitBucket jar file to the Maven central repository as well. At first, hit following command to publish artifacts to the sonatype OSS repository:

```bash
$ sbt publish-signed
```

Then operate release sequence at https://oss.sonatype.org/.
