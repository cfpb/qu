# -*- mode: ruby -*-
# vi: set ft=ruby :

Vagrant.configure("2") do |config|
  # Every Vagrant virtual environment requires a box to build off of.
  config.vm.box = "centos-6.4-x86_64"
  config.vm.box_url = "http://developer.nrel.gov/downloads/vagrant-boxes/CentOS-6.4-x86_64-v20130731.box"

  # For web server
  config.vm.network "forwarded_port", guest: 3000, host: 3333
  # For Leiningen use
  config.vm.network "forwarded_port", guest: 5678, host: 5678

  config.ssh.shell = "bash -c 'BASH_ENV=/etc/profile exec bash'"
  config.vm.provision :puppet do |puppet|
    puppet.manifests_path = "doc/manifests"
    puppet.manifest_file = "vagrant.pp"
  end
end
