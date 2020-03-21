# -*- mode: ruby -*-
# vi: set ft=ruby :

# All Vagrant configuration is done below. The "2" in Vagrant.configure
# configures the configuration version (we support older styles for
# backwards compatibility). Please don't change it unless you know what
# you're doing.
Vagrant.configure("2") do |config|
  # The most common configuration options are documented and commented below.
  # For a complete reference, please see the online documentation at
  # https://docs.vagrantup.com.

  # Every Vagrant development environment requires a box. You can search for
  # boxes at https://vagrantcloud.com/search.
  config.vm.box = "ubuntu/bionic64"

  config.vm.define "consul", primary: true do |consul|
	consul.vm.network "private_network", ip: "192.168.42.10"
  consul.vm.network "forwarded_port", guest: 22, host: 2220, auto_correct: false, id: "ssh"
	consul.vm.provision "shell", path: "scripts/bootstrap.sh"

	consul.vm.provider "virtualbox" do |vb|
		vb.memory = 512
		vb.cpus = 2
	end
  end

  config.vm.define "vault", primary: true do |vault|
	vault.vm.network "private_network", ip: "192.168.42.13"
  vault.vm.network "forwarded_port", guest: 22, host: 2250, auto_correct: false, id: "ssh"
	vault.vm.provision "shell", path: "scripts/bootstrap.sh"

	vault.vm.provider "virtualbox" do |vb|
		vb.memory = 512
		vb.cpus = 2
	end
  end

  config.vm.define "mongodb" do |mongodb|
  mongodb.vm.network "private_network", ip: "192.168.42.11"
  mongodb.vm.network "forwarded_port", guest: 22, host: 2230, auto_correct: false, id: "ssh"
	mongodb.vm.provision "shell", path: "scripts/bootstrap.sh"

	mongodb.vm.provider "virtualbox" do |vb|
		vb.memory = 1024
		vb.cpus = 2
	end
  end
  
  config.vm.define "rabbitmq" do |rabbitmq|
	rabbitmq.vm.network "private_network", ip: "192.168.42.12"
  rabbitmq.vm.network "forwarded_port", guest: 22, host: 2240, auto_correct: false, id: "ssh"
  rabbitmq.vm.provision "shell", path: "scripts/bootstrap.sh"

	rabbitmq.vm.provider "virtualbox" do |vb|
		vb.memory = 1024
		vb.cpus = 2
	end
  end

  config.vm.define "ansible", autostart: false do |ansible|
	ansible.vm.network "private_network", ip: "192.168.42.14"
  ansible.vm.network "forwarded_port", guest: 22, host: 2260, auto_correct: false, id: "ssh"
  # TODO provision with ansible playbook to install ansible and SSH key
  ansible.vm.provision "shell", path: "scripts/ansible.sh"

	ansible.vm.provider "virtualbox" do |vb|
		vb.memory = 1024
		vb.cpus = 2
	end
  end
  
  config.vm.define "nomad_server" do |nomad_server|
	nomad_server.vm.network "private_network", ip: "192.168.42.15"
  nomad_server.vm.network "forwarded_port", guest: 22, host: 2270, auto_correct: false, id: "ssh"
  nomad_server.vm.provision "shell", path: "scripts/bootstrap.sh"

	nomad_server.vm.provider "virtualbox" do |vb|
		vb.memory = 512
		vb.cpus = 2
	end
  end
  
  config.vm.define "nomad_client" do |nomad_client|
	nomad_client.vm.network "private_network", ip: "192.168.42.16"
  nomad_client.vm.network "forwarded_port", guest: 22, host: 2280, auto_correct: false, id: "ssh"
  nomad_client.vm.provision "shell", path: "scripts/bootstrap.sh"

	nomad_client.vm.provider "virtualbox" do |vb|
		vb.memory = 2048
		vb.cpus = 4
	end
  end

  # Disable automatic box update checking. If you disable this, then
  # boxes will only be checked for updates when the user runs
  # `vagrant box outdated`. This is not recommended.
  # config.vm.box_check_update = false

  # Create a forwarded port mapping which allows access to a specific port
  # within the machine from a port on the host machine. In the example below,
  # accessing "localhost:8080" will access port 80 on the guest machine.
  # NOTE: This will enable public access to the opened port
  # config.vm.network "forwarded_port", guest: 80, host: 8080

  # Create a forwarded port mapping which allows access to a specific port
  # within the machine from a port on the host machine and only allow access
  # via 127.0.0.1 to disable public access
  # config.vm.network "forwarded_port", guest: 80, host: 8080, host_ip: "127.0.0.1"

  #Create a private network, which allows host-only access to the machine
  #using a specific IP.

  # Create a public network, which generally matched to bridged network.
  # Bridged networks make the machine appear as another physical device on
  # your network.
  # config.vm.network "public_network"

  # Share an additional folder to the guest VM. The first argument is
  # the path on the host to the actual folder. The second argument is
  # the path on the guest to mount the folder. And the optional third
  # argument is a set of non-required options.
  # config.vm.synced_folder "../data", "/vagrant_data"

  # Provider-specific configuration so you can fine-tune various
  # backing providers for Vagrant. These expose provider-specific options.
  # Example for VirtualBox:
  #
  #
  # View the documentation for the provider you are using for more
  # information on available options.

  # Enable provisioning with a shell script. Additional provisioners such as
  # Puppet, Chef, Ansible, Salt, and Docker are also available. Please see the
  # documentation for more information about their specific syntax and use.
end
