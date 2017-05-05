# Install & Upgrade GitBucket on Linux systemd

It provides a script for easy installation and upgrade of GitBucket using systemd.

## Requirements

This script requires at least:
* A recent Linux distribution with systemd. The following OS has been confirmed.
    * Red Hat Enterprise Linux 7.3
    * Ubuntu 16.04.2 LTS
* A Java Runtime Environment 8 or later.
* A sudoer.

## Install (and run)

1. Download the script.  
`$ curl -O https://raw.githubusercontent.com/gitbucket/gitbucket/master/contrib/linux/systemd/gitbucket-install.sh`
1. Edit the configurations of the script as you like.
1. Install the latest GitBucket and run.  
`$ bash gitbucket-install.sh`

Note: If you want to use an external database, stop the service after the installation is completed and then set it. See also https://github.com/gitbucket/gitbucket/wiki/External-database-configuration.

## Upgrade

It can be executed from the state where the service is running.

1. Upgrade GitBucket to the latest version using the configured script.  
`$ bash gitbucket-install.sh`

Note: A plugins will not be upgraded, please upgrade manually.

## Tips

1. Start the service  
`$ sudo systemctl start gitbucket.service`
1. Stop the service  
`$ sudo systemctl stop gitbucket.service`
1. View the logs  
`$ sudo journalctl -u gitbucket.service `
1. Uninstall  
`$ sudo systemctl disable gitbucket.service; sudo systemctl stop gitbucket.service; sudo rm -rf /var/lib/gitbucket /usr/share/gitbucket /etc/systemd/system/gitbucket.service; sudo userdel gitbucket`

Note: Needs change the unit name, directory path, and user name to appropriate values.
