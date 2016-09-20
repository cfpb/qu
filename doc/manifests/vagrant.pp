yumrepo { "mongodb":
  name => 'mongodb',
  baseurl => 'http://downloads-distro.mongodb.org/repo/redhat/os/$basearch/',
  gpgcheck => 0,
  enabled => 1,
}

yumrepo { 'epel':
  name => 'EPEL',
  mirrorlist => 'http://mirrors.fedoraproject.org/mirrorlist?repo=epel-6&arch=$basearch',
  gpgcheck => 0,  
  enabled => 1,
}

package { "mongodb-org":
  ensure => present,
  require => Yumrepo["mongodb"],
}

service { "mongod":
  ensure => running,
  require => Package["mongodb-org"],
}

package { "nodejs":
  ensure => present,
  require => Yumrepo["epel"]
}

package { "npm":
  ensure => present,
  require => [Yumrepo["epel"], Package["nodejs"]],
}

package { "unzip":
  ensure => present,
}

package { "p7zip":
  ensure => present,
  require => Yumrepo["epel"],
}

file { "/usr/bin/7z":
  ensure => link,
  target => "/usr/bin/7za",
  mode => "0755",
  owner => "root",
  require => Package["p7zip"],
}

package { "git":
  ensure => present,
}

package { "java-1.8.0-openjdk":
  ensure => present,
}

exec { 'install leiningen':
  command => "/usr/bin/curl -sL https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein > /usr/bin/lein",
  creates => "/usr/bin/lein",
}

file { "/usr/bin/lein":
  ensure => present,
  mode => "0755",
  owner => "root",
  require => Exec["install leiningen"],
}

package{"nss":
  ensure => "latest",
}

exec { 'install bower':
  timeout => 1800,
  command => "/usr/bin/npm install -g bower",
  creates => "/usr/bin/bower",
  require => Package["npm"],
}

exec { 'install grunt':
  command => "/usr/bin/npm install -g grunt-cli",
  creates => "/usr/bin/grunt",
  require => Package["npm"],
}

service { "iptables":
  ensure => "stopped",
  enable => false,
}
