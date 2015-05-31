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

### build.xml

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<project name="gitbucket" default="all" basedir=".">

  <property name="target.dir" value="target"/>
  <property name="embed.classes.dir" value="${target.dir}/embed-classes"/>
  <property name="jetty.dir" value="embed-jetty"/>
  <property name="scala.version" value="2.11"/>
  <property name="gitbucket.version" value="3.2.0"/> <!---- update here!! ---->
  <property name="jetty.version" value="8.1.16.v20140903"/>
  <property name="servlet.version" value="3.0.0.v201112011016"/>
  ...
```

### deploy-assembly/deploy-assembly-jar.sh

```bash
#!/bin/sh
mvn deploy:deploy-file \
  -DgroupId=gitbucket\
  -DartifactId=gitbucket-assembly\
  -Dversion=3.2.0\ # <---- update here!!
  -Dpackaging=jar\
  -Dfile=../target/scala-2.11/gitbucket-assembly-3.2.0.jar\ # <---- update here!!
  -DrepositoryId=sourceforge.jp\
  -Durl=scp://shell.sourceforge.jp/home/groups/a/am/amateras/htdocs/mvn/
```

Generate release files
--------

Note: Release operation requires [Ant](http://ant.apache.org/) and [Maven](https://maven.apache.org/).

### Make release war file

Run ant with `build.xml` in the root directory. The release war file is generated into `target/scala-2.11/gitbucket.war`.

### Deploy assemnbly jar file

For plug-in development, we have to publish the assembly jar file to the public Maven repository.

```
./sbt.sh clean assembly
cd deploy-assembly/
./deploy-assembly-jar.sh
```

This script runs `sbt assembly` and `mvn deploy`.
