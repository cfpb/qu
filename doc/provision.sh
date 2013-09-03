#!/bin/bash

# Ensures if the specified file is present and the md5 checksum is equal
ensureFilePresentMd5 () {
    source=$1
    target=$2
    if [ "$3" != "" ]; then description=" $3"; else description=" $source"; fi
 
    md5source=`md5sum ${source} | awk '{ print $1 }'`
    if [ -f "$target" ]; then md5target=`md5sum $target | awk '{ print $1 }'`; else md5target=""; fi

    if [ "$md5source" != "$md5target" ];
    then
        echo "Provisioning$description file to $target..."
        cp $source $target
        echo "...done"
        return 1
    else
        return 0
    fi
}

# Ensures that the specified symbolic link exists and creates it otherwise
ensureSymlink () {
    target=$1
    symlink=$2
    if ! [ -L "$symlink" ];
    then
        ln -s $target $symlink
        echo "Created symlink $symlink that references $target"
        return 1
    else
        return 0
    fi
}

# Provision commands
provision() {
    ensureFilePresentMd5 /vagrant/doc/provision.sh /etc/provision.sh "provisioning"

    if [ "$?" = 1 ]; then
        cp /vagrant/doc/provision.sh /etc/provision.sh        
        sudo apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 7F0CEB10
        if [ ! -f /etc/apt/sources.list.d/mongodb.list ]; then
            echo 'deb http://downloads-distro.mongodb.org/repo/ubuntu-upstart dist 10gen' | sudo tee /etc/apt/sources.list.d/mongodb.list
        fi
        sudo apt-get update
        # sudo apt-get -y upgrade
        # sudo apt-get install -y dkms build-essential linux-headers-generic
        # sudo /etc/init.d/vboxadd setup
        sudo apt-get install -y openjdk-7-jre-headless mongodb-10gen git byobu tmux nginx

        if [ ! -f /usr/local/bin/lein ]; then
            wget https://raw.github.com/technomancy/leiningen/stable/bin/lein
            mv lein /usr/local/bin/lein
            chmod 755 /usr/local/bin/lein
        fi
    fi

    ensureFilePresentMd5 /vagrant/doc/nginx.conf /etc/nginx/sites-available/qu "nginx config"
    ensureSymlink /etc/nginx/sites-available/qu /etc/nginx/sites-enabled/qu
    service nginx reload
}

provision
