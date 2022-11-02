GitBucket [![Gitter chat](https://badges.gitter.im/gitbucket/gitbucket.svg)](https://gitter.im/gitbucket/gitbucket) [![build](https://github.com/gitbucket/gitbucket/workflows/build/badge.svg?branch=master)](https://github.com/gitbucket/gitbucket/actions?query=workflow%3Abuild+branch%3Amaster) [![gitbucket Scala version support](https://index.scala-lang.org/gitbucket/gitbucket/gitbucket/latest-by-scala-version.svg)](https://index.scala-lang.org/gitbucket/gitbucket/gitbucket) [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/gitbucket/gitbucket/blob/master/LICENSE)
=========

GitBucket is a Git web platform powered by Scala offering:

- Easy installation
- Intuitive UI
- High extensibility by plugins
- API compatibility with GitHub

![GitBucket](https://gitbucket.github.io/img/screenshots/screenshot-repository_viewer.png)

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

Installation
--------
GitBucket requires **Java8**. You have to install it, if it is not already installed.

1. Download the latest **gitbucket.war** from [the releases page](https://github.com/gitbucket/gitbucket/releases) and run it by `java -jar gitbucket.war`.
2. Go to `http://[hostname]:8080/` and log in with ID: **root** / Pass: **root**.

You can also deploy `gitbucket.war` to a servlet container which supports Servlet 3.0 (like Jetty, Tomcat, JBoss, etc). Note that GitBucket doesn't support Jakarta EE yet.

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

Building and Development
-----------
If you want to try the development version of GitBucket, or want to contribute to the project, please see the [Developer's Guide](https://github.com/gitbucket/gitbucket/blob/master/doc/readme.md).
It provides instructions on building from source and on setting up an IDE for debugging. 
It also contains documentation of the core concepts used within the project.

Support
--------

- If you have any questions about GitBucket, see [Wiki](https://github.com/gitbucket/gitbucket/wiki) and check issues whether there is a same question or request in the past.
- If you can't find same question and report, send it to our [Gitter room](https://gitter.im/gitbucket/gitbucket) before raising an issue.
- The highest priority of GitBucket is the ease of installation and API compatibility with GitHub, so your feature request might be rejected if they go against those principles.

What's New in 4.38.x
-------------
## 4.38.4 - 2 Nov 2022
- Downgrade MariaDB JDBC drive to avoid unknown error

## 4.38.3 - 30 Oct 2022
- Fix several issues around multiple assignees in issues and pull requests
- Fix IllegalStateException when returning unknown avatar image

## 4.38.2 - 20 Sep 2022
- Resurrect assignee icons on the issue list

## 4.38.1 - 10 Sep 2022
- Fix comment diff in Chrome 105
- Fix Markdown table CSS
- Fix HTML rendering of multiple asignees

## 4.38.0 - 3 Sep 2022
- Support multiple assignees for Issues and Pull requests
- Custom fields for issues and pull requests
- Reset password by users
- Allow to configure Jetty idle timeout in standalone mode
- Horizontal scroll for too wide tables in Markdown
- Hide header content on signin and register page
- Fix the default charset of the online editor in the repository viewer
- Fix the milestone count
- Some improvements and bugfixes for WebAPI and WebHook

See the [change log](CHANGELOG.md) for all of the updates.
