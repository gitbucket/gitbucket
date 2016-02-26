Release Operation
========

Update version number
--------

Note to update version number in files below:

### project/build.scala

```scala
val Organization = "gitbucket"
val Name = "gitbucket"
val GitBucketVersion = "3.12.0" // <---- update version!!
val ScalatraVersion = "2.4.0"
val JettyVersion = "9.3.6.v20151106"
```

### src/main/scala/gitbucket/core/servlet/AutoUpdate.scala

```scala
object AutoUpdate {

  /**
   * The history of versions. A head of this sequence is the current GitBucket version.
   */
  val versions = Seq(
    new Version(3, 12), // <---- add this line!!
    new Version(3, 11),
```

Generate release files
--------

Note: Release operation requires [Ant](http://ant.apache.org/) and [Maven](https://maven.apache.org/).

### Make release war file

Run `sbt executable`. The release war file and fingerprint are generated into `target/executable/gitbucket.war`.

```bash
$sbt executable
```

### Deploy assembly jar file

For plug-in development, we have to publish the assembly jar file to the public Maven repository by `release/deploy-assembly-jar.sh`.

```bash
$ cd release/
$ ./deploy-assembly-jar.sh
```
