#!/usr/bin/python

import os
import shutil
import glob
from optparse import OptionParser

"""
remove existing rpm files
"""
def remove_rpm_files():
    cwd = os.getcwd()

    for file in glob.glob(os.path.join(cwd, "*.rpm")):
         print(file)
         os.remove(file)

"""
move created rpm files by rpmbuild to current directory
"""
def move_rpm_files(src_dir, dst_dir):
    for file in glob.glob(os.path.join(src_dir, "*.rpm")):
         print("moving " + file + " to " + dst_dir)
         shutil.move(file, dst_dir)

"""
get a subdirectory of rpmbuild
"""
def get_rpm_directory(dir):
    home = os.environ["HOME"]
    return os.path.join(home, "rpmbuild", dir)

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
    URL = "https://github.com/gitbucket/gitbucket/releases/download/" + version + "/gitbucket.war"

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
            f.write(chunk)

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
main function
"""
def main():
    parser = OptionParser()
    parser.add_option("--version", dest="version",
                      help="specify gitbucket version")
    parser.add_option("--port",
                      action="store", dest="port", default=8080,
                      help="specify port number")

    (options, args) = parser.parse_args()
    
    if options.version == None:
         parser.error("--version must be specified")

    # remove existing rpm files
    remove_rpm_files()

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

    cmd = "rpmbuild -ba " + dst_gitbucket_spec
    print cmd
    proc = Popen( cmd, shell=True )
    proc.wait()

    # remove gitbucket.war
    if os.path.exists(gitbucket_war_path):
        os.remove(gitbucket_war_path)

    # move rpm files to cureent directory
    src_rpm_directory = os.path.join(RPMS, "noarch")
    cwd = os.getcwd()
    move_rpm_files(src_rpm_directory, cwd)

if __name__ == "__main__":
    main()
