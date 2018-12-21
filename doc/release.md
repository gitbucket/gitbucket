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

### Deploy assembly jar file

For plug-in development, we have to publish the GitBucket jar file to the Maven central repository before release GitBucket itself.
 
First, hit following command to publish artifacts to the sonatype OSS repository:

```bash
$ sbt publishSigned
```

Then logged-in to https://oss.sonatype.org/, close and release the repository.

You need to wait up to a day until [gitbucket-notification-plugin](https://plugins.gitbucket-community.org/) which is default bundled plugin is built for new version of GitBucket.

### Make release war file

Run `sbt executable`. The release war file and fingerprint are generated into `target/executable/gitbucket.war`.

```bash
$ sbt executable
```

Create new release from the corresponded tag on GitHub, then upload generated jar file and fingerprints to the release.
