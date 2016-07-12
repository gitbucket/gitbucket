#!/bin/bash

DEBUG=0
if [ "$1" = "-v" ]
then
    DEBUG=1
    shift
fi

GITBUCKET_DATA=$1
BACKUP_FOLDER=$2
GITBUCKET_DB_BACKUP_URL=$3

gitbucketRepositoriesFolder=$GITBUCKET_DATA/repositories
repositoriesFolderNameSize=${#gitbucketRepositoriesFolder}

repositoriesBackupFolder=$BACKUP_FOLDER/repositories

##
## trace allows to print messages on the console if -v argument (verbose) has been given to the program
## 
function trace {
    if [ "$DEBUG" = "1" ]
    then
        echo "$@"
    fi
}

##
## create a git mirror clone of a repository. If the clone already exists, the operation is skipped
##    arg1: source of the repository to be cloned
##    arg2: full folder name of the clone
## 
function createClone {
    source=$1
    dest=$2

    if [ ! -d $dest ]
    then
        trace "  cloning $source into $dest"
        git clone --mirror $source $dest > /dev/null 2>&1
    else
        trace "  $dest already exists, skipping git clone operation"
    fi
}

##
## update a clone folder, the update is down toward the latests state of it's default remote
##
function updateRepository {
    currentFolder=$(pwd)
    cd $1
    trace "  updating $1"
    git remote update > /dev/null
    cd $currentFolder
}

# Perform some initializations
if [ ! -d $repositoriesBackupFolder ] 
then
    mkdir -p $repositoriesBackupFolder > /dev/null
fi


#
# To keep integrity as its maximum possible, the database export and git backups must be done in the shortest possible timeslot.
# Thus we will:
#   - clone new repositories into backup directory
#   - update them all a first time, so that we gather gib updates
#   - export the database
#   - update all repositories a second time, this time it should be ultra-fast
#

# First let's be sure that all existing directories have a clone
# as clone operation can be heavy let's do it now
repositories=$(find $gitbucketRepositoriesFolder -name *.git -print)

echo "Starting clone process"
for fullRepositoryFolderPath in $repositories
do
    repositoryFolder=${fullRepositoryFolderPath:$repositoriesFolderNameSize}
    mirrorFolder=$repositoriesBackupFolder$repositoryFolder

    createClone $fullRepositoryFolderPath $mirrorFolder
done;
echo "All repositories, cloned"

echo "Update repositories: phase 1"
#
# Then let's update all our clones
# 
for fullRepositoryFolderPath in $repositories
do
    repositoryFolder=${fullRepositoryFolderPath:$repositoriesFolderNameSize}
    mirrorFolder=$repositoriesBackupFolder$repositoryFolder

    updateRepository $mirrorFolder
done;
echo "Update repositories: phase 1, terminated"

#
# Export the database
# 
if [ "$GITBUCKET_DB_BACKUP_URL" != "" ]
then
    echo "Database backup"
    curl $GITBUCKET_DB_BACKUP_URL > /dev/null
    cp -f $GITBUCKET_DATA/gitbucket-database-backup.zip $BACKUP_FOLDER > /dev/null
else
    echo "No database URL provided, skipping database backup"
fi

#
# Export the GitBucket configuration
# 
echo "Configuration backup"
cp $GITBUCKET_DATA/gitbucket.conf $BACKUP_FOLDER > /dev/null

#
# Export the GitBucket data directory (avatars, ...)
# 
echo "Avatars backup"
tar -cf $BACKUP_FOLDER/data.tar $GITBUCKET_DATA/data > /dev/null 2>&1
gzip -f $BACKUP_FOLDER/data.tar > /dev/null

#
# Then let's do a final update
# 
echo "Update repositories: phase 2"
for fullRepositoryFolderPath in $repositories
do
    repositoryFolder=${fullRepositoryFolderPath:$repositoriesFolderNameSize}
    mirrorFolder=$repositoriesBackupFolder$repositoryFolder

    updateRepository $mirrorFolder
done;
echo "Update repositories: phase 2, terminated"

echo "Update process ended, backup available under: $BACKUP_FOLDER"