Directory Structure
========
GitBucket persists all data into __HOME/.gitbucket__ in default (In 1.9 or before, HOME/gitbucket is default).

This directory has following structure:

```
* /HOME/gitbucket
  * /repositories 
    * /USER_NAME
      * / REPO_NAME.git (substance of repository. GitServlet sees this directory)
      * / REPO_NAME
        * /issues (files which are attached to issue)
      * / REPO_NAME.wiki.git (wiki repository)
  * /data
    * /USER_NAME
      * /files
        * avatar.xxx (image file of user avatar)
  * /plugins
    * /PLUGIN_NAME
      * plugin.js
  * /tmp
    * /_upload
      * /SESSION_ID (removed at session timeout)
        * current time millis + random 10 alphanumeric chars (temporary file for file uploading)
    * /USER_NAME
      * /init-REPO_NAME (used in repository creation and removed after it) ... unused since 1.8
      * /REPO_NAME.wiki (working directory for wiki repository) ... unused since 1.8
      * /REPO_NAME
         * /download (temporary directories are created under this directory)
```

There are some ways to specify the data directory instead of the default location.

1. Environment variable __GITBUCKET_HOME__
2. System property __gitbucket.home__ (e.g. ```-Dgitbucket.home=PATH_TO_DATADIR```)
3. Command line option for embedded Jetty (e.g. ```java -jar gitbucket.war --data=PATH_TO_DATADIR```)
4. Context parameter __gitbucket.home__ in web.xml like below:
```xml
<context-param>
  <param-name>gitbucket.home</param-name>
  <param-value>PATH_TO_DATADIR</param-value>
</context-param>
```
