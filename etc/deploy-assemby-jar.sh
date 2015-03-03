#!/bin/sh
mvn deploy:deploy-file \
  -DgroupId=gitbucket\
  -DartifactId=gitbucket-assembly\
  -Dversion=3.0.0\
  -Dpackaging=jar\
  -Dfile=../target/scala-2.11/gitbucket-assembly-3.0.0.jar\
  -DrepositoryId=sourceforge.jp\
  -Durl=scp://shell.sourceforge.jp/home/groups/a/am/amateras/htdocs/mvn/
