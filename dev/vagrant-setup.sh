#!/bin/sh

# install required packages
sudo apt-get update
sudo apt-get install mongodb rabbitmq-server unzip -y

# fix ubuntu systemctl bug for rabbitmq
sudo sed -i 's/rabbitmqctl stop/rabbitmqctl shutdown/' /lib/systemd/system/rabbitmq-server.service
sudo systemctl daemon-reload

# install consul
wget -nv https://releases.hashicorp.com/consul/1.5.1/consul_1.5.1_linux_amd64.zip
unzip consul*
rm consul_1.5.1_linux_amd64.zip
sudo mv consul /usr/local/bin
sudo cp /vagrant/dev/consul.service /lib/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable consul.service
sudo systemctl start consul.service
