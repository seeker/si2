# -*- mode: ruby -*-
# vi: set ft=ruby :

# All Vagrant configuration is done below. The "2" in Vagrant.configure
# configures the configuration version (we support older styles for
# backwards compatibility). Please don't change it unless you know what
# you're doing.
Vagrant.configure("2") do |config|
  config.vm.box = "ubuntu/bionic64"
  
  config.vm.define "nomad" do |nomad|
  nomad.vm.network "forwarded_port", guest: 22, host: 2270, auto_correct: false, id: "ssh"
  nomad.vm.network "forwarded_port", guest: 4646, host: 4646, id: "nomad"
  nomad.vm.network "forwarded_port", guest: 8500, host: 8500, id: "consul"
  nomad.vm.network "forwarded_port", guest: 8200, host: 8200, id: "vault"
  nomad.vm.network "forwarded_port", guest: 15672, host: 15672, id: "rabbitmq-http"
  nomad.vm.network "forwarded_port", guest: 15671, host: 15671, id: "rabbitmq-https"
  nomad.vm.network "forwarded_port", guest: 5672, host: 5672, id: "amqp"
  nomad.vm.network "forwarded_port", guest: 27017, host: 27017, id: "mongodb"
  nomad.vm.network "forwarded_port", guest: 2375, host: 2375, id: "docker-daemon"
  
  nomad.vm.provision "ansible_local" do |ansible|
    ansible.playbook = "ansible/site.yml"
  end

	nomad.vm.provider "virtualbox" do |vb|
		vb.memory = 5120
		vb.cpus = 4
	end
  end
end
