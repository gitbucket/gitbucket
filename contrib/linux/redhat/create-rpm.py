#!/usr/bin/python

import os
import sys
import shutil
import glob
from optparse import OptionParser

"""
move rpm files created by rpmbuild to current directory
"""
def move_rpm_files(src_dir, dst_dir):
    for file in glob.glob(os.path.join(src_dir, "*.rpm")):
         base      = os.path.basename(file)
         dst_file  = os.path.join(dst_dir, base)

         print("moving " + file + " to " + dst_file)
         if os.path.exists(dst_file):
             os.remove(dst_file)
         shutil.move(file, dst_file)

"""
get a subdirectory of rpmbuild
"""
def get_rpm_directory(dir):
    cwd = os.getcwd()
    if len(dir) > 0:
        return os.path.join(cwd, "rpmbuild", dir)
    else:
        return os.path.join(cwd, "rpmbuild")
        

"""
remove existing rpmbuild directory and recreate it
"""
def create_rpmbuild_directory():
    subdirs = ["BUILD","RPMS","SOURCES","SPECS","SRPMS"]
    
    rpmbuild = get_rpm_directory("")
    
    # remove rpmbuild
    if os.path.exists(rpmbuild):
        print "removing " + rpmbuild
        shutil.rmtree(rpmbuild)

    for subdir in subdirs:
        dir = os.path.join(rpmbuild, subdir)
        print("creating: " + dir)
        os.makedirs(dir)

"""
get the directory where this script in
"""
def get_script_directory():
    script_dir = os.path.dirname(os.path.realpath(__file__))
    return script_dir

"""
download gitbucket.war from github.com
"""
def get_gitbucket_war_file(filepath, version):
    import urllib2
    URL = '/'.join(["https://github.com/gitbucket/gitbucket/releases/download", version, "gitbucket.war"])

    # remove gitbucket.war
    if os.path.exists(filepath):
        os.remove(filepath)

    CHUNK = 512 * 1024
    result = urllib2.urlopen(URL)
    length = result.headers['content-length']

    print "downloading from " + URL
    total = 0
    with open(filepath, 'wb') as f:
        while True:
            chunk = result.read(CHUNK)
            if not chunk:
                break
            total += len(chunk)
            percent = 100 * total / int(length)
            print "downloading " + str(total) + " bytes" + " / " + str(length) + " bytes" + " (" + str(percent) + " %" + ")"
            sys.stdout.flush()
            f.write(chunk)

"""
enum releases at github.com and returns them as a list
"""
def enum_release(owner, repos):
    import urllib2
    import re

    # https://api.github.com/repos/<owner>/<repos>/releases
    URL = '/'.join(["https://api.github.com/repos", owner, repos, "releases"])

    result = urllib2.urlopen(URL)

    # "tag_name":"x.xx.x"
    re_release = re.compile(r'"tag_name":"(?P<version>.+?)",')

    content = ""
    for data in result:
        content += data

    releases = []
    iterator = re_release.finditer(content)
    for match in iterator:
        group = match.group('version')
        releases.append(group)

    return releases

"""
open bitbucket.conf and replace the settings based on the command line options
"""
def process_gitbucket_conf(orgfile, newfile, options):
    import re

    fin = open(orgfile, 'r')
    fout = open(newfile, 'w')
    
    re_version = re.compile("GITBUCKET_VERSION=(.*)")
    re_port    = re.compile("GITBUCKET_PORT=(.*)")

    for data in fin:
        if re_version.match(data):
            fout.write("GITBUCKET_VERSION=" + options.version)
        elif re_port.match(data):
            fout.write("GITBUCKET_PORT=" + str(options.port))
        else:
            fout.write(data)
        fout.write("\n")
    
    fin.close()
    fout.close()

"""
open bitbucket.spec and replace the settings based on the command line options
"""
def process_gitbucket_spec(orgfile, newfile, options):
    import re

    fin = open(orgfile, 'r')
    fout = open(newfile, 'w')

    re_version = re.compile("Version:(.*)")

    for data in fin:
        if re_version.match(data):
            fout.write("Version:      " + options.version)
        else:
            fout.write(data)
        fout.write("\n")
    
    fin.close()
    fout.close()

"""
create rpm
"""
def create_rpm(options):

    # remove and create directories for rpmbuild
    create_rpmbuild_directory()
    
    gitbucket_war_path = "gitbucket.war"

    # get gitbucket.war from github.com
    get_gitbucket_war_file(gitbucket_war_path, options.version)

    SOURCES = get_rpm_directory("SOURCES")
    SPECS   = get_rpm_directory("SPECS")
    RPMS    = get_rpm_directory("RPMS")

    # copy gitbucket.war to SOURCES in rpmbuild
    shutil.copy(gitbucket_war_path, SOURCES)

    # get script directory
    scriptdir = get_script_directory()
    
    # copy gitbucket.init
    src_gitbucket_init = os.path.join(scriptdir, "gitbucket.init")
    shutil.copy(src_gitbucket_init, SOURCES)
    
    # process gitbucket.conf
    src_gitbucket_conf = os.path.join(scriptdir, "../..", "gitbucket.conf")
    dst_gitbucket_conf = os.path.join(SOURCES, "gitbucket.conf")
    process_gitbucket_conf(src_gitbucket_conf, dst_gitbucket_conf, options)

    # process gitbucket.spec
    src_gitbucket_spec = os.path.join(scriptdir, "gitbucket.spec")
    dst_gitbucket_spec = os.path.join(SPECS, "gitbucket.spec")
    process_gitbucket_spec(src_gitbucket_spec, dst_gitbucket_spec, options)

    # call rpmbuild
    import subprocess
    from subprocess import Popen

    cmd = "rpmbuild -ba " + dst_gitbucket_spec + " --define '_topdir " + get_rpm_directory("") + "'"
    print cmd
    sys.stdout.flush()

    proc = Popen( cmd, shell=True )
    proc.wait()

    # remove gitbucket.war
    if os.path.exists(gitbucket_war_path):
        os.remove(gitbucket_war_path)

    # move rpm files to cureent directory
    src_rpm_directory = os.path.join(RPMS, "noarch")
    cwd = os.getcwd()
    move_rpm_files(src_rpm_directory, cwd)

    # remove rpmbuild directory
    rpmbuild = get_rpm_directory("")
    if os.path.exists(rpmbuild):
        print "removing " + rpmbuild
        shutil.rmtree(rpmbuild)

"""
enum releases for gitbucket
"""
def enum_gitbucket_release():
    releases = enum_release('gitbucket', 'gitbucket')
    return releases

def is_rpmbuild_available():
    import distutils.spawn

    if distutils.spawn.find_executable('rpmbuild') != None:
       return True
    else:
       return False

"""
main function
"""
def main():
    parser = OptionParser()
    parser.add_option("--version", dest="version",
                      help="specify gitbucket version")
    parser.add_option("--port",
                      action="store", dest="port", default=8080,
                      help="specify port number")
    parser.add_option("--command",
                      action="store", dest="command",
                      help="list-version or create")

    if len(sys.argv) == 1:
        parser.print_help()
        sys.exit(1)

    (options, args) = parser.parse_args()

    if options.command == None:
         parser.error("--command must be specified")

    if options.command == "create":
        releases = enum_gitbucket_release()
        if len(releases) == 0:
            print "no available version"
            sys.exit(1)

        if is_rpmbuild_available() != True:
            print "you need to install rpmbuild in advance with 'dnf install rpm-build'"
            sys.exit(1)

        if options.version == None:
           options.version = releases[0]
        elif options.version not in releases:
            print "not valid version: " + options.version
            print "use '--command list-version' to show available versions"
            sys.exit(1)
        create_rpm(options)
    elif options.command == "list-version":
        releases = enum_gitbucket_release()
        for release in releases:
            print release
    else:
        print "command must be create or list-version"

if __name__ == "__main__":
    main()

