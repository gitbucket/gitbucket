GitBucket
=========

GitBucket is the easily installable Github clone written with Scala.

The current version of GitBucket provides a basic features below:

- Public / Private Git repository (http access only)
- Repository viewer (some advanced features are not implemented)
- Wiki
- Issues
- Activity timeline
- User management (for Administrators)

Following features are not implemented, but we will make them in the future release!

- Fork and pull request
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
### 1.4 - 31 Jul 2013
- Group management.
- Repository search for code and issues.
- Display user related issues on the dashboard.
- Display participants avatar of issues on the issue page.
- Performance improvement for repository viewer.
- Alert by milestone due date.
- H2 database administration console.
- Fixed some bugs.

### 1.3 - 18 Jul 2013
- Batch updating for issues.
- Display assigned user on issue list.
- User icon and Gravatar support.
- Convert @xxxx to link to the account page.
- Add copy to clipboard button for git clone URL.
- Allows multi-byte characters as wiki page name.
- Allows to create the empty repository.
- Fixed some bugs.

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
