#!/bin/sh
sudo apt-get -y update
sudo apt-get -y install python-software-properties
if [ ! -e /etc/apt/sources.list.d/mongodb.list ]; then
  sudo apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 7F0CEB10
echo 'deb http://downloads-distro.mongodb.org/repo/ubuntu-upstart dist 10gen' | sudo tee /etc/apt/sources.list.d/mongodb.list
fi
sudo add-apt-repository ppa:chris-lea/node.js
sudo apt-get -y update
sudo apt-get -y install openjdk-7-jdk mongodb-10gen git nodejs
wget https://raw.github.com/technomancy/leiningen/stable/bin/lein
sudo mv lein /usr/bin/lein
sudo chmod a+x /usr/bin/lein
npm install -g grunt-cli bower

