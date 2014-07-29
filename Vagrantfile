# -*- mode: ruby -*-
# vi: set ft=ruby :

Vagrant.configure("2") do |config|
  # Every Vagrant virtual environment requires a box to build off of.
  config.vm.box = "centos-6.4-x86_64"
  config.vm.box_url = "http://developer.nrel.gov/downloads/vagrant-boxes/CentOS-6.4-x86_64-v20130731.box"

  config.vm.network "forwarded_port", guest: 3000,  host: 3333   # Web Server
  config.vm.network "forwarded_port", guest: 5678,  host: 5678   # Leiningen
  config.vm.network "forwarded_port", guest: 27017, host: 27017  # MongoDB
  config.vm.network "forwarded_port", guest: 28017, host: 28017  # MongoDB status

  config.vm.provision :puppet do |puppet|
    puppet.manifests_path = "doc/manifests"
    puppet.manifest_file = "vagrant.pp"
  end

  # Optional - expand memory limit for the virtual machine to improve performance with larger datasets
  # config.vm.provider "virtualbox" do |v|
  #   v.memory = 2048
  # end
end
