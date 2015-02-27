#!/bin/sh
mvn deploy:deploy-file \
  -DgroupId=jp.sf.amateras\
  -DartifactId=gitbucket-assembly\
  -Dversion=0.0.1\
  -Dpackaging=jar\
  -Dfile=../target/scala-2.11/gitbucket-assembly-0.0.1.jar\
  -DrepositoryId=sourceforge.jp\
  -Durl=scp://shell.sourceforge.jp/home/groups/a/am/amateras/htdocs/mvn/