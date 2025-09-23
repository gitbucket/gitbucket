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
 
First, stage artifacts on your machine:

```bash
$ sbt publishSigned
```

Next, upload artifacts to Sonatype's Central Portal with the following command:

```bash
$ sbt sonaUpload
```

Then logged-in to https://central.sonatype.com/ and publish the deployment.

You need to wait up to a day until default bundled plugins:

- https://github.com/gitbucket/gitbucket-notifications-plugin
- https://github.com/gitbucket/gitbucket-gist-plugin
- https://github.com/gitbucket/gitbucket-pages-plugin
- https://github.com/gitbucket/gitbucket-emoji-plugin

### Make release war file

Run `sbt executable`. The release war file and fingerprint are generated into `target/executable/gitbucket.war`.

```bash
$ sbt executable
```
Create new release from the corresponded tag on GitHub, then upload generated jar file and fingerprints to the release.
