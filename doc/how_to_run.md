How to run from the source tree
========

for Testers
--------

If you want to test GitBucket, input following command at the root directory of the source tree.

```
$ sbt jetty:start jetty:join
```

Then access to `http://localhost:8080/` by your browser. The default administrator account is `root` and password is `root`.

`CTRL + C` to shutdown.

for Developers
--------
If you want to modify source code and confirm it, you can run GitBucket in auto reloading mode as following:

```
$ sbt
...
> jetty:start
> ~compile
```

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
