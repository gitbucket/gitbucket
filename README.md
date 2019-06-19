GitBucket [![Gitter chat](https://badges.gitter.im/gitbucket/gitbucket.svg)](https://gitter.im/gitbucket/gitbucket) [![Build Status](https://travis-ci.org/gitbucket/gitbucket.svg?branch=master)](https://travis-ci.org/gitbucket/gitbucket) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.github.gitbucket/gitbucket_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.github.gitbucket/gitbucket_2.12) [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/gitbucket/gitbucket/blob/master/LICENSE)
=========

GitBucket is a Git web platform powered by Scala offering:

- Easy installation
- Intuitive UI
- High extensibility by plugins
- API compatibility with GitHub

You can try an [online demo](https://gitbucket.herokuapp.com/) *(ID: root / Pass: root)* of GitBucket, and also get the latest information at [GitBucket News](https://gitbucket.github.io/gitbucket-news/).

Features
--------
The current version of GitBucket provides many features such as:

- Public / Private Git repositories (with http/https and ssh access)
- GitLFS support
- Repository viewer including an online file editor
- Issues, Pull Requests and Wiki for repositories
- Activity timeline and email notifications
- Account and group management with LDAP integration
- a Plug-in system

If you want to try the development version of GitBucket, see the [Developer's Guide](https://github.com/gitbucket/gitbucket/blob/master/doc/readme.md).

Installation
--------
GitBucket requires **Java8**. You have to install it, if it is not already installed.

1. Download the latest **gitbucket.war** from [the releases page](https://github.com/gitbucket/gitbucket/releases) and run it by `java -jar gitbucket.war`.
2. Go to `http://[hostname]:8080/` and log in with ID: **root** / Pass: **root**.

You can specify following options:

- `--port=[NUMBER]`
- `--prefix=[CONTEXTPATH]`
- `--host=[HOSTNAME]`
- `--gitbucket.home=[DATA_DIR]`
- `--temp_dir=[TEMP_DIR]`
- `--max_file_size=[MAX_FILE_SIZE]`

`TEMP_DIR` is used as the [temporary directory for the jetty application context](https://www.eclipse.org/jetty/documentation/9.3.x/ref-temporary-directories.html). This is the directory into which the `gitbucket.war` file is unpacked, the source files are compiled, etc. If given this parameter **must** match the path of an existing directory or the application will quit reporting an error; if not given the path used will be a `tmp` directory inside the gitbucket home.

`MAX_FILE_SIZE` is the max file size for upload files.

You can also deploy `gitbucket.war` to a servlet container which supports Servlet 3.0 (like Jetty, Tomcat, JBoss, etc)

For more information about installation on Mac or Windows Server (with IIS), or configuration of Apache or Nginx and also integration with other tools or services such as Jenkins or Slack, see [Wiki](https://github.com/gitbucket/gitbucket/wiki).

To upgrade GitBucket, replace `gitbucket.war` with the new version, after stopping GitBucket. All GitBucket data is stored in `HOME/.gitbucket` by default. So if you want to back up GitBucket's data, copy this directory to the backup location.

Plugins
--------
GitBucket has a plug-in system that allows extra functionality. Officially the following plug-ins are provided:

- [gitbucket-gist-plugin](https://github.com/gitbucket/gitbucket-gist-plugin)
- [gitbucket-emoji-plugin](https://github.com/gitbucket/gitbucket-emoji-plugin)
- [gitbucket-pages-plugin](https://github.com/gitbucket/gitbucket-pages-plugin)
- [gitbucket-notifications-plugin](https://github.com/gitbucket/gitbucket-notifications-plugin)

You can find more plugins made by the community at [GitBucket community plugins](https://gitbucket-plugins.github.io/).

Support
--------

- If you have any questions about GitBucket, see [Wiki](https://github.com/gitbucket/gitbucket/wiki) and check issues whether there is a same question or request in the past.
- If you can't find same question and report, send it to [gitter room](https://gitter.im/gitbucket/gitbucket) before raising an issue.
- The highest priority of GitBucket is the ease of installation and API compatibility with GitHub, so your feature request might be rejected if they go against those principles.

What's New in 4.31.x
-------------
### 4.31.2 - 7 Apr 2019
- Bug and security fix

### 4.31.1 - 17 Mar 2019
- Bug fix

### 4.31.0 - 17 Mar 2019
- Docker support in CI plugin
- Verify GPG key signed commit
- OAuth2 Token (sent as a parameter) authentication support and new APIs in Web API
- OGP (Open Graph protocol) support
- Username completion with avatars

See the [change log](CHANGELOG.md) for all of the updates.
