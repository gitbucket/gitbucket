#!/bin/bash
version=$1
output_dir=`dirname $0`
git rm -f ${output_dir}/jetty-*.jar
for name in 'io' 'servlet' 'xml' 'continuation' 'security' 'util' 'http' 'server' 'webapp'
do
  jar_filename="jetty-${name}-${version}.jar"
  wget "http://repo1.maven.org/maven2/org/eclipse/jetty/jetty-${name}/${version}/${jar_filename}" -O ${output_dir}/${jar_filename}
done
git add ${output_dir}/*.jar
git commit
