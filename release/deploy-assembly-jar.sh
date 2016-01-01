#!/bin/sh
. ./env.sh

cd ../
./sbt.sh clean assembly

cd release

if [[ "$GITBUCKET_VERSION" =~ -SNAPSHOT$ ]]; then
  MVN_DEPLOY_PATH=mvn-snapshot
else
  MVN_DEPLOY_PATH=mvn
fi

echo $MVN_DEPLOY_PATH

mvn deploy:deploy-file \
  -DgroupId=gitbucket\
  -DartifactId=gitbucket-assembly\
  -Dversion=$GITBUCKET_VERSION\
  -Dpackaging=jar\
  -Dfile=../target/scala-2.11/gitbucket-assembly-$GITBUCKET_VERSION.jar\
  -DrepositoryId=sourceforge.jp\
  -Durl=scp://shell.sourceforge.jp/home/groups/a/am/amateras/htdocs/$MVN_DEPLOY_PATH/
