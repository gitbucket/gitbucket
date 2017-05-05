#!/bin/bash

## Configurations

# The OS user that the process is executed. If does not exist it will be created.
GITBUCKET_USER=gitbucket

# Bind host.
GITBUCKET_HOST=0.0.0.0

# Bind port.
GITBUCKET_PORT=8080

# URL prefix for the GitBucket page. (http://<host>:<port>/<prefix>/)
GITBUCKET_PREFIX=/

# Data directory, holds repositories.
GITBUCKET_HOME=/var/lib/gitbucket

# Directory where GitBucket is installed.
GITBUCKET_DIR=/usr/share/gitbucket
GITBUCKET_WAR_DIR=$GITBUCKET_DIR/lib

# systemd's unit configuration file.
GITBUCKET_SERVICE_DESC=gitbucket.service

# Java VM options
#GITBUCKET_JVM_OPTS="-Xms512m -Xmx512m -Djava.net.preferIPv4Stack=true"
GITBUCKET_JVM_OPTS=""

# Path to java command.
JAVA_CMD=/usr/bin/java

## Installation & Upgrade

if ! id -u $GITBUCKET_USER > /dev/null 2>&1; then
  sudo useradd -r -d "$GITBUCKET_HOME" -s /usr/sbin/nologin $GITBUCKET_USER
fi

if [ ! -e "$GITBUCKET_HOME" ]; then
  sudo mkdir -m 700 -p "$GITBUCKET_HOME"
  sudo chown $GITBUCKET_USER: "$GITBUCKET_HOME"
fi

sudo mkdir -p "$GITBUCKET_WAR_DIR"

if [ ! -e /etc/systemd/system/$GITBUCKET_SERVICE_DESC ]; then
  sudo tee /etc/systemd/system/$GITBUCKET_SERVICE_DESC << _EOF_ > /dev/null
[Unit]
Description=GitBucket
After=network.target

[Service]
User=$GITBUCKET_USER
ExecStart=$JAVA_CMD $GITBUCKET_JVM_OPTS -jar "$GITBUCKET_WAR_DIR/gitbucket.war" "--gitbucket.home=$GITBUCKET_HOME" --host=$GITBUCKET_HOST --port=$GITBUCKET_PORT --prefix=$GITBUCKET_PREFIX
Restart=always
PrivateTmp=true

[Install]
WantedBy=multi-user.target
_EOF_

  sudo chmod 400 /etc/systemd/system/$GITBUCKET_SERVICE_DESC
  sudo systemctl daemon-reload
  sudo systemctl enable $GITBUCKET_SERVICE_DESC
  echo "Unit $GITBUCKET_SERVICE_DESC was registered to systemd."
fi

LATEST_GITBUCKET_WAR_URL="`curl -s https://api.github.com/repos/gitbucket/gitbucket/releases/latest | sed -n 's|.*"browser_download_url" *: *"\(.*/gitbucket.war\)".*|\1|p'`"
if [ $? -ne 0 ]; then
  echo 'Failed to get latest version of GitBucket.'
  exit 1
fi
LATEST_GITBUCKET_VERSION="`echo -n "$LATEST_GITBUCKET_WAR_URL" | sed 's|.*/download/\(.*\)/.*|\1|'`"

if [ -e "$GITBUCKET_WAR_DIR/gitbucket-$LATEST_GITBUCKET_VERSION.war" ]; then
  echo 'Already using the latest GitBucket.'
  exit 1
fi

echo "Download \"$LATEST_GITBUCKET_WAR_URL\"."
sudo curl -L -o "$GITBUCKET_WAR_DIR/gitbucket-$LATEST_GITBUCKET_VERSION.war" "$LATEST_GITBUCKET_WAR_URL"
if [ $? -ne 0 ]; then
    echo 'Failed to download GitBucket WAR.'
    exit 1
fi
sudo chmod a-w "$GITBUCKET_WAR_DIR/gitbucket-$LATEST_GITBUCKET_VERSION.war"

if systemctl is-active $GITBUCKET_SERVICE_DESC > /dev/null 2>&1; then
  echo "Stop $GITBUCKET_SERVICE_DESC."
  sudo systemctl stop $GITBUCKET_SERVICE_DESC
fi

sudo ln -fns "gitbucket-$LATEST_GITBUCKET_VERSION.war" "$GITBUCKET_WAR_DIR/gitbucket.war"
echo "Current GitBucket version is $LATEST_GITBUCKET_VERSION."

echo "Start $GITBUCKET_SERVICE_DESC."
sudo systemctl start $GITBUCKET_SERVICE_DESC

echo -e "\e[32m# Tail all logs; press Ctrl-C to exit\e[m"
sudo journalctl -f -n 3 -o cat -u $GITBUCKET_SERVICE_DESC
