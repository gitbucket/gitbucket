How to build and run from the source tree
========

First of all, Install [sbt](http://www.scala-sbt.org/index.html).

```shell
$ brew install sbt
```

Run for Development
--------

If you want to test GitBucket, type the following command in the root directory of the source tree.

```shell
$ sbt ~jetty:start
```

Then access `http://localhost:8080/` in your browser. The default administrator account is `root` and password is `root`.

Source code modifications are detected and a reloading happens automatically.
You can modify the logging configuration by editing `src/main/resources/logback-dev.xml`.

Note that HttpSession is cleared when auto-reloading happened.
This is a bit annoying when developing features that requires sign-in.
You can keep HttpSession even if GitBucket is restarted by enabling this configuration in `build.sbt`:
https://github.com/gitbucket/gitbucket/blob/d5c083b70f7f3748d080166252e9a3dcaf579648/build.sbt#L292

Or by launching GitBucket with the following command:
```shell
sbt '; set Jetty/javaOptions += "-Ddev-features=keep-session" ; ~jetty:start'
```

Note that this feature serializes HttpSession on the local disk and assigns all requests to the same session
which means you cannot test multi users behavior in this mode.

Build war file
--------

To build a war file, run the following command:

```shell
$ sbt package
```

`gitbucket_2.13-x.x.x.war` is generated into `target/scala-2.13`.

To build an executable war file, run

```shell
$ sbt executable
```

at the top of the source tree. It generates executable `gitbucket.war` into `target/executable`.
We release this war file as release artifact.

Run tests spec
---------
Before running tests, you need to install docker.

```shell
$ brew cask install docker       # Install Docker
$ open /Applications/Docker.app  # Start Docker
```

To run the full series of tests, run the following command:

```shell
$ sbt test
```

If you don't have docker, you can skip docker tests which require docker as follows:

```shell
$ sbt "testOnly * -- -l ExternalDBTest"
```
