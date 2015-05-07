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
  val Version = "3.2.0" // <---- update here!!
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
    new Version(3, 2), // <---- add this!!
    new Version(3, 1),
    ...
```

### deploy-assembly.sh

```bash
#!/bin/sh
./sbt.sh assembly

mvn deploy:deploy-file \
  -DgroupId=gitbucket\
  -DartifactId=gitbucket-assembly\
  -Dversion=3.2.0\ # <---- update here!!
  -Dpackaging=jar\
  -Dfile=target/scala-2.11/gitbucket-assembly-x.x.x.jar\ # <---- update here!!
  -DrepositoryId=sourceforge.jp\
  -Durl=scp://shell.sourceforge.jp/home/groups/a/am/amateras/htdocs/mvn/
```

Release
--------

Note: Release operation requires And and Maven.

### Make release war file

Run ant with `build.xml` in the root directory. The release war file is generated into `target/scala-2.11/gitbucket.war`.

### Deploy assemnbly jar file

For plug-in development, we have to publish the assembly jar file to the public Maven repository.

```
./deploy-assembly.sh
```

This script runs `sbt assembly` and `mvn deploy`.
