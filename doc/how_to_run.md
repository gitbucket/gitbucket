How to run from the source tree
========

Install [sbt](http://www.scala-sbt.org/index.html) at first.

```
$ brew install sbt
```

Run for Development
--------

If you want to test GitBucket, type the following command in the root directory of the source tree.

```
$ sbt ~jetty:start
```

Then access `http://localhost:8080/` in your browser. The default administrator account is `root` and password is `root`.

Source code modifications are detected and a reloaded happens automatically. You can modify the logging configuration by editing `src/main/resources/logback-dev.xml`.

Build war file
--------

To build war file, run the following command:

```
$ sbt package
```

`gitbucket_2.12-x.x.x.war` is generated into `target/scala-2.12`.

To build an executable war file, run

```
$ sbt executable
```

at the top of the source tree. It generates executable `gitbucket.war` into `target/executable`. We release this war file as release artifact.

Run tests spec
---------
To run the full series of tests, run the following command:

```
$ sbt test
```
