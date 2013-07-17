GitBucket
=========

GitBucket is the easily installable Github clone written with Scala.

The current version of GitBucket provides a basic features below:

- Public / Private Git repository (http access only)
- Repository viewer (some advanced features are not implemented)
- Wiki
- Issues
- User management (for Administrators)

Following features are not implemented, but we will make them in the future release!

- Fork and pull request
- Timeline
- Search
- Network graph
- Statics
- Watch / Star
- Team management (like Organization in Github)

If you want to try the development version of GitBucket, see the documentation for developers at [Wiki](https://github.com/takezoe/gitbucket/wiki).

Installation
--------

1. Download latest **gitbucket.war** from [the release page](https://github.com/takezoe/gitbucket/releases).
2. Deploy it to the servlet container such as Tomcat or Jetty.
3. Access **http://[hostname]:[port]/gitbucket/** using your web browser.

The default administrator account is **root** and password is **root**.

To upgrade GitBucket, only replace gitbucket.war.

Release Notes
--------
### 1.2 - 09 Jul 2013
- Added activity timeline.
- Bugfix for Git 1.8.1.5 or later.
- Allows multi-byte characters as label.
- Fixed some bugs.

### 1.1 - 05 Jul 2013
- Fixed some bugs.
- Upgrade to JGit 3.0.

### 1.0 - 04 Jul 2013
- This is a first public release.
