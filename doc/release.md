Release Operation
========

Update version number
--------

Note to update version number in files below:

### project/build.scala

```scala
object MyBuild extends Build {
  val Organization = "gitbucket"
  val Name = "gitbucket"
  val Version = "3.3.0" // <---- update version!!
  val ScalaVersion = "2.11.6"
  val ScalatraVersion = "2.3.1"
```

### src/main/scala/gitbucket/core/servlet/AutoUpdate.scala

```scala
object AutoUpdate {

  /**
   * The history of versions. A head of this sequence is the current BitBucket version.
   */
  val versions = Seq(
    new Version(3, 3), // <---- add this line!!
    new Version(3, 2),
```

Generate release files
--------

Note: Release operation requires [Ant](http://ant.apache.org/) and [Maven](https://maven.apache.org/).

### Make release war file

Run `release/make-release-war.sh`. The release war file is generated into `target/scala-2.11/gitbucket.war`.

```bash
$ cd release
$ ./make-release-war.sh
```

### Deploy assembly jar file

For plug-in development, we have to publish the assembly jar file to the public Maven repository by `release/deploy-assembly-jar.sh`.

```bash
$ cd release/
$ ./deploy-assembly-jar.sh
```
