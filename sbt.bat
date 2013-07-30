set SCRIPT_DIR=%~dp0
java -Dhttp.proxyHost=proxy.intellilink.co.jp -Dhttp.proxyPort=8080 -XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=256m -Xmx512M -Xss2M -jar "%SCRIPT_DIR%\sbt-launch-0.12.3.jar" %*
