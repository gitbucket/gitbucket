#!/bin/sh
export GITBUCKET_VERSION=`cat ../build.sbt | grep 'val GitBucketVersion' | cut -d \" -f 2`
echo "GITBUCKET_VERSION: $GITBUCKET_VERSION"
