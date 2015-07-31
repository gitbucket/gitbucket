# Gitbucket backup

The following page describes an example of a possible [backup script](backup.sh) for your gitbucket installation.

Feel free to inspire from it.

## What it does?

The backup of a gitbucket installation should be consistent between the database state and the state of the git repositories.

Of course the most important to keep is probably the git repositories, but hey if you need sometime to recover it would be cool that all PRs, issues, references and so on are in synch with the backups of the repositories no?

The provided [backup script](backup.sh) tries to minimise as much as possible the time between the database backup and a clean stage of all repositories by doing the following steps:

- clone all repositories into a backup folder (creating only non already existing ones)
- update all repositories
- make a database backup
- update again all repositories

## Using backup.sh

How to use the provided [backup script](backup.sh)?

`bash backup.sh [-v] GITBUCKET_HOME BACKUP_FOLDER [Database backup URL]`

where

- -v: stands for verbose, when used the script will output more about what it is doing. Optional
- GITBUCKET_HOME: is the full path to your gitbucket data folder. By default it might be `~/.gitbucket`
- BACKUP_FOLDER: is the full path to the folder into which you would like the backup to be done
- Database backup URL: it is the full URL that forces gitbucket isntallation to perform a database export (see [PR-845](https://github.com/takezoe/gitbucket/pull/845)). This parameter is optional ; thus if it is ommited then no database export will be done.

## Using from Windows

I tested this script on windows using a msysgit installation (one copy of one coming bundled with [Sourcetree git client](https://www.sourcetreeapp.com/)).

Here is a `backup.bat` file that could be launched on the server hosting gitbucket
```
@echo off

REM Go to script directory
CD /D %~dp0

REM Add all msyggit commands in the path (bash, git, 
SET PATH=D:\tools\portable-git-1.9.5\bin;%PATH%

bash backup.sh d:\gitbucket\work e:\backup\gitbucket http://localhost:8080/database/backup

```
