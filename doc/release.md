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

### env.sh

```bash
#!/bin/sh
export GITBUCKET_VERSION=3.3.0 # <---- update here!!
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
