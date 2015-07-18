#!/bin/sh
export GITBUCKET_VERSION=`cat project/build.scala | grep 'val Version' | cut -d \" -f 2`
echo "GITBUCKET_VERSION: $GITBUCKET_VERSION"
