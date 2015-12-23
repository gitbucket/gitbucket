How to run from the source tree
========

Run for Development
--------

If you want to test GitBucket, input following command at the root directory of the source tree.

```
$ sbt ~jetty:start
```

Then access to `http://localhost:8080/` by your browser. The default administrator account is `root` and password is `root`.

Source code modification is detected and reloaded automatically. You can modify logging configuration by editing `src/main/resources/logback-test.xml`.

Build war file
--------

To build war file, run the following command:

```
$ sbt package
```

`gitbucket_2.11-x.x.x.war` is generated into `target/scala-2.11`.

To build executable war file, run

*  Windows: Not available
*  Linux: `./release/make-release-war.sh`

at the top of the source tree. It generates executable `gitbucket.war` into `target/scala-2.11`. We release this war file as release artifact.
