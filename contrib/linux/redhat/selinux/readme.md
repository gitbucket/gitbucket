# Red Hat Enterprise Linux / CentOS SELinux policy module for GitBucket

One way to run GitBucket on Enterprise Linux is under Tomcat. Since EL 7.4, Tomcat is no longer unconfined.
Thus since 7.4, Enterprise Linux blocks certain operations that are required for GitBucket to work properly:

* Tomcat is not allowed to connect to SMTP ports, which is required to send email notifications.
* Tomcat is not allowed to execute files, which is required for creating repositories.
* Tomcat is not allowed to act as a server on unreserved ports, which is required for serving repositories via SSH.

To mitigate this, you can use the SELinux policy module provided as `gitbucket.te`. You can deploy the module with the
attached script, e.g.:

~~~
./sedeploy.sh gitbucket
~~~

You most likely also need to fix file contexts on your system. Assuming a new, default Tomcat installation on 7.4, you
can do so by issuing the following commands:

~~~
GITBUCKET_HOME='/usr/share/tomcat/.gitbucket'
mkdir -p ${GITBUCKET_HOME}
chown tomcat.tomcat ${GITBUCKET_HOME}
semanage fcontext -a -t tomcat_var_lib_t "${GITBUCKET_HOME}(/.*)?"
restorecon -rv ${GITBUCKET_HOME}

JAVA_CONF='/usr/share/tomcat/.java'
mkdir -p ${JAVA_CONF}
chown tomcat.tomcat ${JAVA_CONF}
semanage fcontext -a -t tomcat_cache_t "${JAVA_CONF}(/.*)?"
restorecon -rv ${JAVA_CONF}
~~~
