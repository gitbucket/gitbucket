JRebel integration (optional)
=============================

[JRebel](https://zeroturnaround.com/software/jrebel/) is a JVM plugin that makes developing web apps much faster.
JRebel is generally able to eliminate the need for the slow "app restart" per modification of codes. Alsp it's only used during development, and doesn't change your deployed app in any way.

JRebel is not open source, but we can use it free for non-commercial use.

----

## 1. Get a JRebel license

Sign up for a [myJRebel](https://my.jrebel.com/register). You will need to create an account.

## 2. Download JRebel

Download the most recent ["nosetup" JRebel zip](https://zeroturnaround.com/software/jrebel/download/prev-releases/).
Next, unzip the downloaded file.

## 3. Activate

Follow `readme.txt` in the extracted directory to activate your downloaded JRebel.

You don't need to integrate with your IDE, since we're using sbt to do the servlet deployment.

## 4. Tell jvm where JRebel is

Fortunately, the gitbucket project is already set up to use JRebel.
You only need to tell jvm where to find the jrebel jar.

To do so, edit your shell resource file (usually `~/.bash_profile` on Mac, and `~/.bashrc` on Linux), and add the following line:

```bash
export JREBEL=/path/to/jrebel/legacy/jrebel.jar
```

For example, if you unzipped your JRebel download in your home directory, you whould use:

```bash
export JREBEL=~/jrebel/legacy/jrebel.jar
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
> jetty:quickstart
:
2017-09-21 15:46:35 JRebel:
2017-09-21 15:46:35 JRebel:  #############################################################
2017-09-21 15:46:35 JRebel:
2017-09-21 15:46:35 JRebel:  Legacy Agent 7.0.15 (201709080836)
2017-09-21 15:46:35 JRebel:  (c) Copyright ZeroTurnaround AS, Estonia, Tartu.
2017-09-21 15:46:35 JRebel:
2017-09-21 15:46:35 JRebel:  Over the last 2 days JRebel prevented
2017-09-21 15:46:35 JRebel:  at least 8 redeploys/restarts saving you about 0.3 hours.
2017-09-21 15:46:35 JRebel:
2017-09-21 15:46:35 JRebel:  Licensed to Naoki Takezoe (using myJRebel).
2017-09-21 15:46:35 JRebel:
2017-09-21 15:46:35 JRebel:
2017-09-21 15:46:35 JRebel:  #############################################################
2017-09-21 15:46:35 JRebel:
:

> ~compile
[success] Total time: 2 s, completed 2017/09/21 15:50:06
1. Waiting for source changes... (press enter to interrupt)
```

Finally, change your code.
For example, you can change the title on `src/main/twirl/gitbucket/core/main.scala.html` like this:

```html
:
  <a href="@context.path/" class="logo">
    <img src="@helpers.assets("/common/images/gitbucket.svg")" style="width: 24px; height: 24px; display: inline;"/>
    GitBucket
    change code !!!!!!!!!!!!!!!!
    <span class="header-version">@gitbucket.core.GitBucketCoreModule.getVersions.last.getVersion</span>
  </a>
:
```

If JRebel is doing is correctly installed you will see a notice for you:

```
1. Waiting for source changes... (press enter to interrupt)
[info] Compiling 1 Scala source to /Users/naoki.takezoe/gitbucket/target/scala-2.12/classes...
[success] Total time: 1 s, completed 2017/09/21 15:55:40
```

And you reload browser, JRebel give notice of that it has reloaded classes:

```
2. Waiting for source changes... (press enter to interrupt)
2017-09-21 15:55:40 JRebel: Reloading class 'gitbucket.core.html.main$'.
```

## 6. Limitations

JRebel is nearly always able to eliminate the need to explicitly reload your container after a code change. However, if you change any of your routes patterns, there is nothing JRebel can do, you will have to restart Jetty by `jetty:quickstart`.
