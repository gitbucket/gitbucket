How to build and run from the source tree
========

Prerequisites
--------

This repository includes `sbt` (macOS/Linux) and `sbt.bat` (Windows) wrapper scripts that automatically download and run the required sbt version — no system-wide sbt installation is needed.

**Requirements:** JDK 17 or later. The wrapper will notify you if your JDK version is insufficient.

On macOS and Linux, ensure the wrapper is executable:

```shell
chmod +x ./sbt
```

On Windows, use `sbt.bat` instead.

Common commands:

```shell
./sbt "~Container / start"      # Run for development (auto-reload on changes)
./sbt test                      # Run tests
./sbt package                   # Build war file
./sbt executable                # Build executable war file
```

Run for Development
--------

If you want to test GitBucket, type the following command in the root directory of the source tree.

```shell
./sbt "~Container / start"
```

Then access `http://localhost:8080/` in your browser. The default administrator account is `root` and password is `root`.

Source code modifications are detected and reloading happens automatically.
You can modify the logging configuration by editing `src/main/resources/logback-dev.xml`.

Note that HttpSession is cleared when auto-reloading happens.
This is a bit annoying when developing features that require sign-in.
You can keep HttpSession even if GitBucket is restarted by enabling this configuration in `build.sbt`:
https://github.com/gitbucket/gitbucket/blob/3dcc0aee3c4413b05be7c03476626cb202674afc/build.sbt#L292

Or by launching GitBucket with the following command:
```shell
./sbt '; set Container/javaOptions += "-Ddev-features=keep-session" ; "~Container / start"'
```

Note that this feature serializes HttpSession on the local disk and assigns all requests to the same session
which means you cannot test multi-user behavior in this mode.

Build war file
--------

To build a war file, run the following command:

```shell
./sbt package
```

`gitbucket_2.13-x.x.x.war` is generated into `target/scala-2.13`.

To build an executable war file:

```shell
./sbt executable
```

Generates `gitbucket.war` into `target/executable`. This is the release artifact.

Run Tests
--------
Before running tests, you need to install Docker.

```shell
# macOS — see Docker docs for Linux/Windows installation
brew cask install docker
open /Applications/Docker.app
```

To run the full series of tests, run the following command:

```shell
./sbt test
```

If you don't have Docker, you can skip Docker tests which require Docker as follows:

```shell
./sbt "testOnly * -- -l ExternalDBTest"
```