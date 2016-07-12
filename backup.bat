@echo off


REM Add all msyggit commands in the path (bash, git,
SET PATH=C:\PortableGit\bin;%PATH%

bash backup.sh C:\Users\roy.li\.gitbucket C:\backup\gitbucket http://localhost:8080/database/backup