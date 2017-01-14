set SCRIPT_DIR=%~dp0
java %JAVA_OPTS% -Dsbt.log.noformat=true -XX:+CMSClassUnloadingEnabled -Xmx512M -Xss2M -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005 -jar "%SCRIPT_DIR%\sbt-launch-0.13.12.jar" %*
