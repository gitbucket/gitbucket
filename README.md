GitBucket [![Gitter chat](https://badges.gitter.im/takezoe/gitbucket.png)](https://gitter.im/takezoe/gitbucket) [![Build Status](https://travis-ci.org/takezoe/gitbucket.svg?branch=master)](https://travis-ci.org/takezoe/gitbucket)
=========

GitBucket is the easily installable GitHub clone powered by Scala.


Features
--------
The current version of GitBucket provides a basic features below:

- Public / Private Git repository (http and ssh access)
- Repository viewer and online file editing
- Repository search (Code and Issues)
- Wiki
- Issues
- Fork / Pull request
- Mail notification
- Activity timeline
- User management (for Administrators)
- Group (like Organization in Github)
- LDAP integration
- Gravatar support

Following features are not implemented, but we will make them in the future release!

- Network graph
- Statistics
- Watch / Star

If you want to try the development version of GitBucket, see the documentation for developers at [Wiki](https://github.com/takezoe/gitbucket/wiki).

Installation
--------

1. Download latest **gitbucket.war** from [the release page](https://github.com/takezoe/gitbucket/releases).
2. Deploy it to the Servlet 3.0 container such as Tomcat 7.x, Jetty 8.x, GlassFish 3.x or higher.
3. Access **http://[hostname]:[port]/gitbucket/** using your web browser.

If you are using Gitbucket behind a webserver please make sure you have increased the **client_max_body_size** (on nignx)

The default administrator account is **root** and password is **root**.

or you can start GitBucket by `java -jar gitbucket.war` without servlet container. In this case, GitBucket URL is **http://[hostname]:8080/**. You can specify following options.

- --port=[NUMBER]
- --prefix=[CONTEXTPATH]
- --host=[HOSTNAME]
- --gitbucket.home=[DATA_DIR]

To upgrade GitBucket, only replace gitbucket.war. All GitBucket data is stored in HOME/.gitbucket. So if you want to back up GitBucket data, copy this directory to the other disk.

For Installation on Windows Server with IIS see [this wiki page](https://github.com/takezoe/gitbucket/wiki/Installation-on-IIS-and-Helicontech-Zoo)

### Mac OS X
#### Installing Via Homebrew

    $ brew install gitbucket
    ==> Downloading https://github.com/takezoe/gitbucket/releases/download/1.10/gitbucket.war
    ######################################################################## 100.0%
    ==> Caveats
    Note: When using launchctl the port will be 8080.

    To have launchd start gitbucket at login:
        ln -sfv /usr/local/opt/gitbucket/*.plist ~/Library/LaunchAgents
    Then to load gitbucket now:
        launchctl load ~/Library/LaunchAgents/homebrew.mxcl.gitbucket.plist
    Or, if you don't want/need launchctl, you can just run:
        java -jar /usr/local/opt/gitbucket/libexec/gitbucket.war
    ==> Summary
    /usr/local/Cellar/gitbucket/1.10: 3 files, 42M, built in 11 seconds

#### Manual Installation
On OS X, copy the [gitbucket.plist](https://raw.github.com/takezoe/gitbucket/master/contrib/macosx/gitbucket.plist) file to `~/Library/LaunchAgents/`

Run the following commands in `Terminal` to

- start gitbucket: `launchctl load ~/Library/LaunchAgents/gitbucket.plist`
- stop gitbucket: `launchctl unload ~/Library/LaunchAgents/gitbucket.plist`

Release Notes
--------
### 3.0 - 3 Mar 2015
- New plug-in system is available
- Connection pooling by c3p0
- New branch UI
- Compare between specified commit ids

### 2.8 - 1 Feb 2015
- New logo and icons
- New system setting options to control visibility
- Comment on side-by-side diff
- Information message on sign-in page
- Fork repository by group account

### 2.7 - 29 Dec 2014
- Comment for commit and diff
- Fix security issue in markdown rendering
- Some bug fix and improvements

### 2.6 - 24 Nov 2014
- Search box at issues and pull requests
- Information from administrator
- Pull request UI has been updated
- Move to TravisCI from Buildhive
- Some bug fix and improvements

### 2.5 - 4 Nov 2014
- New Dashboard
- Change datetime format
- Create branch from Web UI
- Task list in Markdown
- Some bug fix and improvements

### 2.4.1 - 6 Oct 2014
- Bug fix

### 2.4 - 6 Oct 2014
- New UI is applied to Issues and Pull requests
- Side-by-side diff is available
- Fix relative path problem in Markdown links and images
- Plugin System is disabled in default
- Some bug fix and improvements

### 2.3 - 1 Sep 2014
- Scala based plugin system
- Embedded Jetty war extraction directory moved to `GITBUCKET_HOME/tmp`
- Some bug fix and improvements

### 2.2.1 - 5 Aug 2014
- Bug fix

### 2.2 - 4 Aug 2014
- Plug-in system is available
- Move to Scala 2.11, Scalatra 2.3 and Slick 2.1
- tar.gz export for repository contents
- LDAP authentication improvement (mail address became optional)
- Show news feed of a private repository to members
- Some bug fix and improvements

### 2.1 - 6 Jul 2014
- Upgrade to Slick 2.0 from 1.9
- Base part of the plug-in system is merged
- Many bug fix and improvements

### 2.0 - 31 May 2014
- Modern Github UI
- Preview in AceEditor
- Select lines by clicking line number in blob view

### 1.13 - 29 Apr 2014
- Direct file editing in the repository viewer using AceEditor
- File attachment for issues
- Atom feed of user activity
- Fix some bugs

### 1.12 - 29 Mar 2014
- SSH repository access is available
- Allow users can create and management their groups
- Git submodule support
- Close issues via commit messages
- Show repository description below the name on repository page
- Fix presentation of the source viewer
- Upgrade to sbt 0.13
- Fix some bugs

### 1.11.1 - 06 Mar 2014
- Bug fix

### 1.11 - 01 Mar 2014
- Base URL for redirection, notification and repository URL box is configurable
- Remove ```--https``` option because it's possible to substitute in the base url
- Headline anchor is available for Markdown contents such as Wiki page
- Improve H2 connectivity
- Label is available for pull requests not only issues
- Delete branch button is added
- Repository icons are updated
- Select lines of source code by URL hash like `#L10` or `#L10-L15` in repository viewer
- Display reference to issue from others in comment list
- Fix some bugs

### 1.10 - 01 Feb 2014
- Rename repository
- Transfer repository owner
- Change default data directory to `HOME/.gitbucket` from `HOME/gitbucket` to avoid problem like #243, but if data directory already exist at HOME/gitbucket, it continues being used.
- Add LDAP display name attribute
- Response performance improvement
- Fix some bugs

### 1.9 - 28 Dec 2013
- Display GITBUCKET_HOME on the system settings page
- Fix some bugs

### 1.8 - 30 Nov 2013
- Add user and group deletion
- Improve pull request performance
- Pull request synchronization (when source repository is updated after pull request, it's applied to the pull request)
- LDAP StartTLS support
- Enable hard wrapping in Markdown
- Add new some options to specify the data directory. See details in [Wiki](https://github.com/takezoe/gitbucket/wiki/DirectoryStructure).
- Fix some bugs

### 1.7 - 26 Oct 2013
- Support working on Java6 in embedded Jetty mode
- Add `--host` option to bind specified host name in embedded Jetty mode
- Add `--https=true` option to force https scheme when using embedded Jetty mode at the back of https proxy
- Add full name as user property
- Change link color for absent Wiki pages
- Add ZIP download button to the repository viewer tab
- Improve ZIP exporting performance
- Expand issue and comment textarea for long text automatically
- Add conflict detection in Wiki
- Add reverting wiki page from history
- Match committer to user name by email address
- Mail notification sender is customizable
- Add link to changeset in refs comment for issues
- Fix some bugs

### 1.6 - 1 Oct 2013
- Web hook
- Performance improvement for pull request
- Executable war file
- Specify suitable Content-Type for downloaded files in the repository viewer
- Fix some bugs

### 1.5 - 4 Sep 2013
- Fork and pull request
- LDAP authentication
- Mail notification
- Add an option to turn off the gravatar support
- Add the branch tab in the repository viewer
- Encoding auto detection for the file content in the repository viewer
- Add favicon, header logo and icons for the timeline
- Specify data directory via environment variable GITBUCKET_HOME
- Fix some bugs

### 1.4 - 31 Jul 2013
- Group management
- Repository search for code and issues
- Display user related issues on the dashboard
- Display participants avatar of issues on the issue page
- Performance improvement for repository viewer
- Alert by milestone due date
- H2 database administration console
- Fix some bugs

### 1.3 - 18 Jul 2013
- Batch updating for issues
- Display assigned user on issue list
- User icon and Gravatar support
- Convert @xxxx to link to the account page
- Add copy to clipboard button for git clone URL
- Allow multi-byte characters as wiki page name
- Allow to create the empty repository
- Fix some bugs

### 1.2 - 09 Jul 2013
- Add activity timeline
- Bugfix for Git 1.8.1.5 or later
- Allow multi-byte characters as label
- Fix some bugs

### 1.1 - 05 Jul 2013
- Fix some bugs
- Upgrade to JGit 3.0

### 1.0 - 04 Jul 2013
- This is a first public release
