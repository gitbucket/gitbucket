# Changelog
All changes to the project will be documented in this file.

### 4.33.0 - 31 Dec 2019

- All CLI options are configurable by environment variables
- Folding pull request files
- WebHook security options
- Add assignee and assignees properties to some Web APIs' response

### 4.32.0 - 7 Aug 2019

- Bump to Scala 2.13.0 and Scalatra 2.7.0
- Draft pull request
- Drop network installation of plugins
- Compare view works for commit id
- Apply default priority to pull requests
- Focus title after clicking issue / pull request edit button

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

### 4.30.1 - 22 Dec 2018
- Bug fix for several WebHooks and Web API

## 4.30.0 - 15 Dec 2018
- Automatic ChangeLog Summary generation for new Releases
- A lot of GitBucket Web API updates to increase compatibility with the GitHub API.
- Display of checkboxes in Markdown files in Git repositories
- A new extension point for plugins: anonymousAccessiblePaths
- Group support in the Gist Plugin
- Allow redirection to the Release Page from the Activity Timeline Page

## 4.29.0 - 29 Sep 2018
- Official Docker image has been available
- Enhance file edit and delete buttons of the repository viewer
- Fix Patch button to generate patches for all files in the commit
- Display confirmation dialog for Transfer Ownership and Garbage collection
- Fix wrong url encoding in "Compare & pull request"

## 4.28.0 - 1 Sep 2018
- Proxy support for plugin installation
- Fix some bugs around pull requests

## 4.27.0 - 29 Jul 2018
- Create new tag on the browser
- EditorConfig support
- Improve issues / pull requests search
- Some improvements and bug fixes for plugin installation via internet and pull request commenting

## 4.26.0 - 30 Jun 2018
- Installing plugins from the central registry
- Repositories tab in the dashboard
- Fork dialog enhancement
- Adjust pull request creation suggestor
- Keep showing incompleted task list
- New notification hooks

## 4.25.0 - 29 May 2018
- Security improvements
- Show mail address at the profile page
- Task list on commit comments
- More detailed editing history of issues and pull requests
- Expose user public keys
- Download repository improvements

## 4.24.1 - 1 May 2018
- Fix bug in Web API authentication

## 4.24.0 - 30 Apr 2018
- Diff for each review comment on pull requests
- Extra mail addresses support
- Show tags at the commit list
- Keep wrap mode of the online editor
- Renew layout of gitbucket-gist-plugin
- Web API of gitbucket-ci-plugin

## 4.23.1 - 10 Apr 2018
- Fix bug that the contents API doesn't work for the repository root
- Fix shutdown problem in Tomcat deployment
- Render by plugins at the blob view even if it's a binary file

## 4.23.0 - 31 Mar 2018
- Allow tail slash in URL
- Display commit message of tags at the releases page
- Add labels property to issues and pull requests API response
- Plugins list API
- Git authentication with personal access token
- Max parallel builds and max stored history in CI plugin became configurable

## 4.22.0 - 3 Mar 2018
- Pull request merge strategy settings
- Create repository with an empty commit
- Improve database viewer
- Update maven-repository-plugin

## 4.21.2 - 27 Jan 2018
- Bugfix

## 4.21.1 - 27 Jan 2018
- Bugfix

## 4.21.0 - 27 Jan 2018
- Release page
- OpenID Connect support
- New database viewer
- Submodule links to web page
- Clarify close/reopen button

# 4.20.0 - 23 Dec 2017
- Squash and rebase merge strategy for pull requests
- Quick pull request creation
- Download patch from the diff view
- Fork and create repository are proceeded asynchronously
- Create new repository by copying existing git repository
- Hide overflowed repository names in the sidebar
- Support CreateEvent web hook
- Display conflicting files if pull request can't be merged

## 4.19.3 - 7 Dec 2017
- Fix file uploading bug
- Fix reply comment form behavior in the diff view

## 4.19.2 - 3 Dec 2017
- Fix routing bug in `CompositeScalatraFilter`
- Resolve id attribute collision in the web hook editing form

## 4.19.1 - 2 Dec 2017
- Update gitbucket-notifications-plugin because it had a version compatibility issue

## 4.19.0 - 2 Dec 2017
- [gitbucket-maven-repository-plugin](https://github.com/takezoe/gitbucket-maven-repository-plugin) is available
- Upgrade to Scalatra 2.6
- Improve layout of the system settings page
- New extension point (`sshCommandProvider`)
- Dropped [gitbucket-pages-plugin](https://github.com/gitbucket/gitbucket-pages-plugin) from bundled plugins temporary because we couldn't complete update for Scalatra 2.6 before this release.

## 4.18.0 - 14 Oct 2017
- Form to reply to review comment
- Display fullname in username suggestion
- Commit hook plugins are applied to online editing
- Improve gitbucket-ci-plugin

## 4.17.0 - 30 Sep 2017
- [gitbucket-ci-plugin](https://github.com/takezoe/gitbucket-ci-plugin) is available
- Transferring to URL with commit ID
- Drop uploadable file type limitation
- Improve Mailer API
- Web API and webhook enhancement

## 4.16.0 - 2 Sep 2017
- Support AdminLTE color skin
- Improve unexpected error handling
- Show commit status on the commits list

## 4.15.0 - 5 Aug 2017
- Bundle GitBucket organization plugins
- Notifications plugin
- Plugin hot deployment
- Update Slick to 3.2.1 from 3.2.0
- Support ed25519 keys for SSH
- Markdown preview in comment editing forms

## 4.14.1 - 4 Jul 2017
- Bug fix: Possibility of error in forking repository

## 4.14 - 1 Jul 2017
- Support priority in issues and pull requests
- Show icons when the sidebar is collapsed
- Support gollum events in web hook
- Support account (user / group) level web hook
- Add `--max_file_size` option
- Configuration by system property or environment variable

## 4.13 - 29 May 2017
- Uploading files into the repository
- HTML is available in Markdown
- Added filter box to dropdown menus

## 4.12 - 30 Apr 2017
- [Gist plug-in](https://github.com/gitbucket/gitbucket-gist-plugin) provides JavaScript to embed snippet
- Dropdown menu filter in the branch comparing page
- Caution for the embedded H2 database

## 4.11 - 1 Apr 2017
- Deploy keys support
- Auto generate avatar images
- Collaborators of the private forked repository are copied from the original repository
- Cache avatar images in the browser
- New extension point to receive events about repository

## 4.10 - 25 Feb 2017
- Update to Scala 2.12, Scalatra 2.5 and Slick 3.2
- Display file size in the file viewer

## 4.9 - 29 Jan 2017
- GitLFS support
- Template for issues and pull requests
- Manual label color editing
- Account description
- `--tmp-dir` option for standalone mode
- More APIs for issues
  - [List issues for a repository](https://developer.github.com/v3/issues/#list-issues-for-a-repository)
  - [Create an issue](https://developer.github.com/v3/issues/#create-an-issue)

## 4.8 - 23 Dec 2016
- Search for repository names from the global header
- Filter repositories on the sidebar of the dashboard
- Search issues and wiki
- Keep pull request comments after new commits are pushed
- New web API to get a single issue
- Performance improvement for the repository viewer

## 4.7.1 - 28 Nov 2016
- Bug fix: group repositories are not shown in the your repositories list on the sidebar
- Small performance improvement of the dashboard

## 4.7 - 26 Nov 2016
- New permission system
- Dropdown filter for issue labels, milestones and assignees
- Keep sidebar folding status
- Link from milestone label to the issue list

## 4.6 - 29 Oct 2016
- Add disable option for forking
- Add History button to wiki page
- Git repository URL redirection for GitHub compatibility
- Get-Content API improvement
- Indicate who is group master in Members tab in group view

## 4.5 - 29 Sep 2016
- Attach files by dropping into textarea
- Issues / Pull requests switcher in dashboard
- HikariCP could be configured in `GITBUCKET_HOME/database.conf`
- Improve Cookie security
- Display commit count on the history button
- Improve mobile view

## 4.4 - 28 Aug 2016
- Import a SQL dump file to the database
- `go get` support in private repositories
- Sort milestones by due date
- apache-sshd has been updated to 1.2.0

## 4.3 - 30 Jul 2016
- Emoji support by [gitbucket-emoji-plugin](https://github.com/gitbucket/gitbucket-emoji-plugin)
- User name suggestion
- Add new web APIs and basic authentication support for API access
- Root Endpoint
  - [List endpoints](https://developer.github.com/v3/#root-endpoint)
  - [List Branches](https://developer.github.com/v3/repos/branches/#list-branches)
  - [Get contents](https://developer.github.com/v3/repos/contents/#get-contents)
  - [Get a Reference](https://developer.github.com/v3/git/refs/#get-a-reference)
  - [List Collaborators](https://developer.github.com/v3/repos/collaborators/#list-collaborators)
  - [List user repositories](https://developer.github.com/v3/repos/#list-user-repositories)
  - [Get a group](https://developer.github.com/v3/orgs/#get-an-organization)
  - [List group repositories](https://developer.github.com/v3/repos/#list-organization-repositories)
- Add new extension points
  - `assetsMapping` : Supplies resources in plugin classpath as web assets
  - `suggestionProvider` : Provides suggestion in the Markdown editing textarea
  - `textDecorator` : Decorate text nodes in HTML which is converted from Markdown

## 4.2.1 - 3 Jul 2016
- Fix migration bug

This is hotfix for a critical bug in migration. If you are new installation, use 4.2.0. But if you have an exisiting installation and it had been updated to 4.0 from 3.x, you must update to 4.2.1.

## 4.2 - 2 Jul 2016
- New UI based on [AdminLTE](https://github.com/almasaeed2010/AdminLTE)
- git gc
- Issues and Wiki have been possible to be disabled
- SMTP configuration test mail

## 4.1 - 4 Jun 2016
- Generic ssh user
- Improve branch protection UI
- Default value of pull request title

## 4.0 - 30 Apr 2016
- MySQL and PostgreSQL support
- Data export and import
- Migration system has been switched to [solidbase](https://github.com/gitbucket/solidbase)

**Note:** You can upgrade to GitBucket 4.0 from 3.14. If your GitBucket is 3.13 or before, you have to upgrade 3.14 at first.

## 3.14 - 30 Apr 2016
- File attachment and search for wiki pages
- New extension points to add menus
- Content-Type of webhooks has been choosable

## 3.13 - 1 Apr 2016
- Refresh user interface for wide screen
- Add `pull_request` key in list issues API for pull requests
- Add `X-Hub-Signature` security to webhooks
- Provide SHA-256 checksum for `gitbucket.war`

## 3.12 - 27 Feb 2016
- New GitHub UI
- Improve mobile view
- Improve printing style
- Individual URL for pull request tabs
- SSH host configuration is separated from HTTP base URL

## 3.11 - 30 Jan 2016
- Upgrade Scalatra to 2.4
- Sidebar and Footer for Wiki
- Branch protection and receive hook extension point for plug-in
- Limit recent updated repositories list
- Issue actions look-alike GitHub
- Web API for labels
- Requires Java 8

## 3.10 - 30 Dec 2015
- Move to Bootstrap3
- New URL for raw contents (`raw/master/doc/activity.md` instead of `blob/master/doc/activity.md?raw=true`)
- Update xsbt-web-plugin
- Update H2 database

## 3.9 - 5 Dec 2015
- GFM inline breaks support in Markdown
- WebHook on create review comment is available
- WebHook event trigger is selectable

## 3.8 - 31 Oct 2015
- Moved to GitHub organization
- Omit diff view for large differences
- Repository creation API
- Render url as link in repository description
- Expand attachable file types

## 3.7 - 3 Oct 2015
- Markdown processor has been switched to [markedj](https://github.com/gitbucket/markedj) from pegdown
- Clone in desktop button
- Providing MD5 and SHA-1 checksum for `gitbucket.war` has started

## 3.6 - 30 Aug 2015
- User interface Improvements: Especially, commit list, issues and pull request have been updated largely.
- Installed plugins list has been available at the system administration console.
- Pages and repository list in the sidebar have been limited and more pages and repositories link is available.
- More reference link notation in Markdown has been supported.

## 3.5 - 1 Aug 2015
- Octicons has been applied
- Global header has been enhanced. Now it's further similar to GitHub.
- Default compare / pull request target has been changed to the parent repository
- A lot of updates for [gitbucket-gist-plugin](https://github.com/gitbucket/gitbucket-gist-plugin)

## 3.4 - 27 Jun 2015
- Declarative style plug-in definition
- New extension point to add markup render
- go-import support

## 3.3 - 31 May 2015
- Rich graphical diff for images
- File finder is available in the repository viewer
- Blame is displayed at the source viewer
- Remain user data and repositories even if user is disabled
- Mobile view improvement

## 3.2 - 3 May 2015
- Directory history button
- Compare / pull request button
- Limit of activity log

## 3.1.1 - 4 Apr 2015
- Rolled back H2 version to avoid version compatibility issue
- Plug-ins became possible to access ServletContext

## 3.1 - 28 Mar 2015
- Web APIs for Jenkins github pull-request builder
- Improved diff view
- Bump Scalatra to 2.3.1, sbt to 0.13.8

## 3.0 - 3 Mar 2015
- New plug-in system is available
- Connection pooling by c3p0
- New branch UI
- Compare between specified commit ids

## 2.8 - 1 Feb 2015
- New logo and icons
- New system setting options to control visibility
- Comment on side-by-side diff
- Information message on sign-in page
- Fork repository by group account

## 2.7 - 29 Dec 2014
- Comment for commit and diff
- Fix security issue in markdown rendering
- Some bug fix and improvements

## 2.6 - 24 Nov 2014
- Search box at issues and pull requests
- Information from administrator
- Pull request UI has been updated
- Move to TravisCI from Buildhive
- Some bug fix and improvements

## 2.5 - 4 Nov 2014
- New Dashboard
- Change datetime format
- Create branch from Web UI
- Task list in Markdown
- Some bug fix and improvements

## 2.4.1 - 6 Oct 2014
- Bug fix

## 2.4 - 6 Oct 2014
- New UI is applied to Issues and Pull requests
- Side-by-side diff is available
- Fix relative path problem in Markdown links and images
- Plugin System is disabled in default
- Some bug fix and improvements

## 2.3 - 1 Sep 2014
- Scala based plugin system
- Embedded Jetty war extraction directory moved to `GITBUCKET_HOME/tmp`
- Some bug fix and improvements

## 2.2.1 - 5 Aug 2014
- Bug fix

## 2.2 - 4 Aug 2014
- Plug-in system is available
- Move to Scala 2.11, Scalatra 2.3 and Slick 2.1
- tar.gz export for repository contents
- LDAP authentication improvement (mail address became optional)
- Show news feed of a private repository to members
- Some bug fix and improvements

## 2.1 - 6 Jul 2014
- Upgrade to Slick 2.0 from 1.9
- Base part of the plug-in system is merged
- Many bug fix and improvements

## 2.0 - 31 May 2014
- Modern Github UI
- Preview in AceEditor
- Select lines by clicking line number in blob view

## 1.13 - 29 Apr 2014
- Direct file editing in the repository viewer using AceEditor
- File attachment for issues
- Atom feed of user activity
- Fix some bugs

## 1.12 - 29 Mar 2014
- SSH repository access is available
- Allow users can create and management their groups
- Git submodule support
- Close issues via commit messages
- Show repository description below the name on repository page
- Fix presentation of the source viewer
- Upgrade to sbt 0.13
- Fix some bugs

## 1.11.1 - 06 Mar 2014
- Bug fix

## 1.11 - 01 Mar 2014
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

## 1.10 - 01 Feb 2014
- Rename repository
- Transfer repository owner
- Change default data directory to `HOME/.gitbucket` from `HOME/gitbucket` to avoid problem like #243, but if data directory already exist at HOME/gitbucket, it continues being used.
- Add LDAP display name attribute
- Response performance improvement
- Fix some bugs

## 1.9 - 28 Dec 2013
- Display GITBUCKET_HOME on the system settings page
- Fix some bugs

## 1.8 - 30 Nov 2013
- Add user and group deletion
- Improve pull request performance
- Pull request synchronization (when source repository is updated after pull request, it's applied to the pull request)
- LDAP StartTLS support
- Enable hard wrapping in Markdown
- Add new some options to specify the data directory. See details in [Wiki](https://github.com/takezoe/gitbucket/wiki/DirectoryStructure).
- Fix some bugs

## 1.7 - 26 Oct 2013
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

## 1.6 - 1 Oct 2013
- Web hook
- Performance improvement for pull request
- Executable war file
- Specify suitable Content-Type for downloaded files in the repository viewer
- Fix some bugs

## 1.5 - 4 Sep 2013
- Fork and pull request
- LDAP authentication
- Mail notification
- Add an option to turn off the gravatar support
- Add the branch tab in the repository viewer
- Encoding auto detection for the file content in the repository viewer
- Add favicon, header logo and icons for the timeline
- Specify data directory via environment variable GITBUCKET_HOME
- Fix some bugs

## 1.4 - 31 Jul 2013
- Group management
- Repository search for code and issues
- Display user related issues on the dashboard
- Display participants avatar of issues on the issue page
- Performance improvement for repository viewer
- Alert by milestone due date
- H2 database administration console
- Fix some bugs

## 1.3 - 18 Jul 2013
- Batch updating for issues
- Display assigned user on issue list
- User icon and Gravatar support
- Convert @xxxx to link to the account page
- Add copy to clipboard button for git clone URL
- Allow multi-byte characters as wiki page name
- Allow to create the empty repository
- Fix some bugs

## 1.2 - 09 Jul 2013
- Add activity timeline
- Bugfix for Git 1.8.1.5 or later
- Allow multi-byte characters as label
- Fix some bugs

## 1.1 - 05 Jul 2013
- Fix some bugs
- Upgrade to JGit 3.0

## 1.0 - 04 Jul 2013
- This is a first public release
