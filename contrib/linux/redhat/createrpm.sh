#!/bin/sh

if [ $# -ne 1 ]; then
  echo "usage: $0 version"
  echo "example: $0 4.15.0"
  exit 1
fi
export VERSION=$1

export originaldir=`pwd`
rm -f  *.rpm
rm -rf ~/rpmbuild
mkdir -p ~/rpmbuild/{BUILD,RPMS,SOURCES,SPECS,SRPMS}

export currentdir=$(cd $(dirname $0); pwd)
cd $currentdir
rm   -f gitbucket.war
wget https://github.com/gitbucket/gitbucket/releases/download/$VERSION/gitbucket.war

cp gitbucket.war         ~/rpmbuild/SOURCES/
cp gitbucket.init        ~/rpmbuild/SOURCES/
sed "s/GITBUCKET_VERSION=\(.*\)/GITBUCKET_VERSION=$VERSION/" ../../gitbucket.conf > ~/rpmbuild/SOURCES/gitbucket.conf
sed "s/Version:\(\s\+\).*/Version:\1$VERSION/" gitbucket.spec > ~/rpmbuild/SPECS/gitbucket.spec

cd ~
rpmbuild -ba rpmbuild/SPECS/gitbucket.spec
echo cp      rpmbuild/RPMS/noarch/gitbucket*.rpm $originaldir
cp           rpmbuild/RPMS/noarch/gitbucket*.rpm $originaldir



