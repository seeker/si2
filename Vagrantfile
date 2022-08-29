# -*- mode: ruby -*-
# vi: set ft=ruby :

# All Vagrant configuration is done below. The "2" in Vagrant.configure
# configures the configuration version (we support older styles for
# backwards compatibility). Please don't change it unless you know what
# you're doing.
Vagrant.configure("2") do |config|
  config.vm.define "nomad", autostart: false do |nomad|
  nomad.vm.box = "ubuntu/bionic64"
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

  config.vm.define "consul" do |consul|
	consul.vm.provider "docker" do |d|
	    d.image = "consul"
		d.ports = ["8500:8500"]
	    d.cmd = ["consul", "agent", "-server", "-bootstrap-expect=1", "-data-dir=/consul/data/"]
		d.env = {"CONSUL_LOCAL_CONFIG" => '{"skip_leave_on_interrupt": true}'}
	end
  end

  config.vm.define "vault" do |consul|
	consul.vm.provider "docker" do |d|
	    d.image = "vault"
		d.ports = ["8200:8200"]
		d.create_args = ["--cap-add=IPC_LOCK"]
	    d.cmd = ["vault", "server","-config=/vault/config/"]
		d.env = {"VAULT_LOCAL_CONFIG" => '{\"backend\": {\"file\": {\"path\": \"/vault/file\"}}, \"default_lease_ttl\": \"168h\", \"max_lease_ttl\": \"720h\"}'}
	end
  end

  config.vm.define "rabbitmq" do |rabbitmq|
	rabbitmq.vm.provider "docker" do |d|
	    d.image = "rabbitmq"
		d.ports = ["15672:15672", "15671:15671", "5672:5672"]
	end
  end

  config.vm.define "mongodb" do |mongodb|
	mongodb.vm.provider "docker" do |d|
	    d.image = "mongo"
		d.ports = ["26017:27017"]
	end
  end
end
