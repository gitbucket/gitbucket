JRebel integration (optional)
=============================

[JRebel](http://zeroturnaround.com/software/jrebel/) is a JVM plugin that makes developing web apps much faster.
JRebel is generally able to eliminate the need for the following slow "app restart" in sbt following a code change:

```
> jetty:start
```

While JRebel is not open source, it does reload your code faster than the `~;copy-resources;aux-compile` way of doing things using `sbt`.

It's only used during development, and doesn't change your deployed app in any way.

JRebel used to be free for Scala developers, but that changed recently, and now there's a cost associated with usage for Scala. There are trial plans and free non-commercial licenses available if you just want to try it out.

----

## 1. Get a JRebel license

Sign up for a [usage plan](https://my.jrebel.com/). You will need to create an account.

## 2. Download JRebel

Download the most recent ["nosetup" JRebel zip](http://zeroturnaround.com/software/jrebel/download/prev-releases/).
Next, unzip the downloaded file.

## 3. Activate

Follow the [instructions on the JRebel website](http://zeroturnaround.com/software/jrebel/download/prev-releases/) to activate your downloaded JRebel.

You can use the default settings for all the configurations.

You don't need to integrate with your IDE, since we're using sbt to do the servlet deployment.

## 4. Tell jvm where JRebel is

Fortunately, the gitbucket project is already set up to use JRebel.
You only need to tell jvm where to find the jrebel jar.

To do so, edit your shell resource file (usually `~/.bash_profile` on Mac, and `~/.bashrc` on Linux), and add the following line:

```bash
export JREBEL=/path/to/jrebel/jrebel.jar
```

For example, if you unzipped your JRebel download in your home directory, you whould use:

```bash
export JREBEL=~/jrebel/jrebel.jar
```

Now reload your shell:

```
$ source ~/.bash_profile # on Mac
$ source ~/.bashrc       # on Linux
```

## 5. See it in action!

Now you're ready to use JRebel with the gitbucket.
When you run sbt as normal, you will see a long message from JRebel, indicating it has loaded.
Here's an abbreviated version of what you will see:

```
$ ./sbt
[info] Loading project definition from /git/gitbucket/project
[info] Set current project to gitbucket (in build file:/git/gitbucket/)
>
```

You will start the servlet container slightly differently now that you're using sbt.

```
> jetty:start
:
[info] starting server ...
[success] Total time: 3 s, completed Jan 3, 2016 9:47:55 PM
2016-01-03 21:47:57 JRebel:
2016-01-03 21:47:57 JRebel: A newer version '6.3.1' is available for download
2016-01-03 21:47:57 JRebel: from http://zeroturnaround.com/software/jrebel/download/
2016-01-03 21:47:57 JRebel:
2016-01-03 21:47:58 JRebel: Contacting myJRebel server ..
2016-01-03 21:47:59 JRebel: Directory '/git/gitbucket/target/scala-2.11/classes' will be monitored for changes.
2016-01-03 21:47:59 JRebel: Directory '/git/gitbucket/target/scala-2.11/test-classes' will be monitored for changes.
2016-01-03 21:47:59 JRebel: Directory '/git/gitbucket/target/webapp' will be monitored for changes.
2016-01-03 21:48:00 JRebel:
2016-01-03 21:48:00 JRebel:  #############################################################
2016-01-03 21:48:00 JRebel:
2016-01-03 21:48:00 JRebel:  JRebel Legacy Agent 6.2.5 (201509291538)
2016-01-03 21:48:00 JRebel:  (c) Copyright ZeroTurnaround AS, Estonia, Tartu.
2016-01-03 21:48:00 JRebel:
2016-01-03 21:48:00 JRebel:  Over the last 30 days JRebel prevented
2016-01-03 21:48:00 JRebel:  at least 182 redeploys/restarts saving you about 7.4 hours.
2016-01-03 21:48:00 JRebel:
2016-01-03 21:48:00 JRebel:  Over the last 324 days JRebel prevented
2016-01-03 21:48:00 JRebel:  at least 1538 redeploys/restarts saving you about 62.4 hours.
2016-01-03 21:48:00 JRebel:
2016-01-03 21:48:00 JRebel:  Licensed to nazo king (using myJRebel).
2016-01-03 21:48:00 JRebel:
2016-01-03 21:48:00 JRebel:
2016-01-03 21:48:00 JRebel:  #############################################################
2016-01-03 21:48:00 JRebel:
:

> ~ copy-resources
[success] Total time: 0 s, completed Jan 3, 2016 9:13:54 PM
1. Waiting for source changes... (press enter to interrupt)
```

Finally, change your code.
For example, you can change the title on `src/main/twirl/gitbucket/core/main.scala.html` like this:

```html
:
  <a class="navbar-brand" href="@path/">
    <img src="@assets/common/images/gitbucket.png" style="width: 24px; height: 24px;"/>GitBucket
    @defining(AutoUpdate.getCurrentVersion){ version =>
      <span class="header-version">@version.majorVersion.@version.minorVersion</span>
    }
    change code !!!!!!!!!!!!!!!!
  </a>
:
```

If JRebel is doing is correctly installed you will see a notice for you:

```
1. Waiting for source changes... (press enter to interrupt)
2016-01-03 21:48:42 JRebel: Reloading class 'gitbucket.core.html.main$'.
[info] Wrote rebel.xml to /git/gitbucket/target/scala-2.11/resource_managed/main/rebel.xml
[info] Compiling 1 Scala source to /git/gitbucket/target/scala-2.11/classes...
[success] Total time: 3 s, completed Jan 3, 2016 9:48:55 PM
2. Waiting for source changes... (press enter to interrupt)
```

And you reload browser, JRebel give notice of that it has reloaded classes:

```
[success] Total time: 3 s, completed Jan 3, 2016 9:48:55 PM
2. Waiting for source changes... (press enter to interrupt)
2016-01-03 21:49:13 JRebel: Reloading class 'gitbucket.core.html.main$'.
```

## 6. Limitations

JRebel is nearly always able to eliminate the need to explicitly reload your container after a code change. However, if you change any of your routes patterns, there is nothing JRebel can do, you will have to run `jetty:start`.
